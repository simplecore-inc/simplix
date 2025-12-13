package dev.simplecore.simplix.auth.jwe.service;

import dev.simplecore.simplix.auth.jwe.exception.JweKeyException;
import dev.simplecore.simplix.auth.jwe.provider.DatabaseJweKeyProvider;
import dev.simplecore.simplix.auth.jwe.store.JweKeyData;
import dev.simplecore.simplix.auth.jwe.store.JweKeyStore;
import dev.simplecore.simplix.encryption.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for JWE key rotation operations.
 * Generates new RSA key pairs, encrypts them, and stores via JweKeyStore.
 *
 * <p>This service provides the key generation and rotation logic.
 * Applications are responsible for:</p>
 * <ul>
 *   <li>Implementing {@link JweKeyStore} for persistence</li>
 *   <li>Scheduling rotation (via @Scheduled, ShedLock, etc.)</li>
 *   <li>Coordinating rotation in distributed environments</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // In your application's scheduled task
 * @Scheduled(cron = "0 0 2 1 * *")  // Monthly at 2 AM on the 1st
 * @SchedulerLock(name = "jweKeyRotation")
 * public void rotateJweKeys() {
 *     jweKeyRotationService.rotateKey();
 * }
 * }</pre>
 *
 * <h2>Rotation Flow</h2>
 * <ol>
 *   <li>Generate new RSA key pair</li>
 *   <li>Encode keys to Base64</li>
 *   <li>Encrypt key material via EncryptionService</li>
 *   <li>Save to storage via JweKeyStore</li>
 *   <li>Deactivate previous keys</li>
 *   <li>Refresh DatabaseJweKeyProvider cache</li>
 * </ol>
 *
 * @see JweKeyStore
 * @see DatabaseJweKeyProvider
 */
@Slf4j
public class JweKeyRotationService {

    private static final String KEY_VERSION_PREFIX = "jwe-v";
    private static final String ROTATION_MARKER_PREFIX = "AFTER-";
    private static final int DEFAULT_RSA_KEY_SIZE = 2048;
    private static final int DEFAULT_TOKEN_LIFETIME_SECONDS = 604800;  // 7 days (refresh token)
    private static final int DEFAULT_BUFFER_SECONDS = 86400;  // 1 day

    private final JweKeyStore keyStore;
    private final EncryptionService encryptionService;
    private final DatabaseJweKeyProvider keyProvider;
    private final int rsaKeySize;
    private final int maxTokenLifetimeSeconds;
    private final int bufferSeconds;
    private final boolean autoCleanup;

    /**
     * Creates a new JweKeyRotationService with default settings.
     *
     * @param keyStore          Storage implementation for key persistence
     * @param encryptionService Encryption service for key material encryption
     * @param keyProvider       Key provider to refresh after rotation
     */
    public JweKeyRotationService(
            JweKeyStore keyStore,
            EncryptionService encryptionService,
            DatabaseJweKeyProvider keyProvider) {
        this(keyStore, encryptionService, keyProvider, DEFAULT_RSA_KEY_SIZE,
            DEFAULT_TOKEN_LIFETIME_SECONDS, DEFAULT_BUFFER_SECONDS, false);
    }

    /**
     * Creates a new JweKeyRotationService with specified RSA key size.
     *
     * @param keyStore          Storage implementation for key persistence
     * @param encryptionService Encryption service for key material encryption
     * @param keyProvider       Key provider to refresh after rotation
     * @param rsaKeySize        RSA key size in bits (e.g., 2048, 4096)
     */
    public JweKeyRotationService(
            JweKeyStore keyStore,
            EncryptionService encryptionService,
            DatabaseJweKeyProvider keyProvider,
            int rsaKeySize) {
        this(keyStore, encryptionService, keyProvider, rsaKeySize,
            DEFAULT_TOKEN_LIFETIME_SECONDS, DEFAULT_BUFFER_SECONDS, false);
    }

    /**
     * Creates a new JweKeyRotationService with full configuration.
     *
     * @param keyStore               Storage implementation for key persistence
     * @param encryptionService      Encryption service for key material encryption
     * @param keyProvider            Key provider to refresh after rotation
     * @param rsaKeySize             RSA key size in bits (e.g., 2048, 4096)
     * @param maxTokenLifetimeSeconds Maximum token lifetime in seconds (usually refresh token lifetime)
     * @param bufferSeconds          Buffer period added to token lifetime for key expiration
     * @param autoCleanup            Whether to automatically delete expired keys during rotation
     */
    public JweKeyRotationService(
            JweKeyStore keyStore,
            EncryptionService encryptionService,
            DatabaseJweKeyProvider keyProvider,
            int rsaKeySize,
            int maxTokenLifetimeSeconds,
            int bufferSeconds,
            boolean autoCleanup) {
        this.keyStore = keyStore;
        this.encryptionService = encryptionService;
        this.keyProvider = keyProvider;
        this.rsaKeySize = rsaKeySize;
        this.maxTokenLifetimeSeconds = maxTokenLifetimeSeconds;
        this.bufferSeconds = bufferSeconds;
        this.autoCleanup = autoCleanup;
    }

    /**
     * Rotates the JWE encryption key.
     * Generates a new RSA key pair, encrypts it, stores it, and updates the current version.
     *
     * <p>After rotation:</p>
     * <ul>
     *   <li>New tokens will be encrypted with the new key</li>
     *   <li>Existing tokens can still be decrypted with old keys</li>
     *   <li>Old keys remain in storage but are marked inactive</li>
     *   <li>If autoCleanup is enabled, expired keys are deleted</li>
     * </ul>
     *
     * <p>Race condition protection:</p>
     * <p>Uses initializationMarker with unique constraint to prevent duplicate key creation
     * in distributed environments. The marker is based on the current active key version,
     * ensuring all servers attempting the same rotation use the same marker value.
     * If another server wins the race, this method logs a warning and returns null
     * instead of throwing an exception.</p>
     *
     * @return The new key version identifier, or null if another server already rotated
     */
    public String rotateKey() {
        return rotateKeyInternal(null);
    }

    /**
     * Internal method for key rotation with optional initialization marker.
     *
     * @param initMarker Initialization marker (null for regular rotation)
     * @return The new key version identifier, or null if another server already completed rotation
     */
    private String rotateKeyInternal(String initMarker) {
        log.info("Starting JWE key rotation...");

        try {
            // Clean up expired keys first if auto-cleanup is enabled
            if (autoCleanup) {
                int deleted = cleanupExpiredKeys();
                if (deleted > 0) {
                    log.info("Cleaned up {} expired JWE key(s)", deleted);
                }
            }

            // Determine marker for race condition protection
            String marker = initMarker;
            if (marker == null) {
                // For regular rotation, use marker based on current active key
                // All servers rotating the same key will use the same marker
                marker = keyStore.findCurrent()
                    .map(current -> ROTATION_MARKER_PREFIX + current.getVersion())
                    .orElseGet(() -> {
                        // Fallback: if no active key, use the most recent key version
                        // This handles edge cases where all keys are inactive
                        return keyStore.findAll().stream()
                            .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                            .map(latest -> ROTATION_MARKER_PREFIX + latest.getVersion())
                            .orElse(JweKeyData.INIT_MARKER);  // Only use INIT if no keys exist at all
                    });
            }

            // Generate new RSA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(rsaKeySize);
            KeyPair newKeyPair = keyGen.generateKeyPair();

            // Create version identifier using timestamp + UUID suffix for uniqueness
            // This prevents version collision even if two servers rotate at the same millisecond
            String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
            String newVersion = KEY_VERSION_PREFIX + System.currentTimeMillis() + "-" + uniqueSuffix;

            // Encode keys to Base64
            String publicKeyBase64 = Base64.getEncoder().encodeToString(
                newKeyPair.getPublic().getEncoded());
            String privateKeyBase64 = Base64.getEncoder().encodeToString(
                newKeyPair.getPrivate().getEncoded());

            // Encrypt key material using simplix-encryption
            String encryptedPublicKey = encryptionService.encrypt(publicKeyBase64).getData();
            String encryptedPrivateKey = encryptionService.encrypt(privateKeyBase64).getData();

            // Calculate key expiration time
            // expiresAt = now + max token lifetime + buffer
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(maxTokenLifetimeSeconds + bufferSeconds);

            // Prepare key data for storage with marker for race condition protection
            JweKeyData keyData = JweKeyData.builder()
                .version(newVersion)
                .encryptedPublicKey(encryptedPublicKey)
                .encryptedPrivateKey(encryptedPrivateKey)
                .active(true)
                .createdAt(now)
                .expiresAt(expiresAt)
                .initializationMarker(marker)
                .build();

            // IMPORTANT: Save new key FIRST, then deactivate old keys
            // This order ensures that if save fails (e.g., unique constraint violation),
            // the existing active key remains active and service continues uninterrupted.
            // If we deactivated first and save failed, there would be no active key.
            keyStore.save(keyData);
            keyStore.deactivateAllExcept(newVersion);

            // Refresh provider cache to pick up new key
            keyProvider.refresh();

            log.info("JWE key rotation completed, new version: {}, expires at: {}", newVersion, expiresAt);
            return newVersion;

        } catch (NoSuchAlgorithmException e) {
            throw new JweKeyException("RSA algorithm not available", e);
        } catch (Exception e) {
            // Check if this is a unique constraint violation (another server won the race)
            if (isUniqueConstraintViolation(e)) {
                log.warn("JWE key rotation skipped - another server already completed rotation (marker constraint violation)");
                // Refresh cache to pick up the key created by the other server
                keyProvider.refresh();
                return null;
            }
            log.error("JWE key rotation failed", e);
            throw new JweKeyException("Failed to rotate JWE key", e);
        }
    }

    /**
     * Checks if the exception is caused by a unique constraint violation.
     * This typically happens when another server wins the race condition.
     *
     * @param e The exception to check
     * @return true if the exception is due to unique constraint violation
     */
    private boolean isUniqueConstraintViolation(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null) {
                String lowerMessage = message.toLowerCase();
                // Check for common unique constraint violation indicators
                if (lowerMessage.contains("unique constraint") ||
                    lowerMessage.contains("duplicate key") ||
                    lowerMessage.contains("unique index") ||
                    lowerMessage.contains("uniqueviolation") ||
                    lowerMessage.contains("constraint violation") ||
                    lowerMessage.contains("uk_jwe_keys_init_marker")) {
                    return true;
                }
            }
            // Check for SQL state codes (23505 is PostgreSQL unique violation)
            if (cause instanceof java.sql.SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                if ("23505".equals(sqlState) || "23000".equals(sqlState)) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Cleans up expired keys from storage.
     * Keys are considered expired when their expiresAt is before the current time.
     *
     * <p>This method is called automatically during rotation if autoCleanup is enabled,
     * but can also be called manually for scheduled cleanup.</p>
     *
     * @return Number of keys deleted
     */
    public int cleanupExpiredKeys() {
        try {
            int deleted = keyStore.deleteExpired();
            if (deleted > 0) {
                // Refresh provider cache to remove expired keys from memory
                keyProvider.refresh();
                log.info("Deleted {} expired JWE key(s)", deleted);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("Failed to cleanup expired JWE keys: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Initializes the key store with an initial key if none exists.
     * Should be called during application startup when auto-initialize is enabled.
     *
     * <p>Race condition protection:</p>
     * <p>Uses initializationMarker="INITIAL" with unique constraint.
     * In distributed environments, multiple servers may try to initialize simultaneously,
     * but only one will succeed due to the unique constraint. Others will log a warning
     * and continue without throwing an exception.</p>
     *
     * <p>This method never throws exceptions - all errors are logged and handled gracefully
     * to prevent application startup failures in distributed environments.</p>
     *
     * @return true if initial key was created, false if keys already exist or another server created it
     */
    public boolean initializeIfEmpty() {
        if (keyStore.findCurrent().isEmpty() && keyStore.findAll().isEmpty()) {
            log.info("No JWE keys found in storage, generating initial key...");
            try {
                String newVersion = rotateKeyInternal(JweKeyData.INIT_MARKER);
                if (newVersion != null) {
                    return true;
                }
                // rotateKeyInternal returned null, meaning another server won the race
                log.info("Another server already initialized JWE key");
                return false;
            } catch (JweKeyException e) {
                // Check if another server already initialized
                if (keyStore.findCurrent().isPresent()) {
                    log.info("Another server already initialized JWE key, refreshing cache...");
                    keyProvider.refresh();
                    return false;
                }
                // Log error but don't throw - allow application to start
                log.error("Failed to initialize JWE key, but continuing application startup: {}", e.getMessage());
                return false;
            }
        }

        log.debug("JWE keys already exist in storage, skipping initialization");
        return false;
    }

    /**
     * Gets the current RSA key size configuration.
     *
     * @return RSA key size in bits
     */
    public int getRsaKeySize() {
        return rsaKeySize;
    }
}
