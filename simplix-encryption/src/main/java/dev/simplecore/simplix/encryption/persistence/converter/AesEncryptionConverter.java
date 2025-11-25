package dev.simplecore.simplix.encryption.persistence.converter;

import dev.simplecore.simplix.encryption.service.EncryptionService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for two-way AES encryption
 * <p>
 * Automatically encrypts data when saving to database and decrypts when loading from database.
 * Uses EncryptionService for AES-GCM authenticated encryption.
 * <p>
 * Usage:
 * <pre>
 * {@code
 * @Entity
 * public class UserAccount {
 *     @Convert(converter = AesEncryptionConverter.class)
 *     @Column(name = "email")
 *     private String email;  // Automatically encrypted/decrypted
 * }
 * }
 * </pre>
 * <p>
 * Features:
 * - AES-GCM with 256-bit keys
 * - Authenticated encryption (prevents tampering)
 * - Automatic encryption detection (prevents double-encryption)
 * - Thread-safe
 * <p>
 * NOTE: This converter is registered as a Spring Bean (@Component) to enable dependency injection.
 * Spring Boot automatically registers Spring-managed converters with JPA.
 */
@Converter(autoApply = false)
@Component
@Slf4j
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private static EncryptionService encryptionService;

    /**
     * Default constructor for JPA
     */
    public AesEncryptionConverter() {
    }

    /**
     * Setter injection for Spring (stores in static field for JPA access)
     */
    @Autowired
    public void setEncryptionService(EncryptionService service) {
        AesEncryptionConverter.encryptionService = service;
        log.debug("Encryption service injected into AesEncryptionConverter");
    }

    /**
     * Encrypt plain text before saving to database
     * <p>
     * If value is already encrypted, returns as-is to prevent double-encryption
     *
     * @param plainText Plain text value
     * @return Encrypted value
     */
    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // Check if encryption service is available
        if (encryptionService == null) {
            log.error("Encryption service not available - cannot store sensitive data");
            throw new IllegalStateException("Encryption service not initialized");
        }

        // Check if already encrypted to prevent double-encryption
        try {
            if (encryptionService.isEncrypted(plainText)) {
                log.debug("Value already encrypted, skipping encryption");
                return plainText;
            }
        } catch (Exception e) {
            log.debug("Could not determine if value is encrypted, attempting encryption");
        }

        // Encrypt the plain text value
        try {
            var encryptedData = encryptionService.encrypt(plainText);
            if (encryptedData != null && encryptedData.getData() != null) {
                log.debug("Value encrypted successfully");
                return encryptedData.getData();
            } else {
                log.error("Encryption returned null, cannot store data securely");
                throw new IllegalStateException("Encryption service returned null");
            }
        } catch (Exception e) {
            log.error("Failed to encrypt value", e);
            throw new RuntimeException("Failed to encrypt sensitive data", e);
        }
    }

    /**
     * Decrypt encrypted value when loading from database
     * <p>
     * If value is not encrypted, returns as-is (for backward compatibility with existing plain text data)
     *
     * @param encryptedText Encrypted value from database
     * @return Decrypted plain text value
     */
    @Override
    public String convertToEntityAttribute(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        // Check if encryption service is available
        if (encryptionService == null) {
            log.error("Encryption service not available - cannot decrypt sensitive data");
            throw new IllegalStateException("Encryption service not initialized");
        }

        // Check if value is actually encrypted
        try {
            if (!encryptionService.isEncrypted(encryptedText)) {
                log.warn("Value not encrypted, returning as-is (legacy data?)");
                return encryptedText;
            }
        } catch (Exception e) {
            log.debug("Could not determine if value is encrypted, attempting decryption");
        }

        // Decrypt the encrypted value
        try {
            String decrypted = encryptionService.decrypt(encryptedText);
            log.debug("Value decrypted successfully");
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt value", e);
            throw new RuntimeException("Failed to decrypt sensitive data", e);
        }
    }
}
