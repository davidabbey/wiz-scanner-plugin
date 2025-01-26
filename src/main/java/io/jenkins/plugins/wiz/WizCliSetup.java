package io.jenkins.plugins.wiz;

/**
 * Configuration class for Wiz CLI setup.
 */
public class WizCliSetup {
    public static final String WIZCLI_WINDOWS_PATH = "wizcli.exe";
    public static final String WIZCLI_UNIX_PATH = "wizcli";

    final boolean isWindows;

    /**
     * Creates a new WizCliSetup instance.
     *
     * @param isWindows Whether running on Windows
     */
    public WizCliSetup(boolean isWindows) {
        this.isWindows = isWindows;
    }

    /**
     * Gets the CLI executable name based on OS.
     *
     * @return The appropriate CLI executable name
     */
    public String getExecutableName() {
        return isWindows ? WIZCLI_WINDOWS_PATH : WIZCLI_UNIX_PATH;
    }

    /**
     * Gets the CLI path with proper prefix based on operating system.
     *
     * @return The CLI command appropriate for the current OS
     */
    public String getCliCommand() {
        if (isWindows) {
            return getExecutableName();
        }
        return "./" + getExecutableName();
    }
}
