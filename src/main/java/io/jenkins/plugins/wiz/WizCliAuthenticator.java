package io.jenkins.plugins.wiz;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles authentication with the Wiz API.
 */
public class WizCliAuthenticator {
    private static final Logger LOGGER = Logger.getLogger(WizCliAuthenticator.class.getName());
    private static final String ERROR_FILE_NAME = "auth_error.txt";

    /**
     * Authenticates with the Wiz API using provided credentials.
     */
    public static void authenticate(
            Launcher launcher,
            FilePath workspace,
            EnvVars env,
            String wizClientId,
            Secret wizSecretKey,
            TaskListener listener,
            WizCliSetup cliSetup)
            throws IOException, InterruptedException {

        listener.getLogger().println("Authenticating with Wiz API...");

        ArgumentListBuilder authArgs = new ArgumentListBuilder();
        authArgs.add(cliSetup.getCliCommand(), "auth", "--id");
        authArgs.addMasked(wizClientId);
        authArgs.add("--secret");
        authArgs.addMasked(wizSecretKey.getPlainText());

        FilePath errorFile = workspace.child(ERROR_FILE_NAME);
        OutputStream errorStream = null;

        try {
            errorStream = errorFile.write();

            int result = launcher.launch()
                    .cmds(authArgs)
                    .pwd(workspace)
                    .envs(env)
                    .stderr(errorStream)
                    .stdout(errorStream)
                    .quiet(true)
                    .join();

            if (result != 0) {
                listener.error("Authentication failed with exit code: " + result);
                String errorMessage = getCleanErrorMessage(errorFile);
                throw new AbortException(errorMessage);
            }
        } finally {
            try {
                if (errorStream != null) {
                    errorStream.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to close error stream", e);
            }
            cleanupErrorFile(errorFile);
        }
    }

    /**
     * Extracts a clean error message from the error file, removing ASCII art and formatting
     */
    private static String getCleanErrorMessage(FilePath errorFile) throws IOException, InterruptedException {
        if (!errorFile.exists() || errorFile.length() == 0) {
            return "Authentication failed";
        }

        String errorContent = errorFile.readToString();

        // Find the actual error message
        int errorIndex = errorContent.indexOf("ERROR:");
        if (errorIndex >= 0) {
            String errorMessage = errorContent.substring(errorIndex);
            // Remove any ASCII art or unnecessary formatting
            errorMessage = errorMessage.replaceAll("(?s)_.*?_", "").trim();
            return errorMessage;
        }

        // If no specific error found, return a generic message with the content
        return "Authentication failed: " + errorContent.trim();
    }

    private static void cleanupErrorFile(FilePath errorFile) {
        try {
            if (errorFile != null && errorFile.exists()) {
                errorFile.delete();
                LOGGER.log(Level.FINE, "Successfully deleted error file: {0}", errorFile.getRemote());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to delete error file: " + errorFile.getRemote(), e);
        }
    }

    /**
     * Logs out from the Wiz CLI.
     */
    public static int logout(
            Launcher launcher, FilePath workspace, EnvVars env, TaskListener listener, WizCliSetup cliSetup)
            throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliSetup.getCliCommand());
        args.add("auth");
        args.add("--logout");

        int exitCode = launcher.launch()
                .cmds(args)
                .envs(env)
                .pwd(workspace)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .quiet(false)
                .join();

        if (exitCode == 0) {
            listener.getLogger().println("Successfully logged out from Wiz CLI");
        } else {
            listener.error("Failed to logout from Wiz CLI. Exit code: " + exitCode);
        }

        return exitCode;
    }
}
