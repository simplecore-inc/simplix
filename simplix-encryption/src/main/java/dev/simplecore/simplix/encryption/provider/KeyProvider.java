package dev.simplecore.simplix.encryption.provider;

import javax.crypto.SecretKey;
import java.util.Map;

/**
 * Unified interface for key management across different environments.
 * Implementations can use simple keys (dev) or Vault (production).
 */
public interface KeyProvider {

    /**
     * Gets the current active encryption key.
     *
     * @return Current SecretKey for encryption
     */
    SecretKey getCurrentKey();

    /**
     * Gets a specific key version for decryption.
     *
     * @param version Key version identifier
     * @return SecretKey for the specified version
     */
    SecretKey getKey(String version);

    /**
     * Gets the current key version identifier.
     *
     * @return Current key version string
     */
    String getCurrentVersion();

    /**
     * Rotates to a new encryption key.
     *
     * @return New key version identifier
     */
    String rotateKey();

    /**
     * Validates if the key provider is properly configured.
     *
     * @return true if configuration is valid
     */
    boolean isConfigured();

    /**
     * Gets the name of this key provider implementation.
     *
     * @return Name identifying this key provider
     */
    String getName();

    /**
     * Gets statistical information about the key provider.
     * Includes information like current version, number of cached keys, rotation status, etc.
     *
     * @return Map containing key statistics
     */
    default Map<String, Object> getKeyStatistics() {
        return Map.of(
            "provider", getName(),
            "currentVersion", getCurrentVersion(),
            "configured", isConfigured()
        );
    }
}