package io.jenkins.plugins.wiz;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility methods for Wiz CLI operations.
 */
public class WizCliUtils {
    private static final Logger LOGGER = Logger.getLogger(WizCliUtils.class.getName());

    /**
     * Safely closes multiple AutoCloseable resources.
     */
    public static void closeQuietly(AutoCloseable... closeables) {
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

    /**
     * Cleans up temporary files and artifacts.
     */
    public static void cleanupArtifacts(Run<?, ?> build, FilePath workspace, TaskListener listener, String artifactName)
            throws InterruptedException {

        List<FilePath> filesToClean = new ArrayList<>();

        try {
            FilePath buildDir = new FilePath(build.getRootDir());
            filesToClean.add(buildDir.child("wizcli_output"));
            filesToClean.add(buildDir.child("wizcli_err_output"));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to access build directory", e);
            listener.getLogger().println("Warning: Failed to access build directory");
        }

        filesToClean.add(workspace.child(artifactName));
        filesToClean.add(workspace.child(WizCliSetup.WIZCLI_UNIX_PATH));
        filesToClean.add(workspace.child(WizCliSetup.WIZCLI_WINDOWS_PATH));

        listener.getLogger().println("Cleaning up temporary files...");

        for (FilePath file : filesToClean) {
            try {
                if (file.exists()) {
                    file.delete();
                    LOGGER.log(Level.FINE, "Deleted file: {0}", file.getRemote());
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to delete file: " + file.getRemote(), e);
                listener.getLogger().println("Warning: Failed to delete " + file.getRemote());
            }
        }

        listener.getLogger().println("Temporary files cleanup completed");
    }
}
