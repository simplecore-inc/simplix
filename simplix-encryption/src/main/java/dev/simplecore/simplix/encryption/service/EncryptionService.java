package dev.simplecore.simplix.encryption.service;

import dev.simplecore.simplix.encryption.provider.KeyProvider;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Unified encryption service that works with both simple and Vault key providers.
 * Provides consistent encryption/decryption API across all environments.
 *
 * The appropriate KeyProvider is selected based on the active Spring profile:
 * - dev/local/test: SimpleKeyProvider
 * - prod/staging/vault: VaultKeyProvider
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final String DELIMITER = ":";

    /**
     * -- GETTER --
     *  Gets the key provider (for migration purposes).
     */
    @Getter
    private final KeyProvider keyProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor with injected KeyProvider.
     * The @Primary KeyProvider for the active profile will be automatically selected:
     * - SimpleKeyProvider for dev/local/test profiles
     * - VaultKeyProvider for prod/staging/vault profiles
     *
     * @param keyProvider The key provider implementation to use
     */
    @Autowired
    public EncryptionService(@Autowired(required = false) KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
        if (keyProvider != null) {
            log.info("EncryptionService initialized with KeyProvider implementation");
        } else {
            log.warn("EncryptionService initialized without KeyProvider - encryption will not be available");
        }
    }

    /**
     * Encrypts plain text using the current key.
     * Format: version:iv:ciphertext
     *
     * @param plainText Text to encrypt
     * @return Encrypted data with version and IV
     */
    public EncryptedData encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }

        try {
            SecretKey key = keyProvider.getCurrentKey();
            String version = keyProvider.getCurrentVersion();

            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            // Encrypt
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combine version:iv:ciphertext
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String cipherBase64 = Base64.getEncoder().encodeToString(cipherText);
            String combined = String.join(DELIMITER, version, ivBase64, cipherBase64);

            return new EncryptedData(combined, version);

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypts encrypted data using the appropriate key version.
     *
     * @param encryptedData Data in format version:iv:ciphertext
     * @return Decrypted plain text
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return null;
        }

        try {
            // Parse format: version:iv:ciphertext
            String[] parts = encryptedData.split(DELIMITER);
            if (parts.length != 3) {
                // Try legacy format or unencrypted data
                if (!encryptedData.contains(DELIMITER)) {
                    log.warn("⚠ Attempting to decrypt non-standard format data");
                    return encryptedData; // Return as-is if not encrypted
                }
                throw new IllegalArgumentException("Invalid encrypted data format");
            }

            String version = parts[0];
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);

            // Get key for version
            SecretKey key = keyProvider.getKey(version);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Decryption failed for data: {}",
                encryptedData.substring(0, Math.min(20, encryptedData.length())) + "...", e);
            throw new DecryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Re-encrypts data with the current key (for key rotation).
     *
     * @param encryptedData Old encrypted data
     * @return New encrypted data with current key
     */
    public EncryptedData reencrypt(String encryptedData) {
        String plainText = decrypt(encryptedData);
        return encrypt(plainText);
    }

    /**
     * Checks if data is encrypted (has our format).
     *
     * @param data Data to check
     * @return true if data appears to be encrypted
     */
    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        // Check for version:iv:ciphertext format
        String[] parts = data.split(DELIMITER);
        if (parts.length != 3) {
            return false;
        }

        // Check if version starts with expected prefixes
        String version = parts[0];
        return version.startsWith("v") || version.startsWith("dev-static") || version.startsWith("static-");
    }

    /**
     * Gets the key version from encrypted data.
     *
     * @param encryptedData Encrypted data
     * @return Key version or null
     */
    public String getKeyVersion(String encryptedData) {
        if (!isEncrypted(encryptedData)) {
            return null;
        }

        String[] parts = encryptedData.split(DELIMITER);
        return parts[0];
    }

    /**
     * Validates that encryption is properly configured.
     *
     * @return true if ready to encrypt/decrypt
     */
    public boolean isConfigured() {
        return keyProvider.isConfigured();
    }

    /**
     * Encrypted data container.
     */
    @Data
    public static class EncryptedData {
        private final String data;
        private final String keyVersion;
    }

    /**
     * Custom exception for encryption failures.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for decryption failures.
     */
    public static class DecryptionException extends RuntimeException {
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}