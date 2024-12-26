package io.jenkins.plugins.wiz;

/**
 * Configuration class for Wiz CLI setup.
 */
public class WizCliSetup {
    public static final String WIZCLI_WINDOWS_PATH = "wizcli.exe";
    public static final String WIZCLI_UNIX_PATH = "wizcli";

    final String cliPath;
    final boolean isWindows;
    final boolean isMac;
    final String osType;
    final String arch;

    /**
     * Creates a new WizCliSetup instance.
     *
     * @param cliPath Path to the CLI executable
     * @param isWindows Whether running on Windows
     * @param isMac Whether running on macOS
     * @param osType Operating system type
     * @param arch System architecture
     */
    public WizCliSetup(String cliPath, boolean isWindows, boolean isMac, String osType, String arch) {
        this.cliPath = cliPath;
        this.isWindows = isWindows;
        this.isMac = isMac;
        this.osType = osType;
        this.arch = arch;
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
