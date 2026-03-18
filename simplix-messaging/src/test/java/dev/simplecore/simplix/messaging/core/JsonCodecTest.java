package dev.simplecore.simplix.messaging.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonCodec")
class JsonCodecTest {

    @Nested
    @DisplayName("static serialize/deserialize")
    class StaticMethodTests {

        @Test
        @DisplayName("should serialize and deserialize a simple POJO")
        void shouldRoundTripSimplePojo() {
            SampleDto original = new SampleDto("Alice", 30, true);

            byte[] bytes = JsonCodec.serialize(original);
            SampleDto restored = JsonCodec.deserialize(bytes, SampleDto.class);

            assertThat(restored.name).isEqualTo("Alice");
            assertThat(restored.age).isEqualTo(30);
            assertThat(restored.active).isTrue();
        }

        @Test
        @DisplayName("should handle null fields gracefully")
        void shouldHandleNullFields() {
            SampleDto original = new SampleDto(null, 0, false);

            byte[] bytes = JsonCodec.serialize(original);
            SampleDto restored = JsonCodec.deserialize(bytes, SampleDto.class);

            assertThat(restored.name).isNull();
            assertThat(restored.age).isZero();
            assertThat(restored.active).isFalse();
        }

        @Test
        @DisplayName("should ignore unknown properties during deserialization")
        void shouldIgnoreUnknownProperties() {
            String json = "{\"name\":\"Bob\",\"age\":25,\"active\":true,\"unknownField\":\"extra\"}";
            byte[] bytes = json.getBytes();

            SampleDto restored = JsonCodec.deserialize(bytes, SampleDto.class);

            assertThat(restored.name).isEqualTo("Bob");
            assertThat(restored.age).isEqualTo(25);
        }

        @Test
        @DisplayName("should serialize and deserialize Java time types")
        void shouldHandleJavaTimeTypes() {
            TimeDto original = new TimeDto(
                    Instant.parse("2025-06-15T10:30:00Z"),
                    LocalDate.of(2025, 6, 15),
                    LocalDateTime.of(2025, 6, 15, 10, 30, 0)
            );

            byte[] bytes = JsonCodec.serialize(original);
            TimeDto restored = JsonCodec.deserialize(bytes, TimeDto.class);

            assertThat(restored.instant).isEqualTo(Instant.parse("2025-06-15T10:30:00Z"));
            assertThat(restored.localDate).isEqualTo(LocalDate.of(2025, 6, 15));
            assertThat(restored.localDateTime).isEqualTo(LocalDateTime.of(2025, 6, 15, 10, 30, 0));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should serialize and deserialize collections")
        void shouldHandleCollections() {
            List<String> original = List.of("alpha", "beta", "gamma");

            byte[] bytes = JsonCodec.serialize(original);
            List<String> restored = JsonCodec.deserialize(bytes, List.class);

            assertThat(restored).containsExactly("alpha", "beta", "gamma");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should serialize and deserialize maps")
        void shouldHandleMaps() {
            Map<String, Object> original = Map.of("key1", "value1", "key2", 42);

            byte[] bytes = JsonCodec.serialize(original);
            Map<String, Object> restored = JsonCodec.deserialize(bytes, Map.class);

            assertThat(restored).containsEntry("key1", "value1");
            assertThat(restored).containsEntry("key2", 42);
        }

        @Test
        @DisplayName("should throw JsonCodecException on static serialize failure")
        void shouldThrowOnStaticSerializeFailure() {
            Object problematic = new Object() {
                @SuppressWarnings("unused")
                public Object getSelf() { return this; }
            };
            assertThatThrownBy(() -> JsonCodec.serialize(problematic))
                    .isInstanceOf(JsonCodec.JsonCodecException.class)
                    .hasMessageContaining("Failed to serialize");
        }

        @Test
        @DisplayName("should throw JsonCodecException on invalid JSON for deserialization")
        void shouldThrowOnInvalidJson() {
            byte[] invalidJson = "not-valid-json{".getBytes();

            assertThatThrownBy(() -> JsonCodec.deserialize(invalidJson, SampleDto.class))
                    .isInstanceOf(JsonCodec.JsonCodecException.class)
                    .hasMessageContaining("Failed to deserialize");
        }
    }

    @Nested
    @DisplayName("instance encode/decode")
    class InstanceMethodTests {

        @Test
        @DisplayName("should encode and decode using default codec instance")
        void shouldRoundTripWithDefaultInstance() {
            JsonCodec codec = new JsonCodec();
            SampleDto original = new SampleDto("Charlie", 40, false);

            byte[] bytes = codec.encode(original);
            SampleDto restored = codec.decode(bytes, SampleDto.class);

            assertThat(restored.name).isEqualTo("Charlie");
            assertThat(restored.age).isEqualTo(40);
        }

        @Test
        @DisplayName("should use custom ObjectMapper")
        void shouldUseCustomObjectMapper() {
            ObjectMapper customMapper = new ObjectMapper();
            JsonCodec codec = new JsonCodec(customMapper);

            assertThat(codec.getObjectMapper()).isSameAs(customMapper);
        }

        @Test
        @DisplayName("should throw JsonCodecException on decode failure")
        void shouldThrowOnDecodeFailure() {
            JsonCodec codec = new JsonCodec();
            byte[] invalidJson = "not-json{".getBytes();

            assertThatThrownBy(() -> codec.decode(invalidJson, SampleDto.class))
                    .isInstanceOf(JsonCodec.JsonCodecException.class)
                    .hasMessageContaining("Failed to deserialize");
        }

        @Test
        @DisplayName("should throw JsonCodecException on encode failure")
        void shouldThrowOnEncodeFailure() {
            JsonCodec codec = new JsonCodec();

            // An object that causes serialization failure
            Object problematic = new Object() {
                @SuppressWarnings("unused")
                public Object getSelf() { return this; }
            };

            assertThatThrownBy(() -> codec.encode(problematic))
                    .isInstanceOf(JsonCodec.JsonCodecException.class)
                    .hasMessageContaining("Failed to serialize");
        }
    }

    @Nested
    @DisplayName("defaultMapper()")
    class DefaultMapperTests {

        @Test
        @DisplayName("should return a non-null shared ObjectMapper")
        void shouldReturnSharedMapper() {
            ObjectMapper mapper = JsonCodec.defaultMapper();

            assertThat(mapper).isNotNull();
        }

        @Test
        @DisplayName("should return the same instance on multiple calls")
        void shouldReturnSameInstance() {
            ObjectMapper first = JsonCodec.defaultMapper();
            ObjectMapper second = JsonCodec.defaultMapper();

            assertThat(first).isSameAs(second);
        }
    }

    // ---------------------------------------------------------------
    // Test DTOs
    // ---------------------------------------------------------------

    static class SampleDto {
        public String name;
        public int age;
        public boolean active;

        public SampleDto() {
        }

        public SampleDto(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
    }

    static class TimeDto {
        public Instant instant;
        public LocalDate localDate;
        public LocalDateTime localDateTime;

        public TimeDto() {
        }

        public TimeDto(Instant instant, LocalDate localDate, LocalDateTime localDateTime) {
            this.instant = instant;
            this.localDate = localDate;
            this.localDateTime = localDateTime;
        }
    }
}
