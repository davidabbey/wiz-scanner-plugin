package io.jenkins.plugins.wiz;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

public class WizScannerExecuter {
    private static final Logger LOGGER = Logger.getLogger(WizScannerExecuter.class.getName());
    private static final int DOWNLOAD_TIMEOUT = 60000; // 60 seconds
    private static final int CONNECT_TIMEOUT = 10000;  // 10 seconds
    private static final String WIZCLI_WIN = "wizcli.exe";
    private static final String WIZCLI_UNIX = "wizcli";
    private static final int ERROR_CODE = -1;

    /**
     * Execute the Wiz CLI scan
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
            String artifactName) {

        try {
            validateInputs(build, workspace, wizCliURL, wizClientId, wizSecretKey);

            // Setup paths and CLI
            String osName = System.getProperty("os.name").toLowerCase();
            LOGGER.log(Level.INFO, "Executing on OS: {0}", osName);

            WizCliSetup cliSetup = setupWizCli(workspace, osName, wizCliURL, launcher, listener);

            // Perform authentication
            int authResult = authenticateWizCli(launcher, workspace, env, wizClientId, wizSecretKey, listener, cliSetup);
            if (authResult != 0) {
                return handleError("Authentication failed with exit code: " + authResult, listener);
            }

            // Execute scan
            return executeScan(build, workspace, env, launcher, listener, userInput, artifactName, cliSetup);

        } catch (Exception e) {
            return handleError("Execution failed: " + e.getMessage(), e, listener);
        }
    }

    private static void validateInputs(Run<?, ?> build, FilePath workspace, String wizCliURL,
                                       String wizClientId, Secret wizSecretKey) throws AbortException {
        if (build == null) throw new AbortException("Build cannot be null");
        if (workspace == null) throw new AbortException("Workspace cannot be null");
        if (StringUtils.isBlank(wizCliURL)) throw new AbortException("Wiz CLI URL cannot be empty");
        if (StringUtils.isBlank(wizClientId)) throw new AbortException("Client ID cannot be empty");
        if (wizSecretKey == null || StringUtils.isBlank(Secret.toString(wizSecretKey))) {
            throw new AbortException("Secret key cannot be empty");
        }
    }

    private static class WizCliSetup {
        final String cliPath;
        final boolean isWindows;

        WizCliSetup(String cliPath, boolean isWindows) {
            this.cliPath = cliPath;
            this.isWindows = isWindows;
        }
    }

    private static WizCliSetup setupWizCli(FilePath workspace, String osName, String wizCliURL,
                                           Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        boolean isWindows = osName.contains("win");
        String cliFileName = isWindows ? WIZCLI_WIN : WIZCLI_UNIX;
        String cliPath = workspace.child(cliFileName).getRemote();

        listener.getLogger().println("Downloading Wiz CLI...");
        downloadWizCli(wizCliURL, cliPath);

        if (!isWindows) {
            makeExecutable(launcher, workspace, cliPath);
        }

        return new WizCliSetup(cliPath, isWindows);
    }

    private static void downloadWizCli(String fileURL, String savePath) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(DOWNLOAD_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download failed with HTTP code: " + responseCode);
            }

            Path targetPath = Paths.get(savePath);
            Files.copy(conn.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void makeExecutable(Launcher launcher, FilePath workspace, String cliPath) throws IOException, InterruptedException {
        int result = launcher.launch()
                .cmds("chmod", "+x", cliPath)
                .pwd(workspace)
                .quiet(true)
                .join();

        if (result != 0) {
            throw new IOException("Failed to make CLI executable. Exit code: " + result);
        }
    }

    private static int authenticateWizCli(Launcher launcher, FilePath workspace, EnvVars env,
                                          String wizClientId, Secret wizSecretKey, TaskListener listener,
                                          WizCliSetup cliSetup) throws IOException, InterruptedException {
        listener.getLogger().println("Authenticating with Wiz API...");

        ArgumentListBuilder authArgs = new ArgumentListBuilder();
        authArgs.add(cliSetup.isWindows ? "./" + WIZCLI_WIN : "./" + WIZCLI_UNIX, "auth", "--id");
        authArgs.addMasked(wizClientId);
        authArgs.add("--secret");
        authArgs.addMasked(Secret.toString(wizSecretKey));

        return launcher.launch()
                .cmds(authArgs)
                .pwd(workspace)
                .envs(env)
                .quiet(true)
                .join();
    }

    private static int executeScan(Run<?, ?> build, FilePath workspace, EnvVars env,
                                   Launcher launcher, TaskListener listener, String userInput,
                                   String artifactName, WizCliSetup cliSetup) throws IOException, InterruptedException {
        listener.getLogger().println("Executing Wiz scan...");

        File outputFile = new File(build.getRootDir(), "wizcli_output");
        File errorFile = new File(build.getRootDir(), "wizcli_err_output");

        ArgumentListBuilder scanArgs = buildScanArguments(userInput, cliSetup);

        int exitCode = executeScanProcess(launcher, workspace, env, scanArgs, outputFile, errorFile);

        if (exitCode == 0) {
            copyOutputToArtifact(outputFile, workspace, artifactName);
        }

        return exitCode;
    }

    private static ArgumentListBuilder buildScanArguments(String userInput, WizCliSetup cliSetup) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(cliSetup.isWindows ? "./" + WIZCLI_WIN : "./" + WIZCLI_UNIX);

        if (StringUtils.isNotBlank(userInput)) {
            for (String arg : userInput.split("\\s+")) {
                args.add(arg);
            }
        }

        return args.add("-f", "json");
    }

    private static int executeScanProcess(Launcher launcher, FilePath workspace, EnvVars env,
                                          ArgumentListBuilder args, File outputFile, File errorFile) throws IOException, InterruptedException {
        PrintStream outputStream = new PrintStream(outputFile, StandardCharsets.UTF_8);
        PrintStream errorStream = new PrintStream(errorFile, StandardCharsets.UTF_8);

        try {
            ProcStarter proc = launcher.launch()
                    .cmds(args)
                    .pwd(workspace)
                    .envs(env)
                    .stdout(outputStream)
                    .stderr(errorStream);

            return proc.join();
        } finally {
            closeQuietly(outputStream, errorStream);
        }
    }

    private static void copyOutputToArtifact(File outputFile, FilePath workspace, String artifactName) throws IOException, InterruptedException {
        FilePath source = new FilePath(outputFile);
        FilePath target = new FilePath(workspace, artifactName);
        source.copyTo(target);
    }

    private static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing resource", e);
            }
        }
    }

    private static int handleError(String message, TaskListener listener) {
        LOGGER.log(Level.SEVERE, message);
        listener.getLogger().println("Error: " + message);
        return ERROR_CODE;
    }

    private static int handleError(String message, Exception e, TaskListener listener) {
        LOGGER.log(Level.SEVERE, message, e);
        listener.getLogger().println("Error: " + message + "\n" + e.getMessage());
        return ERROR_CODE;
    }
}