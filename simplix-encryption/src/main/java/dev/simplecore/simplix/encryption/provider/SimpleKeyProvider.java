package dev.simplecore.simplix.encryption.provider;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Simple key provider for development and testing environments.
 * Uses a static key derived from configuration or environment.
 * WARNING: This provider does NOT support key rotation and should NEVER be used in production.
 */
@Slf4j
public class SimpleKeyProvider extends AbstractKeyProvider {

    @Value("${simplix.encryption.static-key:dev-default-key-do-not-use-in-production}")
    private String staticKey;

    @Value("${simplix.encryption.simple.allow-rotation:false}")
    private boolean allowRotation;

    private SecretKey secretKey;

    @PostConstruct
    public void initialize() {
        try {
            // Derive a proper AES key from the configured string
            this.secretKey = deriveKeyFromString(staticKey);
            this.currentVersion = "static";
            this.permanentKeyCache.put(currentVersion, secretKey);

            log.warn("⚠ SimpleKeyProvider initialized - DO NOT USE IN PRODUCTION");
            log.info("✔ Static key version: {}", currentVersion);

            // Disable rotation for SimpleKeyProvider by default
            if (!allowRotation) {
                this.rotationEnabled = false;
                this.autoRotation = false;
            }

        } catch (Exception e) {
            log.error("✖ Failed to initialize SimpleKeyProvider", e);
            throw new IllegalStateException("SimpleKeyProvider initialization failed", e);
        }
    }

    @Override
    public SecretKey getCurrentKey() {
        if (secretKey == null) {
            throw new IllegalStateException("SimpleKeyProvider not initialized");
        }
        return secretKey;
    }

    @Override
    public SecretKey getKey(String version) {
        if ("static".equals(version) || version == null) {
            return getCurrentKey();
        }

        // Check cache for any other versions (shouldn't exist for SimpleKeyProvider)
        SecretKey cached = permanentKeyCache.get(version);
        if (cached != null) {
            return cached;
        }

        log.warn("⚠ Unknown key version requested: {}. Returning current key.", version);
        return getCurrentKey();
    }

    @Override
    public String getCurrentVersion() {
        return currentVersion;
    }

    @Override
    public String rotateKey() {
        if (!allowRotation) {
            log.warn("⚠ Key rotation is disabled for SimpleKeyProvider");
            return currentVersion;
        }

        log.warn("⚠ SimpleKeyProvider does not support key rotation in practice");
        return currentVersion;
    }

    @Override
    public boolean isConfigured() {
        return secretKey != null && currentVersion != null;
    }

    @Override
    public String getName() {
        return "SimpleKeyProvider";
    }

    @Override
    public Map<String, Object> getKeyStatistics() {
        Map<String, Object> stats = super.getKeyStatistics();
        stats.put("warning", "Development only - No rotation support");
        stats.put("staticKey", "****" + staticKey.substring(Math.max(0, staticKey.length() - 4)));
        return stats;
    }

    /**
     * Derives a valid AES-256 key from an arbitrary string.
     * Uses SHA-256 to ensure consistent key size.
     */
    private SecretKey deriveKeyFromString(String keyString) throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(keyString.getBytes(StandardCharsets.UTF_8));

        // Ensure we have exactly 256 bits (32 bytes) for AES-256
        key = Arrays.copyOf(key, 32);

        SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);

        log.debug("Derived AES key from static string (hash: {})",
            Base64.getEncoder().encodeToString(Arrays.copyOf(key, 8)));

        return secretKey;
    }

    /**
     * Validates the configuration for SimpleKeyProvider.
     */
    @Override
    public boolean validateConfiguration() {
        if (staticKey == null || staticKey.isEmpty()) {
            log.error("✖ Static key is not configured");
            return false;
        }

        if (staticKey.equals("dev-default-key-do-not-use-in-production")) {
            log.warn("⚠ Using default development key - NEVER use in production!");
        }

        if (staticKey.length() < 16) {
            log.warn("⚠ Static key is too short (< 16 characters) - security risk!");
        }

        return super.validateConfiguration();
    }
}