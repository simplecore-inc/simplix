package dev.simplecore.simplix.core.timezone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimezoneContext - Extended Coverage")
class TimezoneContextExtendedTest {

    @AfterEach
    void cleanup() {
        TimezoneContext.clear();
    }

    @Test
    @DisplayName("should return null when not set")
    void shouldReturnNull() {
        assertThat(TimezoneContext.getZoneId()).isNull();
    }

    @Test
    @DisplayName("should set and get custom timezone")
    void shouldSetAndGet() {
        TimezoneContext.set(ZoneId.of("Asia/Tokyo"));
        assertThat(TimezoneContext.getZoneId()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    @DisplayName("should clear to null")
    void shouldClearToNull() {
        TimezoneContext.set(ZoneId.of("US/Eastern"));
        TimezoneContext.clear();
        assertThat(TimezoneContext.getZoneId()).isNull();
    }

    @Test
    @DisplayName("should return fallback when not set")
    void shouldReturnFallback() {
        ZoneId fallback = ZoneId.of("UTC");
        assertThat(TimezoneContext.getZoneIdOrDefault(fallback)).isEqualTo(fallback);
    }

    @Test
    @DisplayName("should return set zone over fallback")
    void shouldReturnSetZoneOverFallback() {
        TimezoneContext.set(ZoneId.of("Asia/Seoul"));
        assertThat(TimezoneContext.getZoneIdOrDefault(ZoneId.of("UTC"))).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("should compute todayStart")
    void shouldComputeTodayStart() {
        assertThat(TimezoneContext.todayStart()).isNotNull();
    }

    @Test
    @DisplayName("should compute todayStart with zone")
    void shouldComputeTodayStartWithZone() {
        assertThat(TimezoneContext.todayStart(ZoneId.of("Asia/Seoul"))).isNotNull();
    }
}
