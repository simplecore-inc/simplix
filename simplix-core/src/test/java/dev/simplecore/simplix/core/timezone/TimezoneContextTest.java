package dev.simplecore.simplix.core.timezone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimezoneContext")
class TimezoneContextTest {

    @AfterEach
    void tearDown() {
        TimezoneContext.clear();
    }

    @Nested
    @DisplayName("set and getZoneId")
    class SetAndGet {

        @Test
        @DisplayName("should return null when not set")
        void shouldReturnNullWhenNotSet() {
            assertThat(TimezoneContext.getZoneId()).isNull();
        }

        @Test
        @DisplayName("should return set timezone")
        void shouldReturnSetTimezone() {
            ZoneId seoul = ZoneId.of("Asia/Seoul");
            TimezoneContext.set(seoul);

            assertThat(TimezoneContext.getZoneId()).isEqualTo(seoul);
        }
    }

    @Nested
    @DisplayName("getZoneIdOrDefault")
    class GetZoneIdOrDefault {

        @Test
        @DisplayName("should return fallback when not set")
        void shouldReturnFallbackWhenNotSet() {
            ZoneId fallback = ZoneId.of("America/New_York");

            assertThat(TimezoneContext.getZoneIdOrDefault(fallback)).isEqualTo(fallback);
        }

        @Test
        @DisplayName("should return set timezone over fallback")
        void shouldReturnSetTimezoneOverFallback() {
            ZoneId seoul = ZoneId.of("Asia/Seoul");
            ZoneId fallback = ZoneId.of("America/New_York");
            TimezoneContext.set(seoul);

            assertThat(TimezoneContext.getZoneIdOrDefault(fallback)).isEqualTo(seoul);
        }
    }

    @Nested
    @DisplayName("todayStart")
    class TodayStart {

        @Test
        @DisplayName("should return start of today in UTC when no timezone set")
        void shouldReturnTodayStartInUtcWhenNotSet() {
            Instant todayStart = TimezoneContext.todayStart();

            Instant expectedStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
            assertThat(todayStart).isEqualTo(expectedStart);
        }

        @Test
        @DisplayName("should return start of today in set timezone")
        void shouldReturnTodayStartInSetTimezone() {
            ZoneId seoul = ZoneId.of("Asia/Seoul");
            TimezoneContext.set(seoul);

            Instant todayStart = TimezoneContext.todayStart();

            Instant expectedStart = LocalDate.now(seoul)
                .atStartOfDay(seoul).toInstant();
            assertThat(todayStart).isEqualTo(expectedStart);
        }

        @Test
        @DisplayName("should return start of today in given timezone")
        void shouldReturnTodayStartInGivenTimezone() {
            ZoneId tokyo = ZoneId.of("Asia/Tokyo");

            Instant todayStart = TimezoneContext.todayStart(tokyo);

            Instant expectedStart = LocalDate.now(tokyo)
                .atStartOfDay(tokyo).toInstant();
            assertThat(todayStart).isEqualTo(expectedStart);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should remove timezone after clear")
        void shouldRemoveTimezoneAfterClear() {
            TimezoneContext.set(ZoneId.of("Asia/Seoul"));
            TimezoneContext.clear();

            assertThat(TimezoneContext.getZoneId()).isNull();
        }
    }
}
