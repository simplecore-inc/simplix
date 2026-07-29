package dev.simplecore.simplix.license.integrity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies the runtime integrity of the application binary.
 *
 * <p>Computes SHA-256 checksum of the running JAR/native binary and
 * compares against a known-good hash. Detects binary patching attempts.
 *
 * <p>Most effective when deployed as a GraalVM Native Image where
 * bytecode is not available for modification.
 */
public class RuntimeIntegrityChecker {

    private static final Logger log = LoggerFactory.getLogger(RuntimeIntegrityChecker.class);

    private final String expectedHash;

    /**
     * @param expectedHash expected SHA-256 hash of the application binary, or null to skip
     */
    public RuntimeIntegrityChecker(String expectedHash) {
        this.expectedHash = expectedHash;
    }

    /**
     * Check the integrity of the current application binary.
     *
     * @return true if integrity check passes or is not configured
     */
    public boolean verify() {
        if (expectedHash == null || expectedHash.isBlank()) {
            log.debug("Runtime integrity check skipped: no expected hash configured");
            return true;
        }

        String binaryPath = detectBinaryPath();
        if (binaryPath == null) {
            log.warn("Could not determine application binary path for integrity check");
            return true;
        }

        String actualHash = computeSha256(Path.of(binaryPath));
        if (actualHash == null) {
            log.warn("Failed to compute hash of application binary");
            return true;
        }

        boolean match = expectedHash.equalsIgnoreCase(actualHash);
        if (!match) {
            log.error("Runtime integrity check FAILED: expected={}, actual={}", expectedHash, actualHash);
        } else {
            log.debug("Runtime integrity check passed");
        }
        return match;
    }

    private String detectBinaryPath() {
        // For GraalVM native image
        String nativePath = System.getProperty("org.graalvm.nativeimage.imagelocation");
        if (nativePath != null) {
            return nativePath;
        }

        // For JAR execution
        try {
            String jarPath = getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            if (jarPath.endsWith(".jar")) {
                return jarPath;
            }
        } catch (Exception e) {
            log.debug("Could not determine JAR path", e);
        }

        return null;
    }

    private String computeSha256(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to compute SHA-256 of binary: {}", path, e);
            return null;
        }
    }
}
