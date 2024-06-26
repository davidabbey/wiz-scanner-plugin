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
import java.nio.file.Files;
import java.nio.file.Paths;

public class WizScannerExecuter {

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

        PrintStream printStream = null;
        PrintStream printStderrStream = null;
        try {
            // Determine the OS
            String osName = System.getProperty("os.name").toLowerCase();
            listener.getLogger().println("Operating System: " + osName);

            // Download Wiz CLI
            listener.getLogger().println("Downloading wizcli...");
            String wizCliPath = workspace.child(osName.contains("win") ? "wizcli.exe" : "wizcli").getRemote();
            downloadFile(wizCliURL, wizCliPath);

            // Make the CLI executable on Unix-based systems
            if (!osName.contains("win")) {
                launcher.launch().cmds("chmod", "+x", wizCliPath).pwd(workspace).join();
            }

            // Auth with Wiz
            listener.getLogger().println("Authenticating to the Wiz API...");
            ArgumentListBuilder authArgs = new ArgumentListBuilder();
            authArgs.add("./wizcli", "auth", "--id");
            authArgs.addMasked(wizClientId);
            authArgs.add("--secret");
            authArgs.addMasked(Secret.toString(wizSecretKey));
            int authExitCode =
                    launcher.launch().cmds(authArgs).pwd(workspace).envs(env).join();
            if (authExitCode != 0) {
                listener.getLogger().println("Error: Authentication to Wiz CLI failed. Exit code: " + authExitCode);
                return authExitCode;
            }

            // Scan
            listener.getLogger().println("Scanning the image using wizcli...");
            File outputFile = new File(build.getRootDir(), "wizcli_output");
            File stderrFile = new File(build.getRootDir(), "wizcli_err_output");
            ArgumentListBuilder scanArgs = new ArgumentListBuilder();
            scanArgs.add("./wizcli");
            // Split userInput into individual arguments and add them
            for (String arg : userInput.split("\\s+")) {
                scanArgs.add(arg);
            }
            scanArgs.add("-f", "json");
            ProcStarter ps = launcher.launch();
            ps.cmds(scanArgs).pwd(workspace).envs(env);
            ps.stdin(null);
            printStream = new PrintStream(outputFile, "UTF-8");
            printStderrStream = new PrintStream(stderrFile, "UTF-8");
            ps.stderr(printStderrStream);
            ps.stdout(printStream);
            ps.quiet(true);
            listener.getLogger().println(scanArgs.toString());
            int exitCode = ps.join(); // RUN !

            FilePath target = new FilePath(workspace, artifactName);
            FilePath outFilePath = new FilePath(outputFile);
            outFilePath.copyTo(target);
            return exitCode;

        } catch (RuntimeException e) {
            listener.getLogger().println("RuntimeException:" + e.toString());
            return -1;
        } catch (Exception e) {
            listener.getLogger().println("Exception:" + e.toString());
            return -1;
        } finally {
            if (printStream != null) {
                printStream.close();
            }
            if (printStderrStream != null) {
                printStderrStream.close();
            }
        }
    }

    private static void downloadFile(String fileURL, String savePath) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        // Check HTTP response code first
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Files.copy(httpConn.getInputStream(), Paths.get(savePath));
            httpConn.disconnect();
        } else {
            throw new IOException("Failed to download file: HTTP response code " + responseCode);
        }
    }
}
