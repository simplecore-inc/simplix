package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SimpliXDateTimeDeserializer")
class SimpliXDateTimeDeserializerTest {

    private ObjectMapper objectMapper;

    static class DateTimeHolder {
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime dateTime;

        public LocalDateTime getDateTime() { return dateTime; }
        public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
    }

    // Custom deserializer binding for test
    static class LocalDateTimeDeserializer extends SimpliXDateTimeDeserializer<LocalDateTime> {
        public LocalDateTimeDeserializer() {
            super(ZoneId.of("UTC"), LocalDateTime.class);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("deserialize")
    class Deserialize {

        @Test
        @DisplayName("should deserialize ISO datetime string to LocalDateTime")
        void shouldDeserializeIsoDatetime() throws JsonProcessingException {
            DateTimeHolder result = objectMapper.readValue(
                "{\"dateTime\":\"2024-01-15 10:30:00\"}", DateTimeHolder.class);

            assertThat(result.getDateTime()).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
        }

        @Test
        @DisplayName("should throw for unparseable datetime string")
        void shouldThrowForInvalidDatetime() {
            assertThatThrownBy(() ->
                objectMapper.readValue("{\"dateTime\":\"not-a-date\"}", DateTimeHolder.class)
            ).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        }
    }
}
