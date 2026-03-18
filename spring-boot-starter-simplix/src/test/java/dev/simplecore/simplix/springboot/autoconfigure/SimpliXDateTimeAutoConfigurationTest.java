package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXDateTimeAutoConfiguration - centralized timezone management")
class SimpliXDateTimeAutoConfigurationTest {

    @Mock
    private Environment environment;

    private SimpliXProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SimpliXProperties();
    }

    @Nested
    @DisplayName("applicationZoneId")
    class ApplicationZoneId {

        @Test
        @DisplayName("Should use simplix.date-time.default-timezone when configured")
        void useSimplixTimezone() {
            properties.getDateTime().setDefaultTimezone("Asia/Seoul");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneId zoneId = config.applicationZoneId();

            assertThat(zoneId).isEqualTo(ZoneId.of("Asia/Seoul"));
        }

        @Test
        @DisplayName("Should fall back to spring.jackson.time-zone")
        void fallbackToSpringTimezone() {
            properties.getDateTime().setDefaultTimezone(null);
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn("Europe/London");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneId zoneId = config.applicationZoneId();

            assertThat(zoneId).isEqualTo(ZoneId.of("Europe/London"));
        }

        @Test
        @DisplayName("Should fall back to system default when no timezone configured")
        void fallbackToSystemDefault() {
            properties.getDateTime().setDefaultTimezone(null);
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneId zoneId = config.applicationZoneId();

            assertThat(zoneId).isNotNull();
        }
    }

    @Nested
    @DisplayName("applicationZoneOffset")
    class ApplicationZoneOffset {

        @Test
        @DisplayName("Should return ZoneOffset matching the configured timezone")
        void matchesConfiguredTimezone() {
            properties.getDateTime().setDefaultTimezone("UTC");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneOffset offset = config.applicationZoneOffset();

            assertThat(offset).isEqualTo(ZoneOffset.UTC);
        }
    }

    @Nested
    @DisplayName("configureTimezone")
    class ConfigureTimezone {

        @Test
        @DisplayName("Should configure timezone when default-timezone is set")
        void configureWithDefaultTimezone() {
            properties.getDateTime().setDefaultTimezone("Asia/Tokyo");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            // configureTimezone sets JVM default timezone
            config.configureTimezone();

            // The method should complete without errors
            assertThat(config.applicationZoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        }

        @Test
        @DisplayName("Should not set JVM timezone when default-timezone is null")
        void doNotSetJvmTimezoneWhenNull() {
            properties.getDateTime().setDefaultTimezone(null);
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            config.configureTimezone();

            // Should use system default without changing JVM timezone
            assertThat(config.applicationZoneId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle invalid simplix timezone gracefully")
        void handleInvalidSimplixTimezone() {
            properties.getDateTime().setDefaultTimezone("Invalid/Zone");
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn(null);

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneId zoneId = config.applicationZoneId();

            // Should fall back to system default
            assertThat(zoneId).isNotNull();
        }

        @Test
        @DisplayName("Should handle invalid Spring timezone gracefully")
        void handleInvalidSpringTimezone() {
            properties.getDateTime().setDefaultTimezone(null);
            when(environment.getProperty("spring.jackson.time-zone")).thenReturn("Not/Valid");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            ZoneId zoneId = config.applicationZoneId();

            // Should fall back to next option
            assertThat(zoneId).isNotNull();
        }

        @Test
        @DisplayName("Should create converters via bean methods")
        void createConverters() {
            properties.getDateTime().setDefaultTimezone("UTC");

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            assertThat(config.offsetDateTimeConverter()).isNotNull();
            assertThat(config.localDateTimeConverter()).isNotNull();
        }
    }

    @Nested
    @DisplayName("SimpliXTimezoneService")
    class TimezoneServiceTest {

        @Test
        @DisplayName("Should create timezone service with correct configuration")
        void createTimezoneService() {
            properties.getDateTime().setDefaultTimezone("Asia/Seoul");
            properties.getDateTime().setUseUtcForDatabase(true);
            properties.getDateTime().setNormalizeTimezone(true);

            SimpliXDateTimeAutoConfiguration config =
                    new SimpliXDateTimeAutoConfiguration(environment, properties);

            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service = config.timezoneService();

            assertThat(service.getApplicationZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
            assertThat(service.isUseUtcForDatabase()).isTrue();
            assertThat(service.isNormalizeTimezone()).isTrue();
        }

        @Test
        @DisplayName("Should normalize LocalDateTime to application timezone")
        void normalizeLocalDateTime() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);

            java.time.LocalDateTime local = java.time.LocalDateTime.of(2024, 6, 15, 12, 0, 0);
            OffsetDateTime result = service.normalizeToApplicationTimezone(local);

            assertThat(result).isNotNull();
            assertThat(result.toLocalDateTime()).isEqualTo(local);
            assertThat(result.getOffset()).isEqualTo(service.getApplicationZoneOffset());
        }

        @Test
        @DisplayName("Should return null when normalizing null LocalDateTime")
        void normalizeNullLocalDateTime() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("UTC"), true, true);

            assertThat(service.normalizeToApplicationTimezone(null)).isNull();
        }

        @Test
        @DisplayName("Should convert to UTC for database when useUtcForDatabase is true")
        void normalizeForDatabaseUtc() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);

            OffsetDateTime seoulTime = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0,
                    ZoneOffset.ofHours(9));
            OffsetDateTime result = service.normalizeForDatabase(seoulTime);

            assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(result.toInstant()).isEqualTo(seoulTime.toInstant());
        }

        @Test
        @DisplayName("Should return as-is for database when useUtcForDatabase is false")
        void normalizeForDatabaseNoConvert() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), false, true);

            OffsetDateTime seoulTime = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0,
                    ZoneOffset.ofHours(9));
            OffsetDateTime result = service.normalizeForDatabase(seoulTime);

            assertThat(result).isEqualTo(seoulTime);
        }

        @Test
        @DisplayName("Should return null when normalizing null for database")
        void normalizeForDatabaseNull() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("UTC"), true, true);

            assertThat(service.normalizeForDatabase(null)).isNull();
        }

        @Test
        @DisplayName("Should convert from UTC to application timezone from database")
        void normalizeFromDatabaseUtc() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), true, true);

            OffsetDateTime utcTime = OffsetDateTime.of(2024, 6, 15, 3, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime result = service.normalizeFromDatabase(utcTime);

            assertThat(result.getOffset()).isEqualTo(service.getApplicationZoneOffset());
            assertThat(result.toInstant()).isEqualTo(utcTime.toInstant());
        }

        @Test
        @DisplayName("Should return as-is from database when useUtcForDatabase is false")
        void normalizeFromDatabaseNoConvert() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("Asia/Seoul"), false, true);

            OffsetDateTime utcTime = OffsetDateTime.of(2024, 6, 15, 3, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime result = service.normalizeFromDatabase(utcTime);

            assertThat(result).isEqualTo(utcTime);
        }

        @Test
        @DisplayName("Should return null when normalizing null from database")
        void normalizeFromDatabaseNull() {
            SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService service =
                    new SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService(
                            ZoneId.of("UTC"), true, true);

            assertThat(service.normalizeFromDatabase(null)).isNull();
        }
    }
}
