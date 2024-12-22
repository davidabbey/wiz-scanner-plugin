package io.jenkins.plugins.wiz;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.io.IOException;
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

        FilePath[] filesToClean = {
            new FilePath(new File(build.getRootDir(), "wizcli_output")),
            new FilePath(new File(build.getRootDir(), "wizcli_err_output")),
            workspace.child(artifactName),
            workspace.child(WizCliSetup.WIZCLI_UNIX_PATH),
            workspace.child(WizCliSetup.WIZCLI_WINDOWS_PATH)
        };

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
