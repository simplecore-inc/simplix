package dev.simplecore.simplix.encryption.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JPA AttributeConverter that holds an encrypted secret as {@code byte[]} in Java.
 *
 * <p>For a value the application must be able to erase. A {@code String} cannot be overwritten and stays in the
 * heap until a collector moves it; an attribute of bytes can be cleared with {@link #erase(byte[])} the moment the
 * work is done.
 *
 * <p>The column holds the same AES ciphertext text that {@link AesEncryptionConverter} writes for the same UTF-8
 * plaintext, so a {@code String} attribute can switch to this converter without a schema or data change.
 *
 * <p>The decrypted text exists briefly as a {@code String} inside {@link #convertToEntityAttribute(String)};
 * JPA offers no way to avoid that. What this removes is the copy that would otherwise live on the entity for as
 * long as the persistence context does.
 *
 * <pre>
 * {@code
 * @Convert(converter = AesEncryptionBytesConverter.class)
 * @Column(name = "signing_key", length = 1000)
 * private byte[] signingKey;
 * }
 * </pre>
 */
@Converter(autoApply = false)
public class AesEncryptionBytesConverter implements AttributeConverter<byte[], String> {

    private final AesEncryptionConverter text = new AesEncryptionConverter();

    @Override
    public String convertToDatabaseColumn(byte[] attribute) {
        if (attribute == null) {
            return null;
        }
        return text.convertToDatabaseColumn(new String(attribute, StandardCharsets.UTF_8));
    }

    @Override
    public byte[] convertToEntityAttribute(String column) {
        if (column == null) {
            return null;
        }
        String plain = text.convertToEntityAttribute(column);
        return plain == null ? null : plain.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Overwrites a secret this converter produced.
     *
     * @param secret the bytes to erase; null and empty are no-ops
     */
    public static void erase(byte[] secret) {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
        }
    }
}
