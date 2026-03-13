package dev.simplecore.simplix.springboot.web.timezone;

import dev.simplecore.simplix.core.timezone.TimezoneContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.ZoneId;

/**
 * Intercepts HTTP requests to extract the {@code X-Timezone} header
 * and store it in {@link TimezoneContext} for the request lifecycle.
 *
 * <p>Header format: {@code X-Timezone: Asia/Seoul} (IANA timezone ID).
 * Invalid values are silently ignored and the fallback timezone is used.
 *
 * @see TimezoneContext
 */
@Slf4j
public class TimezoneInterceptor implements HandlerInterceptor {

    public static final String HEADER_NAME = "X-Timezone";

    private final ZoneId fallbackZoneId;

    /**
     * @param fallbackZoneId timezone to use when the header is missing or invalid
     */
    public TimezoneInterceptor(ZoneId fallbackZoneId) {
        this.fallbackZoneId = fallbackZoneId;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        String headerValue = request.getHeader(HEADER_NAME);

        if (headerValue != null && !headerValue.isBlank()) {
            try {
                TimezoneContext.set(ZoneId.of(headerValue.trim()));
            } catch (Exception e) {
                log.warn("Invalid X-Timezone header value: '{}', using fallback: {}",
                        headerValue, fallbackZoneId);
                TimezoneContext.set(fallbackZoneId);
            }
        } else {
            TimezoneContext.set(fallbackZoneId);
        }

        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        TimezoneContext.clear();
    }
}
