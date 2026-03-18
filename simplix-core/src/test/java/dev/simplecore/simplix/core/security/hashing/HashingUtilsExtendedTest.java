package dev.simplecore.simplix.core.security.hashing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HashingUtils - Extended Coverage")
class HashingUtilsExtendedTest {

    @Nested
    @DisplayName("isValidSha256Hash")
    class IsValidSha256Hash {

        @Test
        @DisplayName("should return true for valid base64 SHA-256 hash")
        void shouldReturnTrueForValid() {
            String hash = HashingUtils.hash("test");
            assertThat(HashingUtils.isValidSha256Hash(hash)).isTrue();
        }

        @Test
        @DisplayName("should return false for null/empty")
        void shouldReturnFalseForNullEmpty() {
            assertThat(HashingUtils.isValidSha256Hash(null)).isFalse();
            assertThat(HashingUtils.isValidSha256Hash("")).isFalse();
        }

        @Test
        @DisplayName("should return false for wrong length")
        void shouldReturnFalseForWrongLength() {
            assertThat(HashingUtils.isValidSha256Hash("short")).isFalse();
        }
    }

    @Nested
    @DisplayName("hash consistency")
    class HashConsistency {

        @Test
        @DisplayName("should produce consistent hash for same input")
        void shouldProduceConsistentHash() {
            String hash1 = HashingUtils.hash("test-value");
            String hash2 = HashingUtils.hash("test-value");
            assertThat(hash1).isEqualTo(hash2);
        }

        @Test
        @DisplayName("should produce different hash for different input")
        void shouldProduceDifferentHash() {
            String hash1 = HashingUtils.hash("value-a");
            String hash2 = HashingUtils.hash("value-b");
            assertThat(hash1).isNotEqualTo(hash2);
        }

        @Test
        @DisplayName("should support SHA-512")
        void shouldSupportSha512() {
            String hash = HashingUtils.hash("test", HashingUtils.SHA_512);
            assertThat(hash).isNotNull();
            assertThat(HashingUtils.isValidSha512Hash(hash)).isTrue();
        }

        @Test
        @DisplayName("should validate hash for algorithm")
        void shouldValidateForAlgorithm() {
            String sha256 = HashingUtils.hash("test", HashingUtils.SHA_256);
            assertThat(HashingUtils.isValidHashForAlgorithm(sha256, "SHA-256")).isTrue();
            assertThat(HashingUtils.isValidHashForAlgorithm(sha256, "SHA256")).isTrue();
            assertThat(HashingUtils.isValidHashForAlgorithm(null, "SHA-256")).isFalse();
            assertThat(HashingUtils.isValidHashForAlgorithm(sha256, null)).isFalse();
        }

        @Test
        @DisplayName("should validate general hash")
        void shouldValidateGeneralHash() {
            String hash = HashingUtils.hash("test");
            assertThat(HashingUtils.isValidHash(hash)).isTrue();
            assertThat(HashingUtils.isValidHash(null)).isFalse();
            assertThat(HashingUtils.isValidHash("")).isFalse();
            assertThat(HashingUtils.isValidHash("short")).isFalse();
        }

        @Test
        @DisplayName("should validate hash for unknown algorithm")
        void shouldValidateForUnknownAlgorithm() {
            String hash = HashingUtils.hash("test");
            assertThat(HashingUtils.isValidHashForAlgorithm(hash, "MD5")).isTrue();
        }
    }
}
