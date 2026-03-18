package dev.simplecore.simplix.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UuidUtils")
class UuidUtilsTest {

    @Nested
    @DisplayName("generateUuidV7")
    class GenerateUuidV7 {

        @Test
        @DisplayName("should generate valid UUID string")
        void shouldGenerateValidUuidString() {
            String uuid = UuidUtils.generateUuidV7();

            assertThat(uuid).isNotNull();
            assertThat(uuid).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }

        @Test
        @DisplayName("should generate unique UUIDs")
        void shouldGenerateUniqueUuids() {
            Set<String> uuids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                uuids.add(UuidUtils.generateUuidV7());
            }

            assertThat(uuids).hasSize(100);
        }
    }

    @Nested
    @DisplayName("generateUuidV7Object")
    class GenerateUuidV7Object {

        @Test
        @DisplayName("should return UUID object")
        void shouldReturnUuidObject() {
            UUID uuid = UuidUtils.generateUuidV7Object();

            assertThat(uuid).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateShortUuid")
    class GenerateShortUuid {

        @Test
        @DisplayName("should return 8 character string")
        void shouldReturn8Characters() {
            String shortUuid = UuidUtils.generateShortUuid();

            assertThat(shortUuid).hasSize(8);
        }
    }

    @Nested
    @DisplayName("generateUuidV4")
    class GenerateUuidV4 {

        @Test
        @DisplayName("should generate valid UUID v4 string")
        void shouldGenerateValidV4String() {
            String uuid = UuidUtils.generateUuidV4();

            assertThat(uuid).isNotNull();
            // UUID v4 has version nibble '4' at position 14
            assertThat(uuid.charAt(14)).isEqualTo('4');
        }
    }

    @Nested
    @DisplayName("generateUuidV4Object")
    class GenerateUuidV4Object {

        @Test
        @DisplayName("should return UUID v4 object with correct version")
        void shouldReturnV4Object() {
            UUID uuid = UuidUtils.generateUuidV4Object();

            assertThat(uuid).isNotNull();
            assertThat(uuid.version()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("isUuidV7")
    class IsUuidV7 {

        @Test
        @DisplayName("should return false for UUID v4")
        void shouldReturnFalseForV4() {
            UUID v4 = UUID.randomUUID();

            assertThat(UuidUtils.isUuidV7(v4)).isFalse();
        }

        @Test
        @DisplayName("should return false for invalid UUID string")
        void shouldReturnFalseForInvalidString() {
            assertThat(UuidUtils.isUuidV7("not-a-uuid")).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmptyString() {
            assertThat(UuidUtils.isUuidV7("")).isFalse();
        }
    }

    @Nested
    @DisplayName("extractTimestamp")
    class ExtractTimestamp {

        @Test
        @DisplayName("should throw for non-v7 UUID")
        void shouldThrowForNonV7Uuid() {
            UUID v4 = UUID.randomUUID();

            assertThatThrownBy(() -> UuidUtils.extractTimestamp(v4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not Version 7");
        }
    }
}
