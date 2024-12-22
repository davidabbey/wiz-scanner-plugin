package io.jenkins.plugins.wiz;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.IOException;
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
     * @return 0 on success, non-zero on failure
     */
    public static int authenticate(
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

        // Build authentication command
        authArgs.add(cliSetup.getCliCommand(), "auth", "--id");
        authArgs.addMasked(wizClientId);
        authArgs.add("--secret");
        authArgs.addMasked(Secret.toString(wizSecretKey));

        // Execute authentication
        int result = launcher.launch()
                .cmds(authArgs)
                .pwd(workspace)
                .envs(env)
                .quiet(true)
                .join();

        if (result != 0) {
            LOGGER.log(Level.WARNING, "Authentication failed with exit code: {0}", result);
        } else {
            LOGGER.log(Level.FINE, "Authentication successful");
        }

        return result;
    }
}
