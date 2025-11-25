package dev.simplecore.simplix.core.entity.converter;


import dev.simplecore.simplix.core.security.hashing.HashingUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter that automatically hashes values for storage and comparison.
 * This converter provides transparent hashing for fields that need to be searchable
 * but should not be stored in plain text.
 * <p>
 * Features:
 * - Automatically hashes plain text values when saving to database
 * - Prevents double-hashing by detecting already-hashed values
 * - Works transparently with JPA queries and comparisons
 * - One-way hashing (values cannot be reversed)
 * <p>
 * Usage:
 * <pre>
 * @Column(name = "email_hashed")
 * @Convert(converter = HashingAttributeConverter.class)
 * private String emailHashed;
 * </pre>
 */
@Converter(autoApply = false)  // Don't auto-apply to all String fields, use @Convert annotation explicitly
@Component
@Slf4j
public class HashingAttributeConverter implements AttributeConverter<String, String> {

    /**
     * Default constructor for JPA
     */
    public HashingAttributeConverter() {
    }

    /**
     * Converts the entity attribute value to database column value.
     * Automatically hashes the value if it's not already hashed.
     *
     * @param attribute The entity attribute value (plain text or already hashed)
     * @return The hashed value for database storage
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        log.debug("convertToDatabaseColumn called with input: {}",
                attribute == null ? "null" : "length=" + attribute.length());

        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        try {
            // Check if already hashed to prevent double-hashing
            if (isAlreadyHashed(attribute)) {
                log.debug("Value is already hashed (length: {}), storing as-is", attribute.length());
                return attribute;
            }

            // Hash the plain text value
            String hashed = HashingUtils.hash(attribute);
            log.debug("Hashed value for database: input='{}' (length={}), hash='{}' (length={})",
                    attribute, attribute.length(), hashed, hashed.length());
            return hashed;
        } catch (Exception e) {
            log.error("Hashing failed", e);
            throw new RuntimeException("Failed to hash value", e);
        }
    }

    /**
     * Converts the database column value to entity attribute value.
     * Since hashing is one-way, this returns the hashed value as-is.
     *
     * @param dbData The database column value (hashed)
     * @return The hashed value (cannot be reversed)
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        // Hashing is one-way, so we return the hashed value as-is
        // The entity will contain the hashed value, not the plain text
        return dbData;
    }

    /**
     * Checks if a value is already hashed.
     * Uses SHA-256 hash validation (44 chars base64).
     *
     * @param value The value to check
     * @return true if the value appears to be already hashed
     */
    private boolean isAlreadyHashed(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Use the hashing utility's validation
        return HashingUtils.isValidSha256Hash(value);
    }
}