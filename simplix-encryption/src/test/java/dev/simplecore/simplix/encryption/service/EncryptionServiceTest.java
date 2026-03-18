package dev.simplecore.simplix.encryption.service;

import dev.simplecore.simplix.encryption.provider.KeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EncryptionService Tests")
class EncryptionServiceTest {

    @Mock
    private KeyProvider keyProvider;

    private EncryptionService encryptionService;

    private SecretKey testKey;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        encryptionService = new EncryptionService(keyProvider);
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        testKey = keyGen.generateKey();
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize with a non-null KeyProvider")
        void shouldInitializeWithKeyProvider() {
            EncryptionService service = new EncryptionService(keyProvider);

            assertThat(service.getKeyProvider()).isEqualTo(keyProvider);
        }

        @Test
        @DisplayName("Should initialize with null KeyProvider gracefully")
        void shouldInitializeWithNullKeyProvider() {
            EncryptionService service = new EncryptionService(null);

            assertThat(service.getKeyProvider()).isNull();
        }
    }

    @Nested
    @DisplayName("encrypt()")
    class EncryptTests {

        @Test
        @DisplayName("Should return null when plainText is null")
        void shouldReturnNullForNullInput() {
            EncryptionService.EncryptedData result = encryptionService.encrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when plainText is empty")
        void shouldReturnNullForEmptyInput() {
            EncryptionService.EncryptedData result = encryptionService.encrypt("");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should encrypt plainText successfully")
        void shouldEncryptPlainTextSuccessfully() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");

            EncryptionService.EncryptedData result = encryptionService.encrypt("Hello World");

            assertThat(result).isNotNull();
            assertThat(result.getData()).isNotNull();
            assertThat(result.getKeyVersion()).isEqualTo("v1");

            // Format should be version:iv:ciphertext
            String[] parts = result.getData().split(":");
            assertThat(parts).hasSize(3);
            assertThat(parts[0]).isEqualTo("v1");
        }

        @Test
        @DisplayName("Should produce different ciphertexts for same plainText (due to random IV)")
        void shouldProduceDifferentCiphertextsForSamePlainText() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");

            EncryptionService.EncryptedData result1 = encryptionService.encrypt("Hello World");
            EncryptionService.EncryptedData result2 = encryptionService.encrypt("Hello World");

            assertThat(result1.getData()).isNotEqualTo(result2.getData());
        }

        @Test
        @DisplayName("Should throw EncryptionException when key provider fails")
        void shouldThrowEncryptionExceptionWhenKeyProviderFails() {
            when(keyProvider.getCurrentKey()).thenThrow(new RuntimeException("Key error"));

            assertThatThrownBy(() -> encryptionService.encrypt("Hello"))
                .isInstanceOf(EncryptionService.EncryptionException.class)
                .hasMessageContaining("Failed to encrypt data");
        }
    }

    @Nested
    @DisplayName("decrypt()")
    class DecryptTests {

        @Test
        @DisplayName("Should return null when encryptedData is null")
        void shouldReturnNullForNullInput() {
            String result = encryptionService.decrypt(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when encryptedData is empty")
        void shouldReturnNullForEmptyInput() {
            String result = encryptionService.decrypt("");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should decrypt encrypted data successfully")
        void shouldDecryptEncryptedDataSuccessfully() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenReturn(testKey);

            EncryptionService.EncryptedData encrypted = encryptionService.encrypt("Hello World");
            String decrypted = encryptionService.decrypt(encrypted.getData());

            assertThat(decrypted).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("Should return data as-is when it does not contain delimiter")
        void shouldReturnDataAsIsWhenNoDelimiter() {
            String plainData = "not-encrypted-data-without-colons";

            // Data with no colons at all will be split into 1 part, and since it doesn't contain ":",
            // it returns as-is
            String result = encryptionService.decrypt(plainData);

            assertThat(result).isEqualTo(plainData);
        }

        @Test
        @DisplayName("Should throw DecryptionException for invalid format with partial delimiters")
        void shouldThrowDecryptionExceptionForInvalidFormat() {
            // 2 parts only, but contains delimiter
            String invalidData = "v1:partialdata";

            assertThatThrownBy(() -> encryptionService.decrypt(invalidData))
                .isInstanceOf(EncryptionService.DecryptionException.class)
                .hasMessageContaining("Failed to decrypt data");
        }

        @Test
        @DisplayName("Should throw DecryptionException when key provider fails")
        void shouldThrowDecryptionExceptionWhenKeyProviderFails() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenThrow(new RuntimeException("Key not found"));

            EncryptionService.EncryptedData encrypted = encryptionService.encrypt("Hello");

            assertThatThrownBy(() -> encryptionService.decrypt(encrypted.getData()))
                .isInstanceOf(EncryptionService.DecryptionException.class)
                .hasMessageContaining("Failed to decrypt data");
        }

        @Test
        @DisplayName("Should decrypt data encrypted with different key versions")
        void shouldDecryptDataWithDifferentKeyVersions() throws NoSuchAlgorithmException {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey keyV1 = keyGen.generateKey();
            SecretKey keyV2 = keyGen.generateKey();

            // Encrypt with v1
            when(keyProvider.getCurrentKey()).thenReturn(keyV1);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            EncryptionService.EncryptedData encryptedV1 = encryptionService.encrypt("Data V1");

            // Encrypt with v2
            when(keyProvider.getCurrentKey()).thenReturn(keyV2);
            when(keyProvider.getCurrentVersion()).thenReturn("v2");
            EncryptionService.EncryptedData encryptedV2 = encryptionService.encrypt("Data V2");

            // Decrypt v1 data
            when(keyProvider.getKey("v1")).thenReturn(keyV1);
            assertThat(encryptionService.decrypt(encryptedV1.getData())).isEqualTo("Data V1");

            // Decrypt v2 data
            when(keyProvider.getKey("v2")).thenReturn(keyV2);
            assertThat(encryptionService.decrypt(encryptedV2.getData())).isEqualTo("Data V2");
        }
    }

    @Nested
    @DisplayName("encrypt/decrypt roundtrip")
    class RoundtripTests {

        @Test
        @DisplayName("Should encrypt and decrypt plain text correctly")
        void shouldEncryptAndDecryptCorrectly() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenReturn(testKey);

            String original = "Sensitive data to protect";
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(original);
            String decrypted = encryptionService.decrypt(encrypted.getData());

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Should handle Unicode text in roundtrip")
        void shouldHandleUnicodeTextInRoundtrip() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenReturn(testKey);

            String original = "Multi-language text: English, Korean, Japanese, Chinese";
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(original);
            String decrypted = encryptionService.decrypt(encrypted.getData());

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Should handle long text in roundtrip")
        void shouldHandleLongTextInRoundtrip() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenReturn(testKey);

            String original = "A".repeat(10000);
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(original);
            String decrypted = encryptionService.decrypt(encrypted.getData());

            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        @DisplayName("Should handle special characters in roundtrip")
        void shouldHandleSpecialCharactersInRoundtrip() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            when(keyProvider.getKey("v1")).thenReturn(testKey);

            String original = "Special chars: !@#$%^&*()_+-={}[]|\\:\";<>?,./~`";
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(original);
            String decrypted = encryptionService.decrypt(encrypted.getData());

            assertThat(decrypted).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("reencrypt()")
    class ReencryptTests {

        @Test
        @DisplayName("Should decrypt and re-encrypt with current key")
        void shouldReencryptWithCurrentKey() throws NoSuchAlgorithmException {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey oldKey = keyGen.generateKey();
            SecretKey newKey = keyGen.generateKey();

            // Encrypt with old key
            when(keyProvider.getCurrentKey()).thenReturn(oldKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");
            EncryptionService.EncryptedData originalEncrypted = encryptionService.encrypt("Secret");

            // Re-encrypt: decrypt uses old key, encrypt uses new key
            when(keyProvider.getKey("v1")).thenReturn(oldKey);
            when(keyProvider.getCurrentKey()).thenReturn(newKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v2");

            EncryptionService.EncryptedData reencrypted = encryptionService.reencrypt(originalEncrypted.getData());

            assertThat(reencrypted).isNotNull();
            assertThat(reencrypted.getKeyVersion()).isEqualTo("v2");

            // Verify the re-encrypted data can be decrypted with the new key
            when(keyProvider.getKey("v2")).thenReturn(newKey);
            String decrypted = encryptionService.decrypt(reencrypted.getData());
            assertThat(decrypted).isEqualTo("Secret");
        }
    }

    @Nested
    @DisplayName("isEncrypted()")
    class IsEncryptedTests {

        @Test
        @DisplayName("Should return false for null data")
        void shouldReturnFalseForNullData() {
            assertThat(encryptionService.isEncrypted(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty data")
        void shouldReturnFalseForEmptyData() {
            assertThat(encryptionService.isEncrypted("")).isFalse();
        }

        @Test
        @DisplayName("Should return false for plain text without delimiters")
        void shouldReturnFalseForPlainText() {
            assertThat(encryptionService.isEncrypted("plain text")).isFalse();
        }

        @Test
        @DisplayName("Should return false for data with wrong number of parts")
        void shouldReturnFalseForWrongPartCount() {
            assertThat(encryptionService.isEncrypted("v1:only-two-parts")).isFalse();
            assertThat(encryptionService.isEncrypted("v1:a:b:c")).isFalse();
        }

        @Test
        @DisplayName("Should return true for data with 'v' version prefix")
        void shouldReturnTrueForVVersionPrefix() {
            assertThat(encryptionService.isEncrypted("v1:iv-data:cipher-data")).isTrue();
        }

        @Test
        @DisplayName("Should return true for data with 'dev-static' version prefix")
        void shouldReturnTrueForDevStaticVersionPrefix() {
            assertThat(encryptionService.isEncrypted("dev-static:iv-data:cipher-data")).isTrue();
        }

        @Test
        @DisplayName("Should return true for data with 'static' version prefix")
        void shouldReturnTrueForStaticVersionPrefix() {
            assertThat(encryptionService.isEncrypted("static:iv-data:cipher-data")).isTrue();
        }

        @Test
        @DisplayName("Should return false for data with unknown version prefix")
        void shouldReturnFalseForUnknownVersionPrefix() {
            assertThat(encryptionService.isEncrypted("unknown:iv-data:cipher-data")).isFalse();
        }

        @Test
        @DisplayName("Should correctly identify actually encrypted data")
        void shouldIdentifyActuallyEncryptedData() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v1");

            EncryptionService.EncryptedData encrypted = encryptionService.encrypt("Test");

            assertThat(encryptionService.isEncrypted(encrypted.getData())).isTrue();
        }
    }

    @Nested
    @DisplayName("getKeyVersion()")
    class GetKeyVersionTests {

        @Test
        @DisplayName("Should return null for non-encrypted data")
        void shouldReturnNullForNonEncryptedData() {
            assertThat(encryptionService.getKeyVersion("plain text")).isNull();
        }

        @Test
        @DisplayName("Should return null for null data")
        void shouldReturnNullForNullData() {
            assertThat(encryptionService.getKeyVersion(null)).isNull();
        }

        @Test
        @DisplayName("Should return null for empty data")
        void shouldReturnNullForEmptyData() {
            assertThat(encryptionService.getKeyVersion("")).isNull();
        }

        @Test
        @DisplayName("Should return version from encrypted data")
        void shouldReturnVersionFromEncryptedData() {
            assertThat(encryptionService.getKeyVersion("v1:iv-data:cipher-data")).isEqualTo("v1");
        }

        @Test
        @DisplayName("Should return version from actually encrypted data")
        void shouldReturnVersionFromActuallyEncryptedData() {
            when(keyProvider.getCurrentKey()).thenReturn(testKey);
            when(keyProvider.getCurrentVersion()).thenReturn("v2");

            EncryptionService.EncryptedData encrypted = encryptionService.encrypt("Test");

            assertThat(encryptionService.getKeyVersion(encrypted.getData())).isEqualTo("v2");
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("Should delegate to key provider")
        void shouldDelegateToKeyProvider() {
            when(keyProvider.isConfigured()).thenReturn(true);

            assertThat(encryptionService.isConfigured()).isTrue();

            verify(keyProvider).isConfigured();
        }

        @Test
        @DisplayName("Should return false when key provider is not configured")
        void shouldReturnFalseWhenNotConfigured() {
            when(keyProvider.isConfigured()).thenReturn(false);

            assertThat(encryptionService.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("EncryptedData inner class")
    class EncryptedDataTests {

        @Test
        @DisplayName("Should store data and keyVersion correctly")
        void shouldStoreDataAndKeyVersionCorrectly() {
            EncryptionService.EncryptedData data = new EncryptionService.EncryptedData("encrypted", "v1");

            assertThat(data.getData()).isEqualTo("encrypted");
            assertThat(data.getKeyVersion()).isEqualTo("v1");
        }
    }

    @Nested
    @DisplayName("EncryptionException inner class")
    class EncryptionExceptionTests {

        @Test
        @DisplayName("Should store message and cause")
        void shouldStoreMessageAndCause() {
            RuntimeException cause = new RuntimeException("root cause");
            EncryptionService.EncryptionException ex =
                new EncryptionService.EncryptionException("Encryption failed", cause);

            assertThat(ex.getMessage()).isEqualTo("Encryption failed");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("DecryptionException inner class")
    class DecryptionExceptionTests {

        @Test
        @DisplayName("Should store message and cause")
        void shouldStoreMessageAndCause() {
            RuntimeException cause = new RuntimeException("root cause");
            EncryptionService.DecryptionException ex =
                new EncryptionService.DecryptionException("Decryption failed", cause);

            assertThat(ex.getMessage()).isEqualTo("Decryption failed");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }
}
