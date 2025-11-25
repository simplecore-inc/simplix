package dev.simplecore.simplix.core.entity.converter;

import dev.simplecore.simplix.core.security.sanitization.DataMaskingUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter for reversible masking of sensitive data.
 * Stores encrypted data in database and provides masked representation for display.
 * Uses AES-GCM for authenticated encryption with integrity protection.
 */
@Converter
@Component
@Slf4j
public class MaskingConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String PREFIX = "MASKED:";
    private static final char MASK_CHAR = '*';

    @Value("${core.masking.enabled:true}")
    private boolean maskingEnabled;

    @Value("${core.masking.key:#{null}}")
    private String maskingKey;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    /**
     * Initialize the masking key
     */
    private void initializeKey() {
        if (secretKey == null) {
            if (maskingKey != null && maskingKey.length() >= 32) {
                // Use configured key
                byte[] keyBytes = maskingKey.substring(0, 32).getBytes(StandardCharsets.UTF_8);
                secretKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                // Generate default key (for development only)
                byte[] defaultKey = "DefaultMaskingKeyForDevelopment!".getBytes(StandardCharsets.UTF_8);
                secretKey = new SecretKeySpec(defaultKey, "AES");
                log.warn("⚠ Using default masking key - configure domain.masking.key for production");
            }
        }
    }

    /**
     * Encrypts and stores the value in database with integrity protection.
     * Format: PREFIX:base64(iv + ciphertext + tag)
     *
     * @param attribute Original value to protect
     * @return Encrypted value with prefix
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!maskingEnabled || attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        try {
            initializeKey();

            // Generate random IV
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            // Encode and return with prefix
            String encoded = Base64.getEncoder().encodeToString(byteBuffer.array());
            return PREFIX + encoded;

        } catch (Exception e) {
            log.error("✖ CRITICAL: Failed to mask data for storage - cannot store unencrypted sensitive data", e);
            throw new IllegalStateException("Masking failed - refusing to store plaintext sensitive data", e);
        }
    }

    /**
     * Decrypts the value from database or returns masked representation.
     * In production mode with proper permissions, returns decrypted value.
     * Otherwise returns masked representation for security.
     *
     * @param dbData Encrypted value from database
     * @return Decrypted or masked value based on context
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!maskingEnabled || dbData == null || !dbData.startsWith(PREFIX)) {
            return dbData;
        }

        try {
            // Check if we should unmask (based on security context)
            if (shouldUnmask()) {
                return decrypt(dbData);
            } else {
                // Return masked representation
                return getMaskedRepresentation(dbData);
            }
        } catch (Exception e) {
            log.error("✖ Failed to process masked data", e);
            return getMaskedRepresentation(dbData);
        }
    }

    /**
     * Decrypts the encrypted value
     */
    private String decrypt(String encryptedData) throws Exception {
        initializeKey();

        String encoded = encryptedData.substring(PREFIX.length());
        byte[] cipherMessage = Base64.getDecoder().decode(encoded);

        // Extract IV
        ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
        byte[] iv = new byte[IV_LENGTH_BYTE];
        byteBuffer.get(iv);

        // Extract ciphertext
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        // Decrypt
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] plainText = cipher.doFinal(cipherText);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * Check if current context should unmask data
     * This should check security context, user roles, etc.
     */
    private boolean shouldUnmask() {
        // Check if in privileged context
        // This is simplified - in production, check:
        // - User roles/permissions
        // - Request context (admin panel vs public API)
        // - Audit requirements

        // For now, check system property or thread-local context
        String unmaskMode = System.getProperty("domain.masking.unmask", "false");
        return "true".equals(unmaskMode);
    }

    /**
     * Returns a masked representation without decrypting
     */
    private String getMaskedRepresentation(String encryptedData) {
        // Return a consistent masked value
        return "****MASKED****";
    }

    /**
     * Masks email addresses for display.
     * Delegates to DataMaskingUtils for consistent masking.
     */
    public static String maskEmail(String email) {
        return DataMaskingUtils.maskEmail(email);
    }

    /**
     * Masks phone numbers for display.
     * Delegates to DataMaskingUtils for consistent masking.
     */
    public static String maskPhoneNumber(String phone) {
        return DataMaskingUtils.maskPhoneNumber(phone);
    }

    /**
     * Masks ID numbers for display.
     * Delegates to DataMaskingUtils for consistent masking.
     */
    public static String maskIdNumber(String idNumber) {
        return DataMaskingUtils.maskRRN(idNumber);
    }

    /**
     * Masks credit card numbers for display.
     * Delegates to DataMaskingUtils for consistent masking.
     */
    public static String maskCreditCard(String cardNumber) {
        return DataMaskingUtils.maskCreditCard(cardNumber);
    }

    /**
     * Masks IP addresses for display.
     * Delegates to DataMaskingUtils for consistent masking.
     */
    public static String maskIpAddress(String ipAddress) {
        return DataMaskingUtils.maskIpAddress(ipAddress);
    }
}
