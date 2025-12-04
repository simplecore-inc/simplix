package dev.simplecore.simplix.springboot.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "simplix")
public class SimpliXProperties {
    private CoreProperties core = new CoreProperties();
    private ExceptionHandlerProperties exceptionHandler = new ExceptionHandlerProperties();
    private DateTimeProperties dateTime = new DateTimeProperties();

    @Data
    public static class CoreProperties {
        private boolean enabled = true;
    }

    @Data
    public static class ExceptionHandlerProperties {
        private boolean enabled = true;
    }

    @Data
    public static class DateTimeProperties {
        /**
         * Default timezone for the application.
         * This will be used when timezone information is not available.
         * Falls back to spring.jackson.time-zone, then user.timezone system property,
         * then system default timezone.
         */
        private String defaultTimezone;

        /**
         * Whether to use UTC for database operations.
         * When true, all OffsetDateTime values are converted to UTC before saving to database.
         */
        private boolean useUtcForDatabase = true;

        /**
         * Whether to normalize timezone-less datetime values to application timezone.
         * When true, LocalDateTime values are assumed to be in application timezone.
         */
        private boolean normalizeTimezone = true;
    }
} 