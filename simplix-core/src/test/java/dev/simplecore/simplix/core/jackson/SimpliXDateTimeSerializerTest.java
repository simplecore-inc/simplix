package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXDateTimeSerializer")
class SimpliXDateTimeSerializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Temporal.class, new SimpliXDateTimeSerializer(ZoneId.of("UTC")));
        objectMapper.registerModule(module);
    }

    @Test
    @DisplayName("should serialize LocalDateTime to ISO formatted string")
    void shouldSerializeLocalDateTime() throws JsonProcessingException {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        String json = objectMapper.writeValueAsString(dateTime);

        assertThat(json).contains("2024-01-15");
        assertThat(json).contains("10:30:00");
    }
}
