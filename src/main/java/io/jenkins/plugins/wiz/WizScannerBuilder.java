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
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class WizScannerBuilder extends Builder implements SimpleBuildStep {
    private final String userInput;

    @DataBoundConstructor
    public WizScannerBuilder(String userInput) {
        this.userInput = userInput;
    }

    public static final int OK_CODE = 0;
    private static int buildId;
    private static int count;

    public String getUserInput() {
        return userInput;
    }

    public static synchronized void setBuildId(int buildId) {
        WizScannerBuilder.buildId = buildId;
    }

    public static synchronized void setCount(int count) {
        WizScannerBuilder.count = count;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        EnvVars envVars = build.getEnvironment(listener);
        String wizClientId = getDescriptor().getWizClientId();
        Secret wizSecretKey = getDescriptor().getWizSecretKey();
        String wizCliURL = getDescriptor().getWizCliURL();
        String wizEnv = getDescriptor().getWizEnv();
        if (wizClientId == null
                || wizClientId.trim().isEmpty()
                || wizSecretKey == null
                || Secret.toString(wizSecretKey).trim().isEmpty()
                || wizCliURL == null
                || wizCliURL.trim().isEmpty()) {
            throw new AbortException(
                    "Missing configuration. Please set the global configuration parameters in The \"Wiz\" section under  \"Manage Jenkins -> System\", before continuing.\n");
        }

        if (wizEnv != null && !wizEnv.isEmpty()) {
            envVars.put("WIZ_ENV", wizEnv);
        }

        // Support unique names for artifacts when there are multiple steps in the same build
        String artifactSuffix, artifactName;
        if (build.hashCode() != buildId) {
            // New build
            setBuildId(build.hashCode());
            setCount(1);
            artifactSuffix = null; // When there is only one step, there should be no suffix at all
            artifactName = "wizscan.json";
        } else {
            setCount(count + 1);
            artifactSuffix = Integer.toString(count);
            artifactName = "wizscan-" + artifactSuffix + ".json";
        }

        int exitCode = WizScannerExecuter.execute(
                build,
                workspace,
                envVars,
                launcher,
                listener,
                wizCliURL,
                wizClientId,
                wizSecretKey,
                userInput,
                artifactName);
        build.addAction(new WizScannerAction(build, workspace, artifactSuffix, artifactName));
        System.out.println("exitCode: " + exitCode);
        String failedMessage = "Scanning failed.";
        cleanupArtifacts(build, workspace, listener, artifactName);
        switch (exitCode) {
            case OK_CODE:
                System.out.println("Scanning success.");
                break;
            default:
                // This exception causes the message to appear in the Jenkins console
                throw new AbortException(failedMessage);
        }
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // No idea why this is needed
    private void cleanupArtifacts(Run<?, ?> build, FilePath workspace, TaskListener listener, String artifactName)
            throws java.lang.InterruptedException {

        // Cleanup: delete created files
        try {
            listener.getLogger().println("Cleaning up temporary files...");
            FilePath outputFile = new FilePath(new File(build.getRootDir(), "wizcli_output"));
            FilePath stderrFile = new FilePath(new File(build.getRootDir(), "wizcli_err_output"));
            FilePath artifactFile = new FilePath(workspace, artifactName);
            FilePath wizcliFile = new FilePath(workspace, "wizcli");

            if (outputFile.exists()) {
                outputFile.delete();
            }
            if (stderrFile.exists()) {
                stderrFile.delete();
            }
            if (artifactFile.exists()) {
                artifactFile.delete();
            }
            if (wizcliFile.exists()) {
                wizcliFile.delete();
            }

            listener.getLogger().println("Temporary files deleted.");
        } catch (Exception e) {
            listener.getLogger().println("Error during cleanup: " + e.toString());
        }
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
            if (value.isEmpty())
                return FormValidation.error(Messages.WizScannerBuilder_DescriptorImpl_errors_missingName());
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
            // To persist global configuration information,
            // set that to properties and call save().
            wizClientId = formData.getString("wizClientId");
            wizSecretKey = Secret.fromString(formData.getString("wizSecretKey"));
            wizCliURL = formData.getString("wizCliURL");
            wizEnv = formData.getString("wizEnv");
            save();
            return super.configure(req, formData);
        }

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
