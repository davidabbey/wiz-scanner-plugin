package io.jenkins.plugins.wiz;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles execution of Wiz CLI commands.
 */
public class WizCliRunner {
    private static final Logger LOGGER = Logger.getLogger(WizCliRunner.class.getName());

    /**
     * Executes a Wiz CLI scan command.
     */
    public static int executeScan(
            Run<?, ?> build,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener,
            String userInput,
            String artifactName,
            WizCliSetup cliSetup)
            throws IOException, InterruptedException {

        listener.getLogger().println("Executing Wiz scan...");

        // Validate command before execution
        try {
            WizInputValidator.validateCommand(userInput);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Command validation failed", e);
            listener.getLogger().println("Error: Invalid command: " + e.getMessage());
            return -1;
        }

        File outputFile = new File(build.getRootDir(), "wizcli_output");
        File errorFile = new File(build.getRootDir(), "wizcli_err_output");

        ArgumentListBuilder scanArgs = buildScanArguments(userInput, cliSetup);
        listener.getLogger().println("Executing command: " + scanArgs);

        int exitCode = executeScanProcess(launcher, workspace, env, scanArgs, outputFile, errorFile);

        if (exitCode == 0) {
            copyOutputToArtifact(outputFile, workspace, artifactName);
        }

        return exitCode;
    }

    private static ArgumentListBuilder buildScanArguments(String userInput, WizCliSetup cliSetup) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliSetup.getCliCommand());

        // Split and add user input, respecting quotes
        if (userInput != null && !userInput.trim().isEmpty()) {
            // Use regex pattern to split while preserving quoted strings
            Pattern pattern = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
            Matcher matcher = pattern.matcher(userInput.trim());

            while (matcher.find()) {
                String arg = matcher.group();
                // Remove surrounding quotes if present
                if ((arg.startsWith("\"") && arg.endsWith("\"")) || (arg.startsWith("'") && arg.endsWith("'"))) {
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

    private static int executeScanProcess(
            Launcher launcher,
            FilePath workspace,
            EnvVars env,
            ArgumentListBuilder args,
            File outputFile,
            File errorFile)
            throws IOException, InterruptedException {

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

    private static void copyOutputToArtifact(File outputFile, FilePath workspace, String artifactName)
            throws IOException, InterruptedException {
        FilePath source = new FilePath(outputFile);
        FilePath target = new FilePath(workspace, artifactName);
        source.copyTo(target);
    }
}
