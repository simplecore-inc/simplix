package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXEnumSerializer")
class SimpliXEnumSerializerTest {

    private ObjectMapper objectMapper;

    enum TestStatus {
        ACTIVE,
        INACTIVE
    }

    enum StatusWithGetter {
        ACTIVE("Active Status"),
        INACTIVE("Inactive Status");

        private final String description;

        StatusWithGetter(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    static class EnumHolder {
        @JsonSerialize(using = SimpliXEnumSerializer.class)
        private TestStatus status;

        public EnumHolder() {}

        public EnumHolder(TestStatus status) {
            this.status = status;
        }

        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
    }

    static class EnumWithGetterHolder {
        @JsonSerialize(using = SimpliXEnumSerializer.class)
        private StatusWithGetter status;

        public EnumWithGetterHolder() {}

        public EnumWithGetterHolder(StatusWithGetter status) {
            this.status = status;
        }

        public StatusWithGetter getStatus() { return status; }
        public void setStatus(StatusWithGetter status) { this.status = status; }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("serialize")
    class Serialize {

        @Test
        @DisplayName("should serialize enum to object with type and value")
        void shouldSerializeEnumToObject() throws JsonProcessingException {
            EnumHolder holder = new EnumHolder(TestStatus.ACTIVE);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"type\"");
            assertThat(json).contains("\"value\":\"ACTIVE\"");
        }

        @Test
        @DisplayName("should serialize null enum to null")
        void shouldSerializeNullEnum() throws JsonProcessingException {
            EnumHolder holder = new EnumHolder(null);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"status\":null");
        }

        @Test
        @DisplayName("should serialize INACTIVE enum value")
        void shouldSerializeInactiveEnum() throws JsonProcessingException {
            EnumHolder holder = new EnumHolder(TestStatus.INACTIVE);

            String json = objectMapper.writeValueAsString(holder);

            assertThat(json).contains("\"value\":\"INACTIVE\"");
        }
    }
}
