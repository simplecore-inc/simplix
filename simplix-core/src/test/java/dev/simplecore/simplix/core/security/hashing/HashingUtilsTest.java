package dev.simplecore.simplix.core.security.hashing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HashingUtils")
class HashingUtilsTest {

    @Nested
    @DisplayName("hash with default algorithm")
    class HashDefault {

        @Test
        @DisplayName("should return SHA-256 hash for valid input")
        void shouldReturnSha256Hash() {
            String result = HashingUtils.hash("test");

            assertThat(result).isNotNull();
            assertThat(HashingUtils.isValidSha256Hash(result)).isTrue();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HashingUtils.hash(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty trimmed input")
        void shouldReturnNullForEmptyTrimmedInput() {
            assertThat(HashingUtils.hash("   ")).isNull();
        }

        @Test
        @DisplayName("should produce consistent hash for same input")
        void shouldProduceConsistentHash() {
            String hash1 = HashingUtils.hash("hello");
            String hash2 = HashingUtils.hash("hello");

            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("should produce different hashes for different inputs")
        void shouldProduceDifferentHashes() {
            String hash1 = HashingUtils.hash("hello");
            String hash2 = HashingUtils.hash("world");

            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should trim input before hashing")
        void shouldTrimInput() {
            String hash1 = HashingUtils.hash("test");
            String hash2 = HashingUtils.hash("  test  ");

            assertThat(hash1).isEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("hash with specified algorithm")
    class HashWithAlgorithm {

        @Test
        @DisplayName("should hash with SHA-512")
        void shouldHashWithSha512() {
            String result = HashingUtils.hash("test", HashingUtils.SHA_512);

            assertThat(result).isNotNull();
            assertThat(HashingUtils.isValidSha512Hash(result)).isTrue();
        }

        @Test
        @DisplayName("should throw HashingException for invalid algorithm")
        void shouldThrowForInvalidAlgorithm() {
            assertThatThrownBy(() -> HashingUtils.hash("test", "INVALID-ALGO"))
                .isInstanceOf(HashingUtils.HashingException.class);
        }
    }

    @Nested
    @DisplayName("isValidSha256Hash")
    class IsValidSha256Hash {

        @Test
        @DisplayName("should return true for valid SHA-256 hash")
        void shouldReturnTrueForValidHash() {
            String hash = HashingUtils.hash("test");

            assertThat(HashingUtils.isValidSha256Hash(hash)).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(HashingUtils.isValidSha256Hash(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmpty() {
            assertThat(HashingUtils.isValidSha256Hash("")).isFalse();
        }

        @Test
        @DisplayName("should return false for plain text")
        void shouldReturnFalseForPlainText() {
            assertThat(HashingUtils.isValidSha256Hash("hello world")).isFalse();
        }

        @Test
        @DisplayName("should return false for wrong length")
        void shouldReturnFalseForWrongLength() {
            assertThat(HashingUtils.isValidSha256Hash("abc123")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidSha512Hash")
    class IsValidSha512Hash {

        @Test
        @DisplayName("should return true for valid SHA-512 hash")
        void shouldReturnTrueForValidHash() {
            String hash = HashingUtils.hash("test", HashingUtils.SHA_512);

            assertThat(HashingUtils.isValidSha512Hash(hash)).isTrue();
        }

        @Test
        @DisplayName("should return false for SHA-256 hash")
        void shouldReturnFalseForSha256() {
            String hash = HashingUtils.hash("test", HashingUtils.SHA_256);

            assertThat(HashingUtils.isValidSha512Hash(hash)).isFalse();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(HashingUtils.isValidSha512Hash(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidHash")
    class IsValidHash {

        @Test
        @DisplayName("should return true for valid base64 hash with reasonable length")
        void shouldReturnTrueForValidBase64Hash() {
            String hash = HashingUtils.hash("test");

            assertThat(HashingUtils.isValidHash(hash)).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(HashingUtils.isValidHash(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for too short string")
        void shouldReturnFalseForTooShort() {
            assertThat(HashingUtils.isValidHash("abc")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidHashForAlgorithm")
    class IsValidHashForAlgorithm {

        @Test
        @DisplayName("should validate SHA-256 hash")
        void shouldValidateSha256() {
            String hash = HashingUtils.hash("test", "SHA-256");

            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "SHA-256")).isTrue();
            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "SHA256")).isTrue();
        }

        @Test
        @DisplayName("should validate SHA-512 hash")
        void shouldValidateSha512() {
            String hash = HashingUtils.hash("test", "SHA-512");

            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "SHA-512")).isTrue();
            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "SHA512")).isTrue();
        }

        @Test
        @DisplayName("should use generic validation for unknown algorithm")
        void shouldUseGenericValidation() {
            String hash = HashingUtils.hash("test");

            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "UNKNOWN")).isTrue();
        }

        @Test
        @DisplayName("should return false for null arguments")
        void shouldReturnFalseForNullArgs() {
            assertThat(HashingUtils.isValidHashForAlgorithm(null, "SHA-256")).isFalse();
            assertThat(HashingUtils.isValidHashForAlgorithm("hash", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("utility class")
    class UtilityClass {

        @Test
        @DisplayName("should have public algorithm constants")
        void shouldHaveAlgorithmConstants() {
            assertThat(HashingUtils.SHA_256).isEqualTo("SHA-256");
            assertThat(HashingUtils.SHA_512).isEqualTo("SHA-512");
        }
    }
}
