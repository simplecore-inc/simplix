package dev.simplecore.simplix.encryption.persistence.converter;

import dev.simplecore.simplix.encryption.service.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AesEncryptionBytesConverter")
class AesEncryptionBytesConverterTest {

    @Mock
    private EncryptionService encryptionService;

    private final AesEncryptionConverter textConverter = new AesEncryptionConverter();

    private final AesEncryptionBytesConverter converter = new AesEncryptionBytesConverter();

    @BeforeEach
    void setUp() {
        textConverter.setEncryptionService(encryptionService);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(AesEncryptionConverter.class, "encryptionService", null);
    }

    @Test
    @DisplayName("writes the same column the text converter writes for the same plaintext")
    void writesTheTextConvertersColumn() {
        when(encryptionService.isEncrypted("secret-key")).thenReturn(false);
        when(encryptionService.encrypt("secret-key"))
                .thenReturn(new EncryptionService.EncryptedData("v1:cipher", "v1"));

        String column = converter.convertToDatabaseColumn("secret-key".getBytes(StandardCharsets.UTF_8));

        assertThat(column).isEqualTo(textConverter.convertToDatabaseColumn("secret-key"));
    }

    @Test
    @DisplayName("reads a column back as the plaintext bytes")
    void readsPlaintextBytes() {
        when(encryptionService.isEncrypted("v1:cipher")).thenReturn(true);
        when(encryptionService.decrypt("v1:cipher")).thenReturn("secret-key");

        assertThat(converter.convertToEntityAttribute("v1:cipher"))
                .isEqualTo("secret-key".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("passes null through both ways")
    void nullPassesThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("erase overwrites every byte")
    void eraseOverwrites() {
        byte[] secret = "secret-key".getBytes(StandardCharsets.UTF_8);

        AesEncryptionBytesConverter.erase(secret);
        AesEncryptionBytesConverter.erase(null);

        assertThat(secret).containsOnly((byte) 0);
    }
}
