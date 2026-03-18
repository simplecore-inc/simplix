package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpliXBooleanDeserializer")
class SimpliXBooleanDeserializerTest {

    private ObjectMapper objectMapper;

    static class BooleanHolder {
        @JsonDeserialize(using = SimpliXBooleanDeserializer.class)
        private Boolean active;

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("deserialize")
    class Deserialize {

        @Test
        @DisplayName("should deserialize 'true' string to true")
        void shouldDeserializeTrueString() throws JsonProcessingException {
            BooleanHolder result = objectMapper.readValue("{\"active\":\"true\"}", BooleanHolder.class);

            assertThat(result.getActive()).isTrue();
        }

        @Test
        @DisplayName("should deserialize 'false' string to false")
        void shouldDeserializeFalseString() throws JsonProcessingException {
            BooleanHolder result = objectMapper.readValue("{\"active\":\"false\"}", BooleanHolder.class);

            assertThat(result.getActive()).isFalse();
        }

        @Test
        @DisplayName("should deserialize 'yes' string to true")
        void shouldDeserializeYesString() throws JsonProcessingException {
            BooleanHolder result = objectMapper.readValue("{\"active\":\"yes\"}", BooleanHolder.class);

            assertThat(result.getActive()).isTrue();
        }

        @Test
        @DisplayName("should deserialize '1' string to true")
        void shouldDeserializeOneString() throws JsonProcessingException {
            BooleanHolder result = objectMapper.readValue("{\"active\":\"1\"}", BooleanHolder.class);

            assertThat(result.getActive()).isTrue();
        }

        @Test
        @DisplayName("should deserialize '0' string to false")
        void shouldDeserializeZeroString() throws JsonProcessingException {
            BooleanHolder result = objectMapper.readValue("{\"active\":\"0\"}", BooleanHolder.class);

            assertThat(result.getActive()).isFalse();
        }

        @Test
        @DisplayName("should throw for invalid boolean string")
        void shouldThrowForInvalidBoolean() {
            assertThatThrownBy(() ->
                objectMapper.readValue("{\"active\":\"maybe\"}", BooleanHolder.class)
            ).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        }
    }
}
