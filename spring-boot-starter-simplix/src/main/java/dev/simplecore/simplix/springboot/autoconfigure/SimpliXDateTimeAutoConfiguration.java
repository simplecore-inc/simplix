package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.converter.SimpliXLocalDateTimeConverter;
import dev.simplecore.simplix.springboot.converter.SimpliXOffsetDateTimeConverter;
import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * Auto-configuration for SimpliX DateTime handling.
 * Provides centralized timezone management using Spring Boot's standard configuration.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(SimpliXProperties.class)
@ConditionalOnProperty(prefix = "simplix.core", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXDateTimeAutoConfiguration {

    private final Environment environment;
    private final SimpliXProperties properties;

    public SimpliXDateTimeAutoConfiguration(Environment environment, SimpliXProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    public void configureTimezone() {
        ZoneId applicationZoneId = resolveApplicationZoneId();
        
        // Set JVM default timezone if configured
        if (properties.getDateTime().getDefaultTimezone() != null) {
            TimeZone.setDefault(TimeZone.getTimeZone(applicationZoneId));
            log.info("Set JVM default timezone to: {}", applicationZoneId);
        }
        
        // Configure Excel/CSV converters to use application timezone
        try {
            Class<?> temporalConverterClass = Class.forName("dev.simplecore.simplix.excel.convert.TemporalConverter");
            temporalConverterClass.getMethod("setDefaultZone", ZoneId.class).invoke(null, applicationZoneId);
            log.debug("Configured TemporalConverter to use application timezone: {}", applicationZoneId);
        } catch (Exception e) {
            log.debug("TemporalConverter not available, skipping configuration");
        }
        
        try {
            Class<?> typeConverterClass = Class.forName("dev.simplecore.simplix.excel.convert.TypeConverter");
            typeConverterClass.getMethod("setDefaultZone", ZoneId.class).invoke(null, applicationZoneId);
            log.debug("Configured TypeConverter to use application timezone: {}", applicationZoneId);
        } catch (Exception e) {
            log.debug("TypeConverter not available, skipping configuration");
        }
        
        log.info("SimpliX DateTime configuration:");
        log.info("  Application timezone: {}", applicationZoneId);
        log.info("  Use UTC for database: {}", properties.getDateTime().isUseUtcForDatabase());
        log.info("  Normalize timezone: {}", properties.getDateTime().isNormalizeTimezone());
    }

    /**
     * Provides the application's default ZoneId.
     * Resolved via {@link ApplicationTimezoneResolver}:
     * <ol>
     *   <li>{@code simplix.date-time.default-timezone} (canonical key)</li>
     *   <li>{@code spring.jackson.time-zone} (deprecated alias, logged)</li>
     *   <li>{@code user.timezone} (deprecated alias, logged)</li>
     *   <li>JVM default timezone (last resort, logged)</li>
     * </ol>
     */
    @Bean
    public ZoneId applicationZoneId() {
        return resolveApplicationZoneId();
    }

    /**
     * Provides the application's default ZoneOffset.
     * Useful for OffsetDateTime operations.
     */
    @Bean
    public ZoneOffset applicationZoneOffset() {
        ZoneId zoneId = resolveApplicationZoneId();
        return ZoneOffset.from(zoneId.getRules().getOffset(Instant.now()));
    }

    /**
     * Provides a timezone service for centralized timezone operations.
     */
    @Bean
    public SimpliXTimezoneService timezoneService() {
        return new SimpliXTimezoneService(
            resolveApplicationZoneId(),
            properties.getDateTime().isUseUtcForDatabase(),
            properties.getDateTime().isNormalizeTimezone()
        );
    }

    /**
     * Provides JPA AttributeConverter for automatic OffsetDateTime timezone conversion.
     */
    @Bean
    public SimpliXOffsetDateTimeConverter offsetDateTimeConverter() {
        return new SimpliXOffsetDateTimeConverter();
    }

    /**
     * Provides JPA AttributeConverter for automatic LocalDateTime to OffsetDateTime conversion.
     */
    @Bean
    public SimpliXLocalDateTimeConverter localDateTimeConverter() {
        return new SimpliXLocalDateTimeConverter();
    }

    private ZoneId resolveApplicationZoneId() {
        return ApplicationTimezoneResolver.resolve(environment);
    }

    /**
     * Service for centralized timezone operations.
     */
    public static class SimpliXTimezoneService {
        private final ZoneId applicationZoneId;
        private final ZoneOffset applicationZoneOffset;
        private final boolean useUtcForDatabase;
        private final boolean normalizeTimezone;

        public SimpliXTimezoneService(ZoneId applicationZoneId, boolean useUtcForDatabase, boolean normalizeTimezone) {
            this.applicationZoneId = applicationZoneId;
            this.applicationZoneOffset = ZoneOffset.from(applicationZoneId.getRules().getOffset(Instant.now()));
            this.useUtcForDatabase = useUtcForDatabase;
            this.normalizeTimezone = normalizeTimezone;
        }

        public ZoneId getApplicationZoneId() {
            return applicationZoneId;
        }

        public ZoneOffset getApplicationZoneOffset() {
            return applicationZoneOffset;
        }

        public boolean isUseUtcForDatabase() {
            return useUtcForDatabase;
        }

        public boolean isNormalizeTimezone() {
            return normalizeTimezone;
        }

        /**
         * Converts LocalDateTime to OffsetDateTime using application timezone.
         * Used when timezone information is not available.
         */
        public OffsetDateTime normalizeToApplicationTimezone(LocalDateTime localDateTime) {
            if (localDateTime == null) {
                return null;
            }
            return localDateTime.atOffset(applicationZoneOffset);
        }

        /**
         * Converts OffsetDateTime to UTC for database storage.
         * Used when useUtcForDatabase is true.
         */
        public OffsetDateTime normalizeForDatabase(OffsetDateTime offsetDateTime) {
            if (offsetDateTime == null) {
                return null;
            }
            return useUtcForDatabase ? offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC) : offsetDateTime;
        }

        /**
         * Converts UTC OffsetDateTime from database to application timezone.
         * Used when reading from database and useUtcForDatabase is true.
         */
        public OffsetDateTime normalizeFromDatabase(OffsetDateTime offsetDateTime) {
            if (offsetDateTime == null) {
                return null;
            }
            return useUtcForDatabase ? offsetDateTime.withOffsetSameInstant(applicationZoneOffset) : offsetDateTime;
        }
    }
} 