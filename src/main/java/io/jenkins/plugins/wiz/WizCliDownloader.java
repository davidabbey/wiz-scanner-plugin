package io.jenkins.plugins.wiz;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
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
    public static WizCliSetup setupWizCli(FilePath workspace, String wizCliURL, TaskListener listener) throws IOException {

        try {
            // Validate CLI URL format before proceeding
            WizInputValidator.validateWizCliUrl(wizCliURL);

            // Detect OS and architecture
            boolean isWindows = SystemUtils.IS_OS_WINDOWS;
            boolean isMac = SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX;
            String arch = SystemUtils.OS_ARCH;
            String cliFileName = isWindows ? WizCliSetup.WIZCLI_WINDOWS_PATH : WizCliSetup.WIZCLI_UNIX_PATH;
            String osName = SystemUtils.OS_NAME;
            FilePath cliPath = workspace.child(cliFileName);

            downloadAndVerifyWizCli(wizCliURL, cliPath, workspace, listener);

            if (!isWindows) {
                cliPath.chmod(0755);
            }

            return new WizCliSetup(cliPath.getRemote(), isWindows, isMac, osName, arch);

        } catch (AbortException e) {
            listener.error("Invalid Wiz CLI URL format: " + e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadAndVerifyWizCli(
            String wizCliURL, FilePath cliPath, FilePath workspace, TaskListener listener)
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
                downloadFile(sha256URL, sha256File);
                downloadFile(signatureURL, signatureFile);

                // Extract public key from resources
                extractPublicKey(publicKeyFile);

                // Verify signature and checksum
                verifySignatureAndChecksum(
                        listener, cliPath, sha256File, signatureFile, publicKeyFile);

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

    private static void downloadFile(String fileURL, FilePath targetPath) throws IOException {
        URL url = new URL(fileURL);
        HttpURLConnection conn = null;

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(DOWNLOAD_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download failed with HTTP code: " + responseCode);
            }
            Objects.requireNonNull(targetPath.getParent()).mkdirs();
            inputStream = conn.getInputStream();
            outputStream = targetPath.write();
            IOUtils.copy(inputStream, outputStream);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (conn != null) conn.disconnect();
        }
    }

    private static void verifySignatureAndChecksum(
            TaskListener listener, FilePath cliPath, FilePath sha256File, FilePath signaturePath, FilePath publicKeyPath)
            throws IOException {
        try {
            PGPVerifier verifier = new PGPVerifier();
            boolean verified = verifier.verifySignatureFromFiles(sha256File.getRemote(), signaturePath.getRemote(), publicKeyPath.getRemote());

            if (!verified) {
                throw new IOException("GPG signature verification failed");
            }

            // Continue with checksum verification
            verifyChecksum(cliPath, sha256File);

            listener.getLogger().println("Successfully verified Wiz CLI signature and checksum");
        } catch (Exception e) {
            throw new IOException("GPG signature verification failed: " + e.getMessage(), e);
        }
    }

    private static void verifyChecksum(FilePath cliPath, FilePath sha256File) throws IOException, InterruptedException {
        String expectedHash = sha256File.readToString().trim();
        String actualHash = calculateSHA256(cliPath);

        if (!expectedHash.equals(actualHash)) {
            throw new IOException("SHA256 checksum verification failed");
        }
    }

    private static String calculateSHA256(FilePath filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = IOUtils.toByteArray(filePath.read());
            byte[] hash = digest.digest(fileBytes);
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