package dev.simplecore.simplix.springboot.web.timezone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.simplecore.simplix.core.timezone.TimezoneContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimezoneAwareInstantSerializer - serializes Instant using per-request timezone")
class TimezoneAwareInstantSerializerTest {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");

    private TimezoneAwareInstantSerializer serializer;

    @Mock
    private JsonGenerator generator;

    @Mock
    private SerializerProvider provider;

    @BeforeEach
    void setUp() {
        serializer = new TimezoneAwareInstantSerializer(FALLBACK_ZONE);
    }

    @AfterEach
    void tearDown() {
        TimezoneContext.clear();
    }

    @Test
    @DisplayName("Should write null when value is null")
    void serializeNull() throws IOException {
        serializer.serialize(null, generator, provider);

        verify(generator).writeNull();
    }

    @Test
    @DisplayName("Should use TimezoneContext zone when available")
    void useTimezoneContext() throws IOException {
        TimezoneContext.set(ZoneId.of("Asia/Seoul"));
        // 2024-01-15T00:00:00Z -> 2024-01-15T09:00:00+09:00 in Seoul
        Instant instant = Instant.parse("2024-01-15T00:00:00Z");

        serializer.serialize(instant, generator, provider);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(generator).writeString(captor.capture());
        String result = captor.getValue();
        assertThat(result).contains("+09:00");
        assertThat(result).startsWith("2024-01-15T09:00:00");
    }

    @Test
    @DisplayName("Should use fallback zone when TimezoneContext is empty")
    void useFallbackZone() throws IOException {
        // No TimezoneContext set
        Instant instant = Instant.parse("2024-01-15T00:00:00Z");

        serializer.serialize(instant, generator, provider);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(generator).writeString(captor.capture());
        String result = captor.getValue();
        // UTC offset can be represented as +00:00 or Z
        assertThat(result).satisfiesAnyOf(
                r -> assertThat(r).contains("+00:00"),
                r -> assertThat(r).contains("Z")
        );
        assertThat(result).startsWith("2024-01-15T00:00:00");
    }

    @Test
    @DisplayName("Should format with millis and timezone offset pattern")
    void formatPattern() throws IOException {
        TimezoneContext.set(ZoneId.of("America/New_York"));
        Instant instant = Instant.parse("2024-06-15T12:30:45.123Z");

        serializer.serialize(instant, generator, provider);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(generator).writeString(captor.capture());
        String result = captor.getValue();
        // Should contain millis (123) and offset
        assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("Should report Instant as handled type")
    void handledType() {
        assertThat(serializer.handledType()).isEqualTo(Instant.class);
    }
}
