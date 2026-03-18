package dev.simplecore.simplix.encryption.persistence.converter;

import dev.simplecore.simplix.encryption.service.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AesEncryptionConverter Tests")
class AesEncryptionConverterTest {

    @Mock
    private EncryptionService encryptionService;

    private AesEncryptionConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AesEncryptionConverter();
        converter.setEncryptionService(encryptionService);
    }

    @AfterEach
    void tearDown() {
        // Clean up static field to avoid test pollution
        ReflectionTestUtils.setField(AesEncryptionConverter.class, "encryptionService", null);
    }

    @Nested
    @DisplayName("convertToDatabaseColumn()")
    class ConvertToDatabaseColumnTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = converter.convertToDatabaseColumn(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void shouldReturnEmptyForEmptyInput() {
            String result = converter.convertToDatabaseColumn("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should encrypt plain text and return encrypted data")
        void shouldEncryptPlainText() {
            when(encryptionService.isEncrypted("plain text")).thenReturn(false);
            EncryptionService.EncryptedData encryptedData =
                new EncryptionService.EncryptedData("v1:iv:cipher", "v1");
            when(encryptionService.encrypt("plain text")).thenReturn(encryptedData);

            String result = converter.convertToDatabaseColumn("plain text");

            assertThat(result).isEqualTo("v1:iv:cipher");
            verify(encryptionService).encrypt("plain text");
        }

        @Test
        @DisplayName("Should skip encryption for already encrypted data")
        void shouldSkipEncryptionForAlreadyEncrypted() {
            String alreadyEncrypted = "v1:iv:cipher";
            when(encryptionService.isEncrypted(alreadyEncrypted)).thenReturn(true);

            String result = converter.convertToDatabaseColumn(alreadyEncrypted);

            assertThat(result).isEqualTo(alreadyEncrypted);
            verify(encryptionService, never()).encrypt(anyString());
        }

        @Test
        @DisplayName("Should throw when encryption service is not available")
        void shouldThrowWhenEncryptionServiceNotAvailable() {
            ReflectionTestUtils.setField(AesEncryptionConverter.class, "encryptionService", null);

            assertThatThrownBy(() -> converter.convertToDatabaseColumn("plain text"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Encryption service not initialized");
        }

        @Test
        @DisplayName("Should throw when encryption returns null")
        void shouldThrowWhenEncryptionReturnsNull() {
            when(encryptionService.isEncrypted("text")).thenReturn(false);
            when(encryptionService.encrypt("text")).thenReturn(null);

            assertThatThrownBy(() -> converter.convertToDatabaseColumn("text"))
                .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should throw when encryption returns EncryptedData with null data")
        void shouldThrowWhenEncryptedDataIsNull() {
            when(encryptionService.isEncrypted("text")).thenReturn(false);
            EncryptionService.EncryptedData nullData =
                new EncryptionService.EncryptedData(null, "v1");
            when(encryptionService.encrypt("text")).thenReturn(nullData);

            // IllegalStateException is caught by the outer try-catch and wrapped in RuntimeException
            assertThatThrownBy(() -> converter.convertToDatabaseColumn("text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to encrypt sensitive data")
                .hasCauseInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should attempt encryption when isEncrypted check throws exception")
        void shouldAttemptEncryptionWhenIsEncryptedCheckFails() {
            when(encryptionService.isEncrypted("text"))
                .thenThrow(new RuntimeException("Check failed"));
            EncryptionService.EncryptedData encryptedData =
                new EncryptionService.EncryptedData("v1:iv:cipher", "v1");
            when(encryptionService.encrypt("text")).thenReturn(encryptedData);

            String result = converter.convertToDatabaseColumn("text");

            assertThat(result).isEqualTo("v1:iv:cipher");
        }

        @Test
        @DisplayName("Should wrap encryption failure in RuntimeException")
        void shouldWrapEncryptionFailure() {
            when(encryptionService.isEncrypted("text")).thenReturn(false);
            when(encryptionService.encrypt("text"))
                .thenThrow(new RuntimeException("Encryption failed"));

            assertThatThrownBy(() -> converter.convertToDatabaseColumn("text"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to encrypt sensitive data");
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute()")
    class ConvertToEntityAttributeTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNullInput() {
            String result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void shouldReturnEmptyForEmptyInput() {
            String result = converter.convertToEntityAttribute("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should decrypt encrypted data")
        void shouldDecryptEncryptedData() {
            String encrypted = "v1:iv:cipher";
            when(encryptionService.isEncrypted(encrypted)).thenReturn(true);
            when(encryptionService.decrypt(encrypted)).thenReturn("plain text");

            String result = converter.convertToEntityAttribute(encrypted);

            assertThat(result).isEqualTo("plain text");
            verify(encryptionService).decrypt(encrypted);
        }

        @Test
        @DisplayName("Should return data as-is when not encrypted (legacy data)")
        void shouldReturnAsIsWhenNotEncrypted() {
            String plainData = "legacy plain text";
            when(encryptionService.isEncrypted(plainData)).thenReturn(false);

            String result = converter.convertToEntityAttribute(plainData);

            assertThat(result).isEqualTo(plainData);
            verify(encryptionService, never()).decrypt(anyString());
        }

        @Test
        @DisplayName("Should throw when encryption service is not available")
        void shouldThrowWhenEncryptionServiceNotAvailable() {
            ReflectionTestUtils.setField(AesEncryptionConverter.class, "encryptionService", null);

            assertThatThrownBy(() -> converter.convertToEntityAttribute("v1:iv:cipher"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Encryption service not initialized");
        }

        @Test
        @DisplayName("Should attempt decryption when isEncrypted check throws exception")
        void shouldAttemptDecryptionWhenIsEncryptedCheckFails() {
            when(encryptionService.isEncrypted("encrypted"))
                .thenThrow(new RuntimeException("Check failed"));
            when(encryptionService.decrypt("encrypted")).thenReturn("decrypted");

            String result = converter.convertToEntityAttribute("encrypted");

            assertThat(result).isEqualTo("decrypted");
        }

        @Test
        @DisplayName("Should wrap decryption failure in RuntimeException")
        void shouldWrapDecryptionFailure() {
            when(encryptionService.isEncrypted("encrypted")).thenReturn(true);
            when(encryptionService.decrypt("encrypted"))
                .thenThrow(new RuntimeException("Decryption failed"));

            assertThatThrownBy(() -> converter.convertToEntityAttribute("encrypted"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt sensitive data");
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with default constructor for JPA")
        void shouldCreateWithDefaultConstructor() {
            AesEncryptionConverter defaultConverter = new AesEncryptionConverter();
            assertThat(defaultConverter).isNotNull();
        }
    }

    @Nested
    @DisplayName("setEncryptionService()")
    class SetEncryptionServiceTests {

        @Test
        @DisplayName("Should set the static encryption service field")
        void shouldSetStaticField() {
            // Reset
            ReflectionTestUtils.setField(AesEncryptionConverter.class, "encryptionService", null);

            AesEncryptionConverter newConverter = new AesEncryptionConverter();
            newConverter.setEncryptionService(encryptionService);

            // The static field should be set
            EncryptionService storedService = (EncryptionService)
                ReflectionTestUtils.getField(AesEncryptionConverter.class, "encryptionService");
            assertThat(storedService).isEqualTo(encryptionService);
        }
    }
}
