package dev.simplecore.simplix.encryption.provider;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based key provider with automatic rotation support.
 * Provides a middle ground between simple static keys and full Vault integration.
 * Implements key lifecycle management including generation, rotation, and retirement.
 * Compliant with PCI-DSS and GDPR key management requirements.
 *
 * This provider is suitable for:
 * - Staging environments without Vault
 * - On-premise deployments with file-based key management
 * - Development environments needing key rotation testing
 */
@Slf4j
@Setter
public class ManagedKeyProvider extends AbstractKeyProvider {

    /**
     * Default constructor for dependency injection
     */
    public ManagedKeyProvider() {
        // Properties will be set via setters
    }

    /**
     * Constructor with all dependencies
     */
    public ManagedKeyProvider(LockProvider lockProvider, String keyStorePath, String masterKey, String salt) {
        this.lockProvider = lockProvider;
        this.keyStorePath = keyStorePath;
        this.masterKey = masterKey;
        this.salt = salt;
    }

    // ManagedKeyProvider-specific constants
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 100000;

    // Use keyRegistry instead of permanentKeyCache for compatibility
    private final Map<String, KeyMetadata> keyRegistry = new ConcurrentHashMap<>();
    private final ReadWriteLock keyLock = new ReentrantReadWriteLock();
    private volatile String currentKeyVersion;  // Shadows parent field for compatibility

    private LockProvider lockProvider;

    private String keyStorePath;

    private String masterKey;

    private String salt;

    /**
     * Key metadata for tracking lifecycle.
     * Keys are permanently retained - never retired or removed.
     */
    private static class KeyMetadata {
        final SecretKey key;
        final String version;
        final Instant createdAt;
        final Instant rotatedAt;
        volatile Instant lastUsed;
        volatile boolean active;

        KeyMetadata(SecretKey key, String version, Instant createdAt) {
            this.key = key;
            this.version = version;
            this.createdAt = createdAt;
            this.rotatedAt = null;
            this.lastUsed = Instant.now();
            this.active = true;
        }

        KeyMetadata(SecretKey key, String version, Instant createdAt, Instant rotatedAt) {
            this.key = key;
            this.version = version;
            this.createdAt = createdAt;
            this.rotatedAt = rotatedAt;
            this.lastUsed = Instant.now();
            this.active = false;
        }
    }

    /**
     * Initializes the key provider and loads existing keys.
     */
    @PostConstruct
    public void initialize() {
        try {
            if (keyStorePath != null) {
                loadKeysFromSecureStore();
            } else {
                initializeInMemoryKey();
            }

            if (rotationEnabled && shouldRotate()) {
                rotateKeyWithLock("init");
            }

            log.info("✔ ManagedKeyProvider initialized with {} keys", keyRegistry.size());
        } catch (Exception e) {
            log.error("✖ Failed to initialize ManagedKeyProvider", e);
            throw new IllegalStateException("Key provider initialization failed", e);
        }
    }

    /**
     * Initialize in-memory key for development/testing
     */
    private void initializeInMemoryKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (masterKey != null && salt != null) {
            SecretKey key = deriveKeyFromMaster(masterKey, salt);
            currentKeyVersion = KEY_VERSION_PREFIX + "1";
            keyRegistry.put(currentKeyVersion, new KeyMetadata(key, currentKeyVersion, Instant.now()));
            lastRotation = Instant.now();
            log.info("✔ In-memory key initialized");
        } else {
            SecretKey key = generateNewKey();
            currentKeyVersion = KEY_VERSION_PREFIX + "1";
            keyRegistry.put(currentKeyVersion, new KeyMetadata(key, currentKeyVersion, Instant.now()));
            lastRotation = Instant.now();
            log.warn("⚠ Using randomly generated key - configure master key for production");
        }
    }

    @Override
    public SecretKey getCurrentKey() {
        keyLock.readLock().lock();
        try {
            if (currentKeyVersion == null) {
                throw new IllegalStateException("No encryption key available");
            }

            KeyMetadata metadata = keyRegistry.get(currentKeyVersion);
            if (metadata == null) {
                throw new IllegalStateException("Current key is not available");
            }

            metadata.lastUsed = Instant.now();
            return metadata.key;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    @Override
    public SecretKey getKey(String version) {
        keyLock.readLock().lock();
        try {
            KeyMetadata metadata = keyRegistry.get(version);
            if (metadata == null) {
                throw new IllegalArgumentException("Key version not found: " + version);
            }

            // All keys are permanently retained - no retirement warnings needed
            metadata.lastUsed = Instant.now();
            return metadata.key;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    @Override
    public String getCurrentVersion() {
        return currentKeyVersion;
    }

    @Override
    public String rotateKey() {
        if (!rotationEnabled) {
            log.info("ℹ Key rotation is disabled");
            return currentKeyVersion;
        }

        return rotateKeyWithLock("manual");
    }

    /**
     * Performs key rotation with distributed lock protection.
     */
    private String rotateKeyWithLock(String context) {
        // If lock provider not available, proceed without distributed lock
        if (lockProvider == null) {
            log.warn("⚠ LockProvider not configured, proceeding without distributed lock");
            return performKeyRotation();
        }

        String lockName = "managed-key-rotation-" + context;
        LockConfiguration lockConfig = new LockConfiguration(
            Instant.now(),
            lockName,
            Duration.ofMinutes(5),
            Duration.ofSeconds(30)
        );

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        if (lock.isPresent()) {
            try {
                log.info("✔ Acquired lock for key rotation ({})", context);

                // Re-check if rotation is still needed
                if (context.equals("scheduled") || context.equals("init")) {
                    refreshFromStorage();
                    if (!shouldRotate()) {
                        log.info("ℹ Key rotation no longer needed after acquiring lock");
                        return currentKeyVersion;
                    }
                }

                return performKeyRotation();
            } finally {
                lock.get().unlock();
                log.info("✔ Released lock for key rotation ({})", context);
            }
        } else {
            log.info("ℹ Another instance is performing key rotation, skipping ({})", context);
            // Wait and refresh
            try {
                Thread.sleep(2000);
                refreshFromStorage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return currentKeyVersion;
        }
    }

    /**
     * Actually performs the key rotation.
     */
    private String performKeyRotation() {
        keyLock.writeLock().lock();
        try {
            SecretKey newKey = generateNewKey();
            int nextVersion = keyRegistry.size() + 1;
            String newVersion = KEY_VERSION_PREFIX + nextVersion;

            if (currentKeyVersion != null) {
                KeyMetadata current = keyRegistry.get(currentKeyVersion);
                if (current != null) {
                    current.active = false;
                }
            }

            KeyMetadata newKeyMeta = new KeyMetadata(newKey, newVersion, Instant.now());
            keyRegistry.put(newVersion, newKeyMeta);
            currentKeyVersion = newVersion;
            lastRotation = Instant.now();

            if (keyStorePath != null) {
                saveKeyToSecureStore(newVersion, newKey);
            }

            // Log rotation using base class method
            logKeyRotation(currentKeyVersion != null ? currentKeyVersion : "initial", newVersion, "managed");
            return newVersion;

        } catch (Exception e) {
            log.error("✖ Key rotation failed", e);
            throw new IllegalStateException("Failed to rotate key", e);
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    /**
     * Refreshes key information from storage.
     */
    private void refreshFromStorage() {
        if (keyStorePath == null) {
            return;
        }

        try {
            Path metadataPath = Paths.get(keyStorePath).resolve("metadata.json");
            if (Files.exists(metadataPath)) {
                String metadata = Files.readString(metadataPath);
                parseMetadata(metadata);
                log.info("✔ Refreshed key metadata from storage");
            }
        } catch (Exception e) {
            log.error("✖ Failed to refresh from storage", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return currentKeyVersion != null && !keyRegistry.isEmpty();
    }

    @Override
    public String getName() {
        return "ManagedKeyProvider";
    }

    /**
     * Check if key rotation is needed.
     * Overrides base implementation to return true if never rotated.
     */
    @Override
    protected boolean shouldRotate() {
        if (lastRotation == null) {
            return true;
        }
        return super.shouldRotate();
    }

    /**
     * Get statistics about the key cache.
     * Overrides to add ManagedKeyProvider-specific stats.
     */
    @Override
    public Map<String, Object> getKeyStatistics() {
        keyLock.readLock().lock();
        try {
            Map<String, Object> stats = new HashMap<>(super.getKeyStatistics());
            stats.put("totalKeysInRegistry", keyRegistry.size());
            stats.put("activeKeys", keyRegistry.values().stream().filter(m -> m.active).count());
            stats.put("keyStorePath", keyStorePath != null ? keyStorePath : "memory");
            return stats;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    // generateNewKey() method is inherited from AbstractKeyProvider

    /**
     * Derives a key from master key using PBKDF2
     */
    private SecretKey deriveKeyFromMaster(String masterKey, String salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {

        PBEKeySpec spec = new PBEKeySpec(
            masterKey.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            PBKDF2_ITERATIONS,
            KEY_SIZE
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Load keys from secure file storage
     */
    private void loadKeysFromSecureStore() throws IOException, NoSuchAlgorithmException {
        Path storePath = Paths.get(keyStorePath);

        if (!Files.exists(storePath)) {
            Files.createDirectories(storePath);
            if (System.getProperty("os.name").toLowerCase().contains("nix") ||
                System.getProperty("os.name").toLowerCase().contains("nux")) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
                Files.setPosixFilePermissions(storePath, perms);
            }

            SecretKey initialKey = generateNewKey();
            currentKeyVersion = KEY_VERSION_PREFIX + "1";
            keyRegistry.put(currentKeyVersion, new KeyMetadata(initialKey, currentKeyVersion, Instant.now()));
            lastRotation = Instant.now();
            saveKeyToSecureStore(currentKeyVersion, initialKey);
            return;
        }

        Files.list(storePath)
            .filter(path -> path.getFileName().toString().startsWith("key_"))
            .sorted()
            .forEach(path -> {
                try {
                    loadKeyFromFile(path);
                } catch (Exception e) {
                    log.error("✖ Failed to load key from: {}", path, e);
                }
            });

        Path metadataPath = storePath.resolve("metadata.json");
        if (Files.exists(metadataPath)) {
            String metadata = Files.readString(metadataPath);
            parseMetadata(metadata);
        }
    }

    /**
     * Load a single key from file
     */
    private void loadKeyFromFile(Path keyFile) throws IOException {
        String fileName = keyFile.getFileName().toString();
        String version = fileName.substring(4, fileName.lastIndexOf('.'));

        byte[] keyBytes = Files.readAllBytes(keyFile);
        String keyString = new String(keyBytes, StandardCharsets.UTF_8);

        String[] parts = keyString.split(":");
        byte[] decodedKey = Base64.getDecoder().decode(parts[0]);
        Instant createdAt = parts.length > 1 ? Instant.parse(parts[1]) : Instant.now();

        SecretKey key = new SecretKeySpec(decodedKey, ALGORITHM);
        KeyMetadata metadata = new KeyMetadata(key, version, createdAt);

        keyRegistry.put(version, metadata);

        if (currentKeyVersion == null || version.compareTo(currentKeyVersion) > 0) {
            currentKeyVersion = version;
        }
    }

    /**
     * Save key to secure file storage
     */
    private void saveKeyToSecureStore(String version, SecretKey key) throws IOException {
        Path storePath = Paths.get(keyStorePath);
        Files.createDirectories(storePath);

        Path keyFile = storePath.resolve("key_" + version + ".key");
        String keyData = Base64.getEncoder().encodeToString(key.getEncoded()) +
                        ":" + Instant.now().toString();

        Files.writeString(keyFile, keyData,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);

        if (System.getProperty("os.name").toLowerCase().contains("nix") ||
            System.getProperty("os.name").toLowerCase().contains("nux")) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(keyFile, perms);
        }

        saveMetadata(storePath);
    }

    /**
     * Save metadata file
     */
    private void saveMetadata(Path storePath) throws IOException {
        Path metadataPath = storePath.resolve("metadata.json");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("currentVersion", currentKeyVersion);
        metadata.put("lastRotation", lastRotation != null ? lastRotation.toString() : null);
        metadata.put("totalKeys", keyRegistry.size());

        String json = new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(metadata);

        Files.writeString(metadataPath, json,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Parse metadata from JSON
     */
    private void parseMetadata(String json) {
        try {
            Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            currentKeyVersion = (String) metadata.get("currentVersion");
            String lastRotationStr = (String) metadata.get("lastRotation");
            if (lastRotationStr != null) {
                lastRotation = Instant.parse(lastRotationStr);
            }
        } catch (Exception e) {
            log.warn("⚠ Failed to parse metadata, using defaults", e);
        }
    }


    /**
     * Validate key provider configuration
     */
    public boolean validateConfiguration() {
        if (masterKey == null || salt == null) {
            if (keyStorePath == null) {
                log.warn("⚠ No master key or key store configured");
                return false;
            }
        }

        if (masterKey != null && masterKey.length() < 32) {
            log.warn("⚠ Master key too short (minimum 32 characters)");
            return false;
        }

        if (salt != null && salt.length() < 16) {
            log.warn("⚠ Salt too short (minimum 16 characters)");
            return false;
        }

        if (rotationEnabled && rotationDays < 1) {
            log.warn("⚠ Invalid rotation period");
            return false;
        }

        return true;
    }
}