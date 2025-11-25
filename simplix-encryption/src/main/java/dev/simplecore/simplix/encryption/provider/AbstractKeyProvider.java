package dev.simplecore.simplix.encryption.provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for key providers.
 * Provides common constants and functionality for all key provider implementations.
 */
@Slf4j
public abstract class AbstractKeyProvider implements KeyProvider {

    // Common algorithm constants
    protected static final String ALGORITHM = "AES";
    protected static final int KEY_SIZE = 256;
    protected static final String KEY_VERSION_PREFIX = "v";

    // Common configuration properties
    @Value("${simplix.encryption.rotation.enabled:true}")
    protected boolean rotationEnabled;

    @Value("${simplix.encryption.rotation.days:90}")
    protected int rotationDays;

    @Value("${simplix.encryption.auto-rotation:true}")
    protected boolean autoRotation;

    // Common state management
    protected final Map<String, SecretKey> permanentKeyCache = new ConcurrentHashMap<>();
    protected volatile String currentVersion;
    protected volatile Instant lastRotation;

    /**
     * Generates a new AES key using the standard algorithm and key size.
     *
     * @return A new SecretKey
     * @throws NoSuchAlgorithmException if AES algorithm is not available
     */
    protected SecretKey generateNewKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Checks if key rotation is needed based on the configured rotation period.
     *
     * @return true if rotation is needed, false otherwise
     */
    protected boolean shouldRotate() {
        if (lastRotation == null) {
            return false;
        }

        long daysSinceRotation = ChronoUnit.DAYS.between(lastRotation, Instant.now());
        return daysSinceRotation >= rotationDays;
    }

    /**
     * Creates a new version identifier.
     * Default implementation uses timestamp-based versioning.
     *
     * @return A new version string
     */
    protected String createVersionIdentifier() {
        return KEY_VERSION_PREFIX + System.currentTimeMillis();
    }

    /**
     * Gets common key statistics applicable to all providers.
     * Subclasses can override to add provider-specific statistics.
     *
     * @return Map of statistics
     */
    public Map<String, Object> getKeyStatistics() {
        return Map.of(
            "provider", getName(),
            "currentVersion", currentVersion != null ? currentVersion : "none",
            "cachedKeys", permanentKeyCache.size(),
            "rotationEnabled", rotationEnabled,
            "autoRotation", autoRotation,
            "rotationDays", rotationDays,
            "lastRotation", lastRotation != null ? lastRotation.toString() : "never",
            "nextRotation", lastRotation != null ?
                lastRotation.plus(rotationDays, ChronoUnit.DAYS).toString() : "unknown"
        );
    }

    /**
     * Validates that the key provider is properly configured.
     *
     * @return true if configuration is valid
     */
    public boolean validateConfiguration() {
        if (rotationEnabled && rotationDays < 1) {
            log.warn("⚠ Invalid rotation period: {} days", rotationDays);
            return false;
        }
        return true;
    }

    @Override
    public boolean isConfigured() {
        return currentVersion != null && validateConfiguration();
    }

    /**
     * Logs key rotation event for audit purposes.
     *
     * @param oldVersion Previous key version
     * @param newVersion New key version
     * @param reason Reason for rotation
     */
    protected void logKeyRotation(String oldVersion, String newVersion, String reason) {
        log.info("✔ Key rotated: {} -> {} (reason: {}, provider: {})",
            oldVersion != null ? oldVersion : "none",
            newVersion,
            reason,
            getName());
    }
}