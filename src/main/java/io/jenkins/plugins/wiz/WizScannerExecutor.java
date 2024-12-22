package io.jenkins.plugins.wiz;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WizScannerExecutor {
    private static final Logger LOGGER = Logger.getLogger(WizScannerExecutor.class.getName());

    public static int execute(
            Run<?, ?> build,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener,
            String wizCliURL,
            String wizClientId,
            Secret wizSecretKey,
            String userInput,
            String artifactName) throws IOException, InterruptedException {

        try {
            // Download and setup CLI
            WizCliSetup cliSetup = WizCliDownloader.setupWizCli(workspace, System.getProperty("os.name").toLowerCase(), wizCliURL, listener);

            // Authenticate
            int authResult = WizCliAuthenticator.authenticate(
                    launcher, workspace, env, wizClientId, wizSecretKey, listener, cliSetup);
            if (authResult != 0) {
                listener.error("Authentication failed with exit code: " + authResult);
                return authResult;
            }

            // Execute scan
            listener.getLogger().println("Executing Wiz scan...");
            ArgumentListBuilder scanArgs = new ArgumentListBuilder();
            scanArgs.add(cliSetup.getCliCommand());

            // Split userInput into arguments
            for (String arg : userInput.split("\\s+")) {
                scanArgs.add(arg);
            }

            scanArgs.add("-f", "json");

            // Create output streams for capturing command output
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

            listener.getLogger().println("Executing command: " + scanArgs);

            // Execute the scan command
            int exitCode = launcher.launch()
                    .cmds(scanArgs)
                    .pwd(workspace)
                    .envs(env)
                    .stdout(outputStream)
                    .stderr(errorStream)
                    .join();

            // Log the error output if any
            String errorOutput = errorStream.toString(StandardCharsets.UTF_8);
            if (!errorOutput.isEmpty()) {
                listener.error("Scan error output: " + errorOutput);
            }

            // Save the output to a file in the workspace
            String output = outputStream.toString(StandardCharsets.UTF_8);
            if (!output.isEmpty()) {
                FilePath outputFile = workspace.child(artifactName);
                outputFile.write(output, StandardCharsets.UTF_8.name());
                listener.getLogger().println("Scan results saved to: " + outputFile.getRemote());
            } else {
                listener.error("No output was generated from the scan");
            }

            return exitCode;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Wiz scan execution", e);
            listener.error("Error during Wiz scan execution: " + e.getMessage());
            throw new IOException("Wiz scan failed", e);
        }
    }
}