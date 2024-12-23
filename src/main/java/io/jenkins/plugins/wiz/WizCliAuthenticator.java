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
        authArgs.addMasked(Secret.toString(wizSecretKey));

        File errorFile = new File(workspace.getRemote(), "auth_error.txt");

        try (PrintStream errorStream = new PrintStream(errorFile, StandardCharsets.UTF_8)) {

            int result = launcher.launch()
                    .cmds(authArgs)
                    .pwd(workspace)
                    .envs(env)
                    .stderr(errorStream)
                    .quiet(true)
                    .join();

            if (result != 0) {
                listener.error("Authentication failed with exit code: " + result);
                String errorMessage = errorFile.exists() && errorFile.length() > 0
                        ? Files.readString(errorFile.toPath())
                        : "Authentication failed with exit code: " + result;
                throw new AbortException("Wiz CLI authentication failed: " + errorMessage.trim());
            }

        } finally {
            if (errorFile.exists()) {
                if (!errorFile.delete()) {
                    LOGGER.log(Level.WARNING, "Failed to delete temporary error file: {0}",
                            errorFile.getAbsolutePath());
                }
            }
        }
    }

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
