package dev.simplecore.simplix.springboot.converter;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXDateTimeAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXLocalDateTimeConverter - converts LocalDateTime to/from OffsetDateTime for JPA")
class SimpliXLocalDateTimeConverterTest {

    @Mock
    private SimpliXDateTimeAutoConfiguration.SimpliXTimezoneService timezoneService;

    private SimpliXLocalDateTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SimpliXLocalDateTimeConverter();
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
        @DisplayName("Should normalize to application timezone and convert to UTC for database")
        void normalizeAndConvertToUtc() {
            ZoneOffset seoulOffset = ZoneOffset.ofHours(9);
            when(timezoneService.isNormalizeTimezone()).thenReturn(true);
            when(timezoneService.isUseUtcForDatabase()).thenReturn(true);

            LocalDateTime local = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
            OffsetDateTime normalized = local.atOffset(seoulOffset);
            OffsetDateTime utc = normalized.withOffsetSameInstant(ZoneOffset.UTC);

            when(timezoneService.normalizeToApplicationTimezone(local)).thenReturn(normalized);
            when(timezoneService.normalizeForDatabase(normalized)).thenReturn(utc);

            OffsetDateTime result = converter.convertToDatabaseColumn(local);

            assertThat(result).isEqualTo(utc);
            assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
        }

        @Test
        @DisplayName("Should normalize without UTC conversion when useUtcForDatabase is false")
        void normalizeWithoutUtcConversion() {
            ZoneOffset seoulOffset = ZoneOffset.ofHours(9);
            when(timezoneService.isNormalizeTimezone()).thenReturn(true);
            when(timezoneService.isUseUtcForDatabase()).thenReturn(false);

            LocalDateTime local = LocalDateTime.of(2024, 6, 15, 12, 0, 0);
            OffsetDateTime normalized = local.atOffset(seoulOffset);

            when(timezoneService.normalizeToApplicationTimezone(local)).thenReturn(normalized);

            OffsetDateTime result = converter.convertToDatabaseColumn(local);

            assertThat(result).isEqualTo(normalized);
            assertThat(result.getOffset()).isEqualTo(seoulOffset);
        }

        @Test
        @DisplayName("Should use application zone offset as fallback when normalization is disabled")
        void fallbackToApplicationOffset() {
            ZoneOffset seoulOffset = ZoneOffset.ofHours(9);
            when(timezoneService.isNormalizeTimezone()).thenReturn(false);
            when(timezoneService.getApplicationZoneOffset()).thenReturn(seoulOffset);

            LocalDateTime local = LocalDateTime.of(2024, 6, 15, 12, 0, 0);

            OffsetDateTime result = converter.convertToDatabaseColumn(local);

            assertThat(result.toLocalDateTime()).isEqualTo(local);
            assertThat(result.getOffset()).isEqualTo(seoulOffset);
        }

        @Test
        @DisplayName("Should use system default timezone when timezoneService is null")
        void nullTimezoneService() {
            SimpliXLocalDateTimeConverter converterWithoutService = new SimpliXLocalDateTimeConverter();
            LocalDateTime local = LocalDateTime.of(2024, 6, 15, 12, 0, 0);

            OffsetDateTime result = converterWithoutService.convertToDatabaseColumn(local);

            assertThat(result).isNotNull();
            assertThat(result.toLocalDateTime()).isEqualTo(local);
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ConvertToEntityAttribute {

        @Test
        @DisplayName("Should return null when database data is null")
        void nullDbData() {
            LocalDateTime result = converter.convertToEntityAttribute(null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should convert UTC database value to application timezone when useUtcForDatabase is true")
        void convertFromUtcToAppTimezone() {
            ZoneOffset seoulOffset = ZoneOffset.ofHours(9);
            when(timezoneService.isUseUtcForDatabase()).thenReturn(true);

            OffsetDateTime utcValue = OffsetDateTime.of(2024, 6, 15, 3, 0, 0, 0, ZoneOffset.UTC);
            OffsetDateTime seoulValue = utcValue.withOffsetSameInstant(seoulOffset);

            when(timezoneService.normalizeFromDatabase(utcValue)).thenReturn(seoulValue);

            LocalDateTime result = converter.convertToEntityAttribute(utcValue);

            assertThat(result).isEqualTo(seoulValue.toLocalDateTime());
        }

        @Test
        @DisplayName("Should return LocalDateTime directly when useUtcForDatabase is false")
        void directConversionWhenNotUtc() {
            when(timezoneService.isUseUtcForDatabase()).thenReturn(false);

            OffsetDateTime dbValue = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));

            LocalDateTime result = converter.convertToEntityAttribute(dbValue);

            assertThat(result).isEqualTo(dbValue.toLocalDateTime());
        }

        @Test
        @DisplayName("Should return LocalDateTime directly when timezoneService is null")
        void nullTimezoneService() {
            SimpliXLocalDateTimeConverter converterWithoutService = new SimpliXLocalDateTimeConverter();
            OffsetDateTime dbValue = OffsetDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.ofHours(9));

            LocalDateTime result = converterWithoutService.convertToEntityAttribute(dbValue);

            assertThat(result).isEqualTo(dbValue.toLocalDateTime());
        }
    }
}
