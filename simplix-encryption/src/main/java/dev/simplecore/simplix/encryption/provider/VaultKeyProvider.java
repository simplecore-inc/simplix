package dev.simplecore.simplix.encryption.provider;

import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Vault-based key provider for production environments.
 * Uses lazy loading - keys are fetched from Vault only when needed.
 * All keys are cached permanently to ensure historical data can be decrypted.
 *
 * Note: This bean is created by KeyProviderConfiguration to ensure only one provider is active.
 */
@Setter
@Slf4j
public class VaultKeyProvider extends AbstractKeyProvider {

    /**
     * Default constructor for dependency injection
     */
    public VaultKeyProvider() {
        // VaultTemplate will be set via setter
    }

    /**
     * Constructor with dependencies
     */
    public VaultKeyProvider(VaultTemplate vaultTemplate, boolean vaultEnabled) {
        this.vaultTemplate = vaultTemplate;
        this.vaultEnabled = vaultEnabled;
    }

    // Vault-specific constants
    private static final String KEY_PATH_PREFIX = "secret/encryption/keys";
    private static final String CURRENT_KEY_PATH = KEY_PATH_PREFIX + "/current";

    // Setter methods for dependency injection
    private VaultTemplate vaultTemplate;
    private boolean vaultEnabled = true;

    @PostConstruct
    public void initialize() {
        if (!vaultEnabled) {
            log.warn("⚠ Vault is disabled in configuration");
            return;
        }

        if (vaultTemplate == null) {
            throw new IllegalStateException(
                "VaultTemplate not configured. Please configure Spring Cloud Vault"
            );
        }

        try {
            // Check if current key exists
            VaultResponse currentResponse = vaultTemplate.read(CURRENT_KEY_PATH);

            if (currentResponse.getData() == null) {
                // Initialize first key
                log.info("Initializing first encryption key in Vault");
                String firstVersion = createNewKey();
                setCurrentVersion(firstVersion);
            } else {
                currentVersion = (String) currentResponse.getData().get("version");
                String rotatedAt = (String) currentResponse.getData().get("rotatedAt");
                if (rotatedAt != null) {
                    lastRotation = Instant.parse(rotatedAt);
                }
                log.info("✔ Vault key provider initialized with version: {}", currentVersion);
            }

            // Pre-load current key for better performance
            if (currentVersion != null) {
                getKey(currentVersion);
            }

        } catch (Exception e) {
            log.error("✖ Failed to initialize Vault key provider", e);
            throw new IllegalStateException("Vault initialization failed", e);
        }
    }

    @Override
    public SecretKey getCurrentKey() {
        // Simply get the current version from Vault and return the key
        // Each instance will check Vault independently
        refreshCurrentVersion();
        return getKey(currentVersion);
    }

    @Override
    public SecretKey getKey(String version) {
        // Check cache first
        SecretKey cached = permanentKeyCache.get(version);
        if (cached != null) {
            return cached;
        }

        // Not in cache - fetch from Vault (lazy loading)
        try {
            String keyPath = KEY_PATH_PREFIX + "/" + version;
            VaultResponse response = vaultTemplate.read(keyPath);

            if (response.getData() == null) {
                throw new IllegalArgumentException("Key not found in Vault: " + version);
            }

            String encodedKey = (String) response.getData().get("key");
            if (encodedKey == null) {
                throw new IllegalStateException("Key data is null for version: " + version);
            }

            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);

            // Cache permanently
            permanentKeyCache.put(version, key);
            log.debug("Loaded and cached key version: {} (total cached: {})",
                version, permanentKeyCache.size());

            return key;

        } catch (Exception e) {
            log.error("Failed to retrieve key version: {}", version, e);
            throw new RuntimeException("Failed to get key from Vault: " + version, e);
        }
    }

    @Override
    public String getCurrentVersion() {
        // Refresh from Vault to get latest version
        refreshCurrentVersion();
        return currentVersion;
    }

    @Override
    public String rotateKey() {
        if (!rotationEnabled) {
            log.info("ℹ Key rotation is disabled");
            return currentVersion;
        }

        try {
            // Create new key
            String newVersion = createNewKey();

            // Update current version in Vault
            setCurrentVersion(newVersion);

            lastRotation = Instant.now();

            // Log rotation using base class method
            logKeyRotation(currentVersion, newVersion, "manual");

            currentVersion = newVersion;
            return newVersion;

        } catch (Exception e) {
            log.error("✖ Key rotation failed", e);
            throw new RuntimeException("Failed to rotate key", e);
        }
    }

    @Override
    public boolean isConfigured() {
        return vaultTemplate != null && super.isConfigured();
    }

    @Override
    public String getName() {
        return "VaultKeyProvider";
    }

    /**
     * Refreshes the current key version from Vault.
     * Simple and direct - no complex synchronization needed.
     */
    private void refreshCurrentVersion() {
        try {
            VaultResponse response = vaultTemplate.read(CURRENT_KEY_PATH);
            if (response.getData() != null) {
                String latestVersion = (String) response.getData().get("version");
                if (latestVersion != null && !latestVersion.equals(currentVersion)) {
                    log.info("ℹ Current key version updated: {} -> {}", currentVersion, latestVersion);
                    currentVersion = latestVersion;

                    String rotatedAt = (String) response.getData().get("rotatedAt");
                    if (rotatedAt != null) {
                        lastRotation = Instant.parse(rotatedAt);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not refresh current version: {}", e.getMessage());
            // Use cached version if Vault is temporarily unavailable
        }
    }

    /**
     * Creates a new key and stores it in Vault.
     */
    private String createNewKey() {
        try {
            // Generate new AES key using base class method
            SecretKey newKey = generateNewKey();

            // Create version identifier using base class method
            String version = createVersionIdentifier();

            // Store in Vault
            String keyPath = KEY_PATH_PREFIX + "/" + version;
            Map<String, Object> keyData = Map.of(
                "key", Base64.getEncoder().encodeToString(newKey.getEncoded()),
                "algorithm", ALGORITHM,
                "keySize", KEY_SIZE,
                "createdAt", Instant.now().toString(),
                "status", "active"
            );

            vaultTemplate.write(keyPath, keyData);

            // Cache immediately
            permanentKeyCache.put(version, newKey);

            return version;

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate key", e);
        }
    }

    /**
     * Updates the current key version in Vault.
     */
    private void setCurrentVersion(String version) {
        Map<String, Object> currentData = Map.of(
            "version", version,
            "updatedAt", Instant.now().toString(),
            "rotatedAt", Instant.now().toString()
        );

        vaultTemplate.write(CURRENT_KEY_PATH, currentData);
        this.currentVersion = version;
    }
}