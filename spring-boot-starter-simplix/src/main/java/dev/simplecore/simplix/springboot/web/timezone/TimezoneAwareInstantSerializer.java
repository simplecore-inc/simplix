package dev.simplecore.simplix.springboot.web.timezone;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.simplecore.simplix.core.timezone.TimezoneContext;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Jackson serializer for {@link Instant} that applies the per-request timezone
 * from {@link TimezoneContext}.
 *
 * <p>Output format: {@code 2026-03-13T00:00:00.000+09:00} (ISO-8601 with offset and millis).
 *
 * <p>Timezone resolution order:
 * <ol>
 *   <li>{@link TimezoneContext#getZoneId()} — per-request (HTTP thread)</li>
 *   <li>Configured fallback — for non-HTTP threads (SSE, scheduler)</li>
 * </ol>
 */
public class TimezoneAwareInstantSerializer extends JsonSerializer<Instant> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final ZoneId fallbackZoneId;

    /**
     * @param fallbackZoneId timezone to use when {@link TimezoneContext} has no value
     */
    public TimezoneAwareInstantSerializer(ZoneId fallbackZoneId) {
        this.fallbackZoneId = fallbackZoneId;
    }

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        ZoneId zone = TimezoneContext.getZoneId();
        if (zone == null) {
            zone = fallbackZoneId;
        }

        String formatted = FORMATTER.format(value.atZone(zone));
        gen.writeString(formatted);
    }

    @Override
    public Class<Instant> handledType() {
        return Instant.class;
    }
}
