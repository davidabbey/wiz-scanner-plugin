package io.jenkins.plugins.wiz;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class WizScannerBuilder extends Builder implements SimpleBuildStep {
    private static final Logger LOGGER = Logger.getLogger(WizScannerBuilder.class.getName());
    private static final String DEFAULT_ARTIFACT_NAME = "wizscan.json";
    private static final String ARTIFACT_PREFIX = "wizscan-";
    private static final String ARTIFACT_SUFFIX = ".json";

    public static final int OK_CODE = 0;
    private static volatile int buildId; // Made volatile for thread safety
    private static volatile int count;

    private final String userInput;

    @DataBoundConstructor
    public WizScannerBuilder(String userInput) {
        this.userInput = StringUtils.trimToEmpty(userInput);
    }

    public String getUserInput() {
        return userInput;
    }

    private static synchronized int getNextCount() {
        return ++count;
    }

    private static synchronized void resetCount() {
        count = 1;
    }

    /**
     * Validates the global configuration parameters
     * @param wizClientId The Wiz client ID
     * @param wizSecretKey The Wiz secret key
     * @param wizCliURL The Wiz CLI URL
     * @throws AbortException if any required parameter is missing
     */
    private void validateConfiguration(String wizClientId, Secret wizSecretKey, String wizCliURL) throws AbortException {
        if (StringUtils.isBlank(wizClientId)) {
            throw new AbortException("Wiz Client ID is required");
        }
        if (wizSecretKey == null || StringUtils.isBlank(Secret.toString(wizSecretKey))) {
            throw new AbortException("Wiz Secret Key is required");
        }
        if (StringUtils.isBlank(wizCliURL)) {
            throw new AbortException("Wiz CLI URL is required");
        }
    }

    /**
     * Determines the artifact name based on build context
     */
    private static class ArtifactInfo {
        final String name;
        final String suffix;

        ArtifactInfo(String name, String suffix) {
            this.name = name;
            this.suffix = suffix;
        }
    }

    private ArtifactInfo determineArtifactName(int currentBuildId) {
        if (currentBuildId != buildId) {
            buildId = currentBuildId;
            resetCount();
            return new ArtifactInfo(DEFAULT_ARTIFACT_NAME, null);
        }
        String suffix = String.valueOf(getNextCount());
        return new ArtifactInfo(ARTIFACT_PREFIX + suffix + ARTIFACT_SUFFIX, suffix);
    }

    @Override
    public void perform(@NotNull Run<?, ?> build, @NotNull FilePath workspace, @NotNull EnvVars env,
                        @NotNull Launcher launcher, @NotNull TaskListener listener) throws InterruptedException, IOException {
        try {
            LOGGER.log(Level.FINE, "Starting Wiz Scanner build step for build {0}", build.getDisplayName());

            // Get configuration
            DescriptorImpl descriptor = getDescriptor();
            EnvVars envVars = build.getEnvironment(listener);

            // Validate configuration
            validateConfiguration(
                    descriptor.getWizClientId(),
                    descriptor.getWizSecretKey(),
                    descriptor.getWizCliURL()
            );

            // Set environment variables
            setupEnvironment(envVars, descriptor.getWizEnv());

            // Determine artifact names
            ArtifactInfo artifactInfo = determineArtifactName(build.hashCode());

            // Execute scan
            int exitCode = executeScan(build, workspace, envVars, launcher, listener, descriptor, artifactInfo);

            // Process results
            processResults(build, exitCode, workspace, listener, artifactInfo);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Wiz scan execution", e);
            throw new AbortException("Wiz scan failed: " + e.getMessage());
        }
    }

    private void setupEnvironment(EnvVars envVars, String wizEnv) {
        if (StringUtils.isNotBlank(wizEnv)) {
            envVars.put("WIZ_ENV", wizEnv);
            LOGGER.log(Level.FINE, "Set WIZ_ENV to {0}", wizEnv);
        }
    }

    private int executeScan(
            Run<?, ?> build,
            FilePath workspace,
            EnvVars envVars,
            Launcher launcher,
            TaskListener listener,
            DescriptorImpl descriptor,
            ArtifactInfo artifactInfo) throws IOException, InterruptedException {

        LOGGER.log(Level.FINE, "Executing Wiz scan with artifact name: {0}", artifactInfo.name);

        return WizScannerExecuter.execute(
                build,
                workspace,
                envVars,
                launcher,
                listener,
                descriptor.getWizCliURL(),
                descriptor.getWizClientId(),
                descriptor.getWizSecretKey(),
                userInput,
                artifactInfo.name);
    }

    private void processResults(
            Run<?, ?> build,
            int exitCode,
            FilePath workspace,
            TaskListener listener,
            ArtifactInfo artifactInfo) throws IOException, InterruptedException {

        build.addAction(new WizScannerAction(build, workspace, artifactInfo.suffix, artifactInfo.name));

        try {
            cleanupArtifacts(build, workspace, listener, artifactInfo.name);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during artifact cleanup", e);
        }

        if (exitCode != OK_CODE) {
            throw new AbortException("Wiz scanning failed with exit code: " + exitCode);
        }

        LOGGER.log(Level.INFO, "Wiz scan completed successfully");
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private void cleanupArtifacts(Run<?, ?> build, FilePath workspace, TaskListener listener, String artifactName)
            throws InterruptedException {

        FilePath[] filesToClean = {
                new FilePath(new File(build.getRootDir(), "wizcli_output")),
                new FilePath(new File(build.getRootDir(), "wizcli_err_output")),
                new FilePath(workspace, artifactName),
                new FilePath(workspace, "wizcli")
        };

        listener.getLogger().println("Cleaning up temporary files...");

        for (FilePath file : filesToClean) {
            try {
                if (file.exists()) {
                    file.delete();
                    LOGGER.log(Level.FINE, "Deleted file: {0}", file.getRemote());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete file: " + file.getRemote(), e);
                listener.getLogger().println("Warning: Failed to delete " + file.getRemote());
            }
        }

        listener.getLogger().println("Temporary files cleanup completed");
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("wizcli")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String wizClientId;
        private Secret wizSecretKey;
        private String wizCliURL;
        private String wizEnv;

        public FormValidation doCheckUserInput(@QueryParameter String value) throws IOException, ServletException {
            if (StringUtils.isBlank(value)) {
                return FormValidation.error(Messages.WizScannerBuilder_DescriptorImpl_errors_missingName());
            }
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.WizScannerBuilder_DescriptorImpl_DisplayName();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            wizClientId = formData.getString("wizClientId");
            wizSecretKey = Secret.fromString(formData.getString("wizSecretKey"));
            wizCliURL = formData.getString("wizCliURL");
            wizEnv = formData.getString("wizEnv");
            save();
            return super.configure(req, formData);
        }

        // Getters
        public String getWizClientId() {
            return wizClientId;
        }

        public Secret getWizSecretKey() {
            return wizSecretKey;
        }

        public String getWizCliURL() {
            return wizCliURL;
        }

        public String getWizEnv() {
            return wizEnv;
        }
    }
}