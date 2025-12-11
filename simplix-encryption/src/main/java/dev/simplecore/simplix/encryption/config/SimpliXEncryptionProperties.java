package dev.simplecore.simplix.encryption.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SimpliX Encryption module configuration properties.
 * Defines all configurable properties for the encryption infrastructure.
 */
@Data
@ConfigurationProperties(prefix = "simplix.encryption")
public class SimpliXEncryptionProperties {

    /**
     * Enable/disable encryption module
     */
    private boolean enabled = true;

    /**
     * Key provider type: simple, managed, or vault
     */
    private String provider = "simple";

    /**
     * Static key for SimpleKeyProvider (development only)
     */
    private String staticKey = "dev-default-key-do-not-use-in-production";

    /**
     * Master key for key derivation
     */
    private String masterKey;

    /**
     * Salt for key derivation
     */
    private String salt;

    /**
     * Key store path for ManagedKeyProvider
     */
    private String keyStorePath;

    /**
     * Enable automatic key rotation
     */
    private boolean autoRotation = false;

    /**
     * Simple key provider configuration
     */
    private Simple simple = new Simple();

    /**
     * Vault key provider configuration
     */
    private Vault vault = new Vault();

    /**
     * Key rotation configuration
     */
    private Rotation rotation = new Rotation();

    /**
     * Configurable key provider configuration for multi-version key management
     */
    private Configurable configurable = new Configurable();

    @Data
    public static class Simple {
        /**
         * Allow rotation for simple key provider (not recommended)
         */
        private boolean allowRotation = false;
    }

    @Data
    public static class Vault {
        /**
         * Enable Vault key provider
         */
        private boolean enabled = false;

        /**
         * Vault secret path for encryption keys
         */
        private String path = "secret/encryption";

        /**
         * Vault namespace (for Vault Enterprise)
         */
        private String namespace;
    }

    @Data
    public static class Rotation {
        /**
         * Enable key rotation
         */
        private boolean enabled = false;

        /**
         * Key rotation interval in days
         */
        private int days = 90;
    }

    @Data
    public static class Configurable {
        /**
         * Current key version to use for encryption.
         * Must exist in the keys map and must not be deprecated.
         */
        private String currentVersion;

        /**
         * Map of key versions to their configurations.
         * Key: version identifier (e.g., "v1", "v2")
         * Value: KeyConfig containing the key and metadata
         */
        private Map<String, KeyConfig> keys = new LinkedHashMap<>();

        @Data
        public static class KeyConfig {
            /**
             * Base64-encoded AES-256 key (32 bytes after decoding)
             */
            private String key;

            /**
             * Whether this key is deprecated.
             * Deprecated keys can only be used for decryption, not encryption.
             */
            private boolean deprecated = false;
        }
    }
}
