package io.jenkins.plugins.wiz;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles downloading and verifying the Wiz CLI binary.
 */
public class WizCliDownloader {
    private static final Logger LOGGER = Logger.getLogger(WizCliDownloader.class.getName());
    private static final int DOWNLOAD_TIMEOUT = 60000; // 60 seconds
    private static final int CONNECT_TIMEOUT = 10000; // 10 seconds
    private static final String PUBLIC_KEY_RESOURCE = "/io/jenkins/plugins/wiz/public_key.asc";


    /**
     * Sets up the Wiz CLI by downloading and verifying the binary.
     */
    public static WizCliSetup setupWizCli(FilePath workspace, String osName, String wizCliURL,
                                         TaskListener listener) throws IOException {

        try {
            // Validate CLI URL format before proceeding
            WizInputValidator.validateWizCliUrl(wizCliURL);

            // Detect OS and architecture
            boolean isWindows = osName.contains("win");
            boolean isMac = osName.contains("mac") || osName.contains("darwin");
            String arch = System.getProperty("os.arch").toLowerCase();
            String cliFileName = isWindows ? WizCliSetup.WIZCLI_WINDOWS_PATH : WizCliSetup.WIZCLI_UNIX_PATH;
            String cliPath = workspace.child(cliFileName).getRemote();

            downloadAndVerifyWizCli(wizCliURL, cliPath, workspace, listener);

            if (!isWindows) {
                makeExecutable(cliPath);
            }

            return new WizCliSetup(cliPath, isWindows, isMac, osName, arch);

        } catch (AbortException e) {
            listener.error("Invalid Wiz CLI URL format: " + e.getMessage());
            throw e;
        }
    }

    private static void downloadAndVerifyWizCli(
            String wizCliURL, String cliPath, FilePath workspace, TaskListener listener)
            throws IOException {
        try {
            // Download CLI
            listener.getLogger().println("Downloading Wiz CLI from: " + wizCliURL);
            downloadFile(wizCliURL, cliPath);
            listener.getLogger().println("Download completed successfully");

            // Construct verification file URLs
            String sha256URL = wizCliURL + "-sha256";
            String signatureURL = sha256URL + ".sig";

            // Create FilePath objects for verification files
            FilePath sha256File = workspace.child("wizcli-sha256");
            FilePath signatureFile = workspace.child("wizcli-sha256.sig");
            FilePath publicKeyFile = workspace.child("public_key.asc");

            try {
                // Download verification files
                downloadFile(sha256URL, sha256File.getRemote());
                downloadFile(signatureURL, signatureFile.getRemote());

                // Extract public key from resources
                extractPublicKey(publicKeyFile);

                // Verify signature and checksum
                verifySignatureAndChecksum(
                        listener, cliPath, sha256File.getRemote(), signatureFile.getRemote(), publicKeyFile.getRemote());

            } finally {
                // Clean up verification files
                cleanupVerificationFiles(workspace, listener);
            }
        } catch (Exception e) {
            listener.error("Failed to download or verify Wiz CLI: " + e.getMessage());
            throw new AbortException("Failed to setup Wiz CLI: " + e.getMessage());
        }
    }

    private static void extractPublicKey(FilePath publicKeyFile) throws IOException {
        try (InputStream keyStream = WizCliDownloader.class.getResourceAsStream(PUBLIC_KEY_RESOURCE)) {
            if (keyStream == null) {
                throw new IOException("Could not find public key resource");
            }

            // Read the public key from resources
            String publicKey = new String(keyStream.readAllBytes(), StandardCharsets.UTF_8);

            // Write to workspace
            publicKeyFile.write(publicKey, StandardCharsets.UTF_8.name());

            LOGGER.log(Level.FINE,"Public key extracted successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to extract public key", e);
            throw new IOException("Failed to extract public key from resources", e);
        }
    }

    private static void downloadFile(String fileURL, String savePath) throws IOException {
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

    private static void verifySignatureAndChecksum(
            TaskListener listener, String cliPath, String sha256Path, String signaturePath, String publicKeyPath)
            throws IOException {
        try {
            PGPVerifier verifier = new PGPVerifier();
            boolean verified = verifier.verifySignatureFromFiles(sha256Path, signaturePath, publicKeyPath);

            if (!verified) {
                throw new IOException("GPG signature verification failed");
            }

            // Continue with checksum verification
            verifyChecksum(cliPath, sha256Path);

            listener.getLogger().println("Successfully verified Wiz CLI signature and checksum");
        } catch (Exception e) {
            throw new IOException("GPG signature verification failed: " + e.getMessage(), e);
        }
    }

    private static void verifyChecksum(String cliPath, String sha256Path) throws IOException {
        String expectedHash = new String(Files.readAllBytes(Paths.get(sha256Path))).trim();
        String actualHash = calculateSHA256(cliPath);

        if (!expectedHash.equals(actualHash)) {
            throw new IOException("SHA256 checksum verification failed");
        }
    }

    private static String calculateSHA256(String filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(Paths.get(filePath)));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA256: " + e.getMessage());
        }
    }

    /**
     * Makes the CLI file executable using Java file permissions.
     *
     * @param cliPath Path to the CLI executable
     * @throws IOException if setting the executable permission fails
     */
    private static void makeExecutable(String cliPath) throws IOException {
        File cliFile = new File(cliPath);

        if (!cliFile.exists()) {
            throw new IOException("CLI file not found at: " + cliPath);
        }

        if (!cliFile.setExecutable(true, true)) {  // true, true means executable by owner only
            throw new IOException("Failed to make CLI executable: " + cliPath);
        }
    }

    private static void cleanupVerificationFiles(FilePath workspace, TaskListener listener) {
        FilePath[] filesToClean = {
            workspace.child("wizcli-sha256"), workspace.child("wizcli-sha256.sig"), workspace.child("public_key.asc")
        };

        for (FilePath file : filesToClean) {
            try {
                if (file.exists()) {
                    file.delete();
                    LOGGER.log(Level.FINE, "Deleted verification file: {0}", file.getRemote());
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete verification file: " + file.getRemote(), e);
                listener.getLogger().println("Warning: Failed to delete " + file.getRemote());
            }
        }
    }
}
