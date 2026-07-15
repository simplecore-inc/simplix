package dev.simplecore.simplix.web.config.openapi;

import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;

import java.time.LocalTime;

/**
 * Registers OpenAPI schema overrides for temporal types that swagger-core does not map to a string
 * by default.
 *
 * <p>swagger-core maps {@link java.time.LocalDate} to {@code string/date} and the instant-bearing
 * types ({@link java.time.Instant}, {@link java.time.OffsetDateTime}) to {@code string/date-time},
 * but has no primitive mapping for {@link LocalTime}. Left alone, LocalTime is introspected as a
 * {@code {hour, minute, second, nano}} bean, which contradicts the Jackson wire format (an
 * {@code HH:mm[:ss]} string). This registrar emits LocalTime as a partial-time string so the
 * generated contract matches the serialized payload.
 */
public class TemporalSchemaRegistrar {

    @PostConstruct
    public void register() {
        SpringDocUtils.getConfig().replaceWithSchema(
            LocalTime.class,
            new StringSchema().format("partial-time").example("18:00:00"));
    }
}
