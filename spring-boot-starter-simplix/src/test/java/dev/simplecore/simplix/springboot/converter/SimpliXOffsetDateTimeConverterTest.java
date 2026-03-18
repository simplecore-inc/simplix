package dev.simplecore.simplix.springboot.converter;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXDateTimeAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXOffsetDateTimeConverter - converts OffsetDateTime timezone for JPA")
class SimpliXOffsetDateTimeConverterTest {

    @Mock
    private SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService;

    private SimpliXOffsetDateTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SimpliXOffsetDateTimeConverter();
        converter.setTimezoneService(timezoneService);
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    class ConvertToDatabaseColumn {

        @Test
        @DisplayName("Should return null when attribute is null")
        void nullAttribute() {
            OffsetDateTime result = converter.convertToDatabaseColumn(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should convert to UTC when useUtcForDatabase is true")
        void convertToUtc() {
            when(timezoneService.isUseUtcForDatabase()).thenReturn(true);

            OffsetDateTime seoulTime = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));
            OffsetDateTime utcTime = seoulTime.withOffsetSameInstant(ZoneOffset.UTC);

            when(timezoneService.normalizeForDatabase(seoulTime)).thenReturn(utcTime);

            OffsetDateTime result = converter.convertToDatabaseColumn(seoulTime);

            assertThat(result).isEqualTo(utcTime);
            assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
        }

        @Test
        @DisplayName("Should return attribute as-is when useUtcForDatabase is false")
        void noConversion() {
            when(timezoneService.isUseUtcForDatabase()).thenReturn(false);

            OffsetDateTime seoulTime = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));

            OffsetDateTime result = converter.convertToDatabaseColumn(seoulTime);

            assertThat(result).isEqualTo(seoulTime);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("Should return null when database data is null")
        void nullDbData() {
            OffsetDateTime result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should convert from UTC to application timezone when useUtcForDatabase is true")
        void convertFromUtc() {
            when(timezoneService.isUseUtcForDatabase()).thenReturn(true);

            OffsetDateTime utcTime = OffsetDateTime.of(2024, 6, 15, 3, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime seoulTime = utcTime.withOffsetSameInstant(ZoneOffset.ofHours(9));

            when(timezoneService.normalizeFromDatabase(utcTime)).thenReturn(seoulTime);

            OffsetDateTime result = converter.convertToEntityAttribute(utcTime);

            assertThat(result).isEqualTo(seoulTime);
            assertThat(result.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        }

        @Test
        @DisplayName("Should return database data as-is when useUtcForDatabase is false")
        void noConversion() {
            when(timezoneService.isUseUtcForDatabase()).thenReturn(false);

            OffsetDateTime dbValue = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));

            OffsetDateTime result = converter.convertToEntityAttribute(dbValue);

            assertThat(result).isEqualTo(dbValue);
        }
    }

    @Test
    @DisplayName("Should work without timezone service (null service)")
    void noTimezoneService() {
        SimpliXOffsetDateTimeConverter converterWithoutService = new SimpliXOffsetDateTimeConverter();
        // Do not set timezoneService

        OffsetDateTime value = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));

        OffsetDateTime toDb = converterWithoutService.convertToDatabaseColumn(value);
        OffsetDateTime fromDb = converterWithoutService.convertToEntityAttribute(value);

        assertThat(toDb).isEqualTo(value);
        assertThat(fromDb).isEqualTo(value);
    }
}
