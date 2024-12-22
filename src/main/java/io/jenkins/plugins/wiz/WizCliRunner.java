package io.jenkins.plugins.wiz;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Main executor class for Wiz CLI operations. Handles command building, execution,
 * and output processing in a structured way.
 */
public class WizCliRunner {
    private static final Logger LOGGER = Logger.getLogger(WizCliRunner.class.getName());
    private static final String OUTPUT_FILENAME = "wizcli_output";
    private static final String ERROR_FILENAME = "wizcli_err_output";

    /**
     * Execute a complete Wiz CLI workflow including setup, authentication, and scanning.
     */
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
            WizCliSetup cliSetup = WizCliDownloader.setupWizCli(
                    workspace,
                    System.getProperty("os.name").toLowerCase(),
                    wizCliURL,
                    listener
            );

            // Authenticate
            int authResult = WizCliAuthenticator.authenticate(
                    launcher,
                    workspace,
                    env,
                    wizClientId,
                    wizSecretKey,
                    listener,
                    cliSetup
            );

            if (authResult != 0) {
                listener.error("Authentication failed with exit code: " + authResult);
                return authResult;
            }

            // Execute scan
            return executeScan(build, workspace, env, launcher, listener, userInput, artifactName, cliSetup);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during Wiz scan execution", e);
            listener.error("Error during Wiz scan execution: " + e.getMessage());
            throw new IOException("Wiz scan failed", e);
        }
    }

    /**
     * Executes the actual scan command after setup and authentication are complete.
     */
    private static int executeScan(
            Run<?, ?> build,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener,
            String userInput,
            String artifactName,
            WizCliSetup cliSetup) throws IOException, InterruptedException {

        listener.getLogger().println("Executing Wiz scan...");

        // Validate command before execution
        try {
            WizInputValidator.validateCommand(userInput);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Command validation failed", e);
            listener.getLogger().println("Error: Invalid command: " + e.getMessage());
            return -1;
        }

        File outputFile = new File(build.getRootDir(), OUTPUT_FILENAME);
        File errorFile = new File(build.getRootDir(), ERROR_FILENAME);

        ArgumentListBuilder scanArgs = buildScanArguments(userInput, cliSetup);
        listener.getLogger().println("Executing command: " + scanArgs);

        int exitCode = executeScanProcess(launcher, workspace, env, scanArgs, outputFile, errorFile);

        if (exitCode == 0) {
            copyOutputToArtifact(outputFile, workspace, artifactName);
        }

        return exitCode;
    }

    /**
     * Builds the scan command arguments, properly handling quoted strings and ensuring JSON output.
     */
    private static ArgumentListBuilder buildScanArguments(String userInput, WizCliSetup cliSetup) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliSetup.getCliCommand());

        // Split and add user input, respecting quotes
        if (userInput != null && !userInput.trim().isEmpty()) {
            Pattern pattern = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
            Matcher matcher = pattern.matcher(userInput.trim());

            while (matcher.find()) {
                String arg = matcher.group();
                // Remove surrounding quotes if present
                if ((arg.startsWith("\"") && arg.endsWith("\"")) ||
                        (arg.startsWith("'") && arg.endsWith("'"))) {
                    arg = arg.substring(1, arg.length() - 1);
                }
                args.add(arg);
            }
        }

        // Ensure JSON output format if not specified
        assert userInput != null;
        if (!userInput.contains("-f") && !userInput.contains("--format")) {
            args.add("-f", "json");
        }

        return args;
    }

    /**
     * Executes the scan process with proper stream handling and cleanup.
     */
    private static int executeScanProcess(
            Launcher launcher,
            FilePath workspace,
            EnvVars env,
            ArgumentListBuilder args,
            File outputFile,
            File errorFile) throws IOException, InterruptedException {

        PrintStream outputStream = null;
        PrintStream errorStream = null;

        try {
            outputStream = new PrintStream(outputFile, StandardCharsets.UTF_8);
            errorStream = new PrintStream(errorFile, StandardCharsets.UTF_8);

            ProcStarter proc = launcher.launch()
                    .cmds(args)
                    .pwd(workspace)
                    .envs(env)
                    .stdout(outputStream)
                    .stderr(errorStream);

            return proc.join();
        } finally {
            WizCliUtils.closeQuietly(outputStream, errorStream);
        }
    }

    /**
     * Copies the scan output to an artifact file in the workspace.
     */
    private static void copyOutputToArtifact(File outputFile, FilePath workspace, String artifactName)
            throws IOException, InterruptedException {
        FilePath source = new FilePath(outputFile);
        FilePath target = new FilePath(workspace, artifactName);
        source.copyTo(target);
    }
}
