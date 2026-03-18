package dev.simplecore.simplix.springboot.web.timezone;

import dev.simplecore.simplix.core.timezone.TimezoneContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimezoneInterceptor - extracts X-Timezone header and sets TimezoneContext")
class TimezoneInterceptorTest {

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("UTC");

    private TimezoneInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new TimezoneInterceptor(FALLBACK_ZONE);
    }

    @AfterEach
    void tearDown() {
        TimezoneContext.clear();
    }

    @Test
    @DisplayName("Should set timezone from valid X-Timezone header")
    void validTimezoneHeader() {
        when(request.getHeader("X-Timezone")).thenReturn("Asia/Seoul");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(TimezoneContext.getZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("Should trim whitespace from header value")
    void trimWhitespace() {
        when(request.getHeader("X-Timezone")).thenReturn("  America/New_York  ");

        interceptor.preHandle(request, response, new Object());

        assertThat(TimezoneContext.getZoneId()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    @DisplayName("Should use fallback timezone when header is null")
    void nullHeader() {
        when(request.getHeader("X-Timezone")).thenReturn(null);

        interceptor.preHandle(request, response, new Object());

        assertThat(TimezoneContext.getZoneId()).isEqualTo(FALLBACK_ZONE);
    }

    @Test
    @DisplayName("Should use fallback timezone when header is blank")
    void blankHeader() {
        when(request.getHeader("X-Timezone")).thenReturn("   ");

        interceptor.preHandle(request, response, new Object());

        assertThat(TimezoneContext.getZoneId()).isEqualTo(FALLBACK_ZONE);
    }

    @Test
    @DisplayName("Should use fallback timezone when header contains invalid timezone")
    void invalidTimezone() {
        when(request.getHeader("X-Timezone")).thenReturn("Invalid/Timezone");

        interceptor.preHandle(request, response, new Object());

        assertThat(TimezoneContext.getZoneId()).isEqualTo(FALLBACK_ZONE);
    }

    @Test
    @DisplayName("Should clear TimezoneContext on afterCompletion")
    void afterCompletion() {
        TimezoneContext.set(ZoneId.of("Asia/Tokyo"));

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(TimezoneContext.getZoneId()).isNull();
    }

    @Test
    @DisplayName("Should clear TimezoneContext on afterCompletion even with exception")
    void afterCompletionWithException() {
        TimezoneContext.set(ZoneId.of("Europe/London"));

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("error"));

        assertThat(TimezoneContext.getZoneId()).isNull();
    }

    @Test
    @DisplayName("HEADER_NAME constant should be X-Timezone")
    void headerNameConstant() {
        assertThat(TimezoneInterceptor.HEADER_NAME).isEqualTo("X-Timezone");
    }
}
