package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
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
     * Priority order:
     * 1. simplix.date-time.default-timezone
     * 2. spring.jackson.time-zone
     * 3. user.timezone system property
     * 4. System default timezone
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
        return ZoneOffset.from(zoneId.getRules().getOffset(java.time.Instant.now()));
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
    public dev.simplecore.simplix.springboot.converter.SimpliXOffsetDateTimeConverter offsetDateTimeConverter() {
        return new dev.simplecore.simplix.springboot.converter.SimpliXOffsetDateTimeConverter();
    }

    /**
     * Provides JPA AttributeConverter for automatic LocalDateTime to OffsetDateTime conversion.
     */
    @Bean
    public dev.simplecore.simplix.springboot.converter.SimpliXLocalDateTimeConverter localDateTimeConverter() {
        return new dev.simplecore.simplix.springboot.converter.SimpliXLocalDateTimeConverter();
    }

    private ZoneId resolveApplicationZoneId() {
        // 1. Check SimpliX configuration
        String simplixTimezone = properties.getDateTime().getDefaultTimezone();
        if (simplixTimezone != null && !simplixTimezone.isEmpty()) {
            try {
                ZoneId zoneId = ZoneId.of(simplixTimezone);
                log.debug("Using SimpliX configured timezone: {}", zoneId);
                return zoneId;
            } catch (Exception e) {
                log.warn("Invalid SimpliX timezone configuration '{}', falling back to next option", simplixTimezone);
            }
        }

        // 2. Check Spring Jackson configuration
        String springTimezone = environment.getProperty("spring.jackson.time-zone");
        if (springTimezone != null && !springTimezone.isEmpty()) {
            try {
                ZoneId zoneId = ZoneId.of(springTimezone);
                log.debug("Using Spring Jackson timezone: {}", zoneId);
                return zoneId;
            } catch (Exception e) {
                log.warn("Invalid Spring Jackson timezone configuration '{}', falling back to next option", springTimezone);
            }
        }

        // 3. Check JVM system property
        String userTimezone = System.getProperty("user.timezone");
        if (userTimezone != null && !userTimezone.isEmpty()) {
            try {
                ZoneId zoneId = ZoneId.of(userTimezone);
                log.debug("Using JVM system timezone: {}", zoneId);
                return zoneId;
            } catch (Exception e) {
                log.warn("Invalid JVM system timezone '{}', falling back to system default", userTimezone);
            }
        }

        // 4. Use system default timezone
        ZoneId systemDefault = ZoneId.systemDefault();
        log.debug("Using system default timezone: {}", systemDefault);
        return systemDefault;
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
            this.applicationZoneOffset = ZoneOffset.from(applicationZoneId.getRules().getOffset(java.time.Instant.now()));
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
        public java.time.OffsetDateTime normalizeToApplicationTimezone(java.time.LocalDateTime localDateTime) {
            if (localDateTime == null) {
                return null;
            }
            return localDateTime.atOffset(applicationZoneOffset);
        }

        /**
         * Converts OffsetDateTime to UTC for database storage.
         * Used when useUtcForDatabase is true.
         */
        public java.time.OffsetDateTime normalizeForDatabase(java.time.OffsetDateTime offsetDateTime) {
            if (offsetDateTime == null) {
                return null;
            }
            return useUtcForDatabase ? offsetDateTime.withOffsetSameInstant(ZoneOffset.UTC) : offsetDateTime;
        }

        /**
         * Converts UTC OffsetDateTime from database to application timezone.
         * Used when reading from database and useUtcForDatabase is true.
         */
        public java.time.OffsetDateTime normalizeFromDatabase(java.time.OffsetDateTime offsetDateTime) {
            if (offsetDateTime == null) {
                return null;
            }
            return useUtcForDatabase ? offsetDateTime.withOffsetSameInstant(applicationZoneOffset) : offsetDateTime;
        }
    }
} 