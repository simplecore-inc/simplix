package dev.simplecore.simplix.encryption.provider;

import dev.simplecore.simplix.encryption.config.SimpliXEncryptionProperties;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration file-based key provider with multi-version support.
 * Manages encryption keys defined in application configuration (YAML/properties).
 *
 * Features:
 * - Multiple key versions for backward compatibility
 * - Deprecated key support (decrypt-only)
 * - Current version designation for encryption
 * - Base64-encoded key input
 *
 * This provider is suitable for:
 * - Environments where keys are managed through configuration management
 * - Kubernetes ConfigMaps/Secrets-based deployments
 * - Scenarios requiring explicit key version control
 */
@Setter
@Slf4j
public class ConfigurableKeyProvider extends AbstractKeyProvider {

    private static final int REQUIRED_KEY_SIZE_BYTES = 32; // AES-256

    private SimpliXEncryptionProperties.Configurable config;

    private final Set<String> deprecatedVersions = new HashSet<>();

    /**
     * Default constructor for dependency injection
     */
    public ConfigurableKeyProvider() {
    }

    /**
     * Constructor with configuration
     */
    public ConfigurableKeyProvider(SimpliXEncryptionProperties.Configurable config) {
        this.config = config;
    }

    @PostConstruct
    public void initialize() {
        validateConfiguration();

        loadKeys();

        this.currentVersion = config.getCurrentVersion();

        this.rotationEnabled = false;
        this.autoRotation = false;

        log.info("✔ ConfigurableKeyProvider initialized with {} keys, current version: {}",
            permanentKeyCache.size(), currentVersion);

        if (!deprecatedVersions.isEmpty()) {
            log.info("ℹ Deprecated key versions (decrypt-only): {}", deprecatedVersions);
        }
    }

    @Override
    public SecretKey getCurrentKey() {
        if (currentVersion == null) {
            throw new IllegalStateException("No current key version configured");
        }

        SecretKey key = permanentKeyCache.get(currentVersion);
        if (key == null) {
            throw new IllegalStateException("Current key not found: " + currentVersion);
        }

        return key;
    }

    @Override
    public SecretKey getKey(String version) {
        SecretKey key = permanentKeyCache.get(version);
        if (key == null) {
            throw new IllegalArgumentException("Key version not found: " + version);
        }

        return key;
    }

    @Override
    public String getCurrentVersion() {
        return currentVersion;
    }

    @Override
    public String rotateKey() {
        log.warn("⚠ Key rotation is not supported for ConfigurableKeyProvider. " +
                 "Update configuration and restart to change keys.");
        return currentVersion;
    }

    @Override
    public boolean isConfigured() {
        return currentVersion != null &&
               !permanentKeyCache.isEmpty() &&
               permanentKeyCache.containsKey(currentVersion);
    }

    @Override
    public String getName() {
        return "ConfigurableKeyProvider";
    }

    @Override
    public Map<String, Object> getKeyStatistics() {
        Map<String, Object> stats = new HashMap<>(super.getKeyStatistics());
        stats.put("totalConfiguredKeys", config.getKeys().size());
        stats.put("deprecatedKeys", deprecatedVersions.size());
        stats.put("activeKeys", config.getKeys().size() - deprecatedVersions.size());
        stats.put("deprecatedVersions", deprecatedVersions);
        return stats;
    }

    @Override
    public boolean validateConfiguration() {
        if (config == null) {
            throw new IllegalStateException("Configurable key provider configuration is not set");
        }

        if (config.getCurrentVersion() == null || config.getCurrentVersion().isBlank()) {
            throw new IllegalStateException("current-version must be specified");
        }

        if (config.getKeys() == null || config.getKeys().isEmpty()) {
            throw new IllegalStateException("At least one key must be configured");
        }

        if (!config.getKeys().containsKey(config.getCurrentVersion())) {
            throw new IllegalStateException(
                "current-version '" + config.getCurrentVersion() + "' does not exist in keys");
        }

        SimpliXEncryptionProperties.Configurable.KeyConfig currentKeyConfig =
            config.getKeys().get(config.getCurrentVersion());
        if (currentKeyConfig.isDeprecated()) {
            throw new IllegalStateException(
                "current-version '" + config.getCurrentVersion() + "' cannot be deprecated");
        }

        for (Map.Entry<String, SimpliXEncryptionProperties.Configurable.KeyConfig> entry :
             config.getKeys().entrySet()) {
            validateKeyConfig(entry.getKey(), entry.getValue());
        }

        return true;
    }

    /**
     * Validates a single key configuration.
     */
    private void validateKeyConfig(String version,
                                   SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig) {
        if (keyConfig.getKey() == null || keyConfig.getKey().isBlank()) {
            throw new IllegalStateException("Key for version '" + version + "' is empty");
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyConfig.getKey());
            if (keyBytes.length != REQUIRED_KEY_SIZE_BYTES) {
                throw new IllegalStateException(
                    "Key for version '" + version + "' must be " + REQUIRED_KEY_SIZE_BYTES +
                    " bytes (AES-256), but was " + keyBytes.length + " bytes");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "Key for version '" + version + "' is not valid Base64", e);
        }
    }

    /**
     * Loads all configured keys into the permanent cache.
     */
    private void loadKeys() {
        for (Map.Entry<String, SimpliXEncryptionProperties.Configurable.KeyConfig> entry :
             config.getKeys().entrySet()) {
            String version = entry.getKey();
            SimpliXEncryptionProperties.Configurable.KeyConfig keyConfig = entry.getValue();

            byte[] keyBytes = Base64.getDecoder().decode(keyConfig.getKey());
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            permanentKeyCache.put(version, secretKey);

            if (keyConfig.isDeprecated()) {
                deprecatedVersions.add(version);
            }

            log.debug("Loaded key version: {} (deprecated: {})", version, keyConfig.isDeprecated());
        }
    }

    /**
     * Checks if a specific key version is deprecated.
     *
     * @param version Key version to check
     * @return true if the version is deprecated
     */
    public boolean isDeprecated(String version) {
        return deprecatedVersions.contains(version);
    }

    /**
     * Gets all available key versions.
     *
     * @return Set of all key versions (including deprecated)
     */
    public Set<String> getAvailableVersions() {
        return permanentKeyCache.keySet();
    }
}