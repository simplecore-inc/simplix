package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXBooleanSerializer")
class SimpliXBooleanSerializerTest {

    private ObjectMapper objectMapper;

    static class BooleanHolder {
        @JsonSerialize(using = SimpliXBooleanSerializer.class)
        private Boolean active;

        public BooleanHolder() {}

        public BooleanHolder(Boolean active) {
            this.active = active;
        }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("serialize")
    class Serialize {

        @Test
        @DisplayName("should serialize true to JSON boolean true")
        void shouldSerializeTrue() throws JsonProcessingException {
            BooleanHolder holder = new BooleanHolder(true);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"active\":true");
        }

        @Test
        @DisplayName("should serialize false to JSON boolean false")
        void shouldSerializeFalse() throws JsonProcessingException {
            BooleanHolder holder = new BooleanHolder(false);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"active\":false");
        }

        @Test
        @DisplayName("should serialize null to JSON null")
        void shouldSerializeNull() throws JsonProcessingException {
            BooleanHolder holder = new BooleanHolder(null);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"active\":null");
        }
    }
}
