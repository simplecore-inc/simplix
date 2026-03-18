package dev.simplecore.simplix.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UuidUtils - Extended Coverage")
class UuidUtilsExtendedTest {

    @Nested
    @DisplayName("isUuidV7 with string")
    class IsUuidV7String {

        @Test
        @DisplayName("should return false for invalid UUID string")
        void shouldReturnFalseForInvalid() {
            assertThat(UuidUtils.isUuidV7("not-a-uuid")).isFalse();
        }

        @Test
        @DisplayName("should return false for random UUID v4")
        void shouldReturnFalseForV4() {
            UUID v4 = UUID.randomUUID();
            assertThat(UuidUtils.isUuidV7(v4.toString())).isFalse();
        }
    }

    @Nested
    @DisplayName("extractTimestamp with string")
    class ExtractTimestampString {

        @Test
        @DisplayName("should extract timestamp from string UUID v7")
        void shouldExtractFromString() {
            UUID v7 = UuidUtils.generateUuidV7Object();
            if (UuidUtils.isUuidV7(v7)) {
                long ts = UuidUtils.extractTimestamp(v7.toString());
                assertThat(ts).isPositive();
            }
        }
    }

    @Nested
    @DisplayName("extractTimestamp - non v7")
    class ExtractTimestampNonV7 {

        @Test
        @DisplayName("should throw for non-v7 UUID")
        void shouldThrowForNonV7() {
            UUID v4 = UUID.randomUUID();
            assertThatThrownBy(() -> UuidUtils.extractTimestamp(v4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not Version 7");
        }
    }

    @Nested
    @DisplayName("generateUuidV4")
    class GenerateV4 {

        @Test
        @DisplayName("should generate valid v4 UUID string")
        void shouldGenerateV4String() {
            String uuid = UuidUtils.generateUuidV4();
            assertThat(uuid).isNotNull();
            assertThat(UUID.fromString(uuid)).isNotNull();
        }

        @Test
        @DisplayName("should generate valid v4 UUID object")
        void shouldGenerateV4Object() {
            UUID uuid = UuidUtils.generateUuidV4Object();
            assertThat(uuid).isNotNull();
            assertThat(uuid.version()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("generateShortUuid")
    class GenerateShortUuid {

        @Test
        @DisplayName("should generate 8-character short UUID")
        void shouldGenerate8Chars() {
            String shortUuid = UuidUtils.generateShortUuid();
            assertThat(shortUuid).hasSize(8);
        }
    }
}
