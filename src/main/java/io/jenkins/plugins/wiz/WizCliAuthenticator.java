package io.jenkins.plugins.wiz;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles authentication with the Wiz API.
 */
public class WizCliAuthenticator {
    private static final Logger LOGGER = Logger.getLogger(WizCliAuthenticator.class.getName());

    /**
     * Authenticates with the Wiz API using provided credentials.
     *
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

        File errorFile = new File(workspace.getRemote(), "auth_error.txt");

        try (PrintStream errorStream = new PrintStream(errorFile, StandardCharsets.UTF_8)) {

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
            cleanupErrorFile(errorFile);

        }
    }

    /**
     * Extracts a clean error message from the error file, removing ASCII art and formatting
     */
    private static String getCleanErrorMessage(File errorFile) throws IOException {
        if (!errorFile.exists() || errorFile.length() == 0) {
            return "Authentication failed";
        }

        String errorContent = Files.readString(errorFile.toPath());

        // Find the actual error message
        int errorIndex = errorContent.indexOf("ERROR:");
        if (errorIndex >= 0) {
            String errorMessage = errorContent.substring(errorIndex);
            // Remove any ASCII art or unnecessary formatting
            errorMessage = errorMessage.replaceAll("(?s)_.*?_", "").trim();
            return errorMessage;
        }

        // If no specific error found, return a generic message
        return "Authentication failed: " + errorContent.trim();
    }

    private static void cleanupErrorFile(File errorFile) {
        if (errorFile.exists() && !errorFile.delete()) {
            LOGGER.log(Level.WARNING, "Failed to delete temporary error file: {0}",
                    errorFile.getAbsolutePath());
        }
    }

    /**
     * Logs out from the Wiz CLI
     */
    public static int logout(
            Launcher launcher,
            FilePath workspace,
            EnvVars env,
            TaskListener listener,
            WizCliSetup cliSetup) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliSetup.getCliCommand());
        args.add("auth");
        args.add("--logout");

        Launcher.ProcStarter procStarter = launcher.launch()
                .cmds(args)
                .envs(env)
                .pwd(workspace)
                .stdout(listener);

        int exitCode = procStarter.join();
        if (exitCode == 0) {
            listener.getLogger().println("Successfully logged out from Wiz CLI");
        } else {
            listener.error("Failed to logout from Wiz CLI. Exit code: " + exitCode);
        }

        return exitCode;
    }
}
