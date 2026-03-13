package dev.simplecore.simplix.core.timezone;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Thread-local context for per-request timezone.
 *
 * <p>Typically set by an HTTP interceptor from the {@code X-Timezone} header.
 * Services and serializers read the timezone for boundary calculations
 * and response formatting.
 *
 * <p>Falls back to {@link ZoneOffset#UTC} when no timezone is set
 * (e.g., non-HTTP threads such as SSE push or scheduler).
 *
 * <pre>{@code
 * // In a service method (within HTTP request thread):
 * ZoneId zone = TimezoneContext.getZoneId();
 * Instant todayStart = TimezoneContext.todayStart();
 *
 * // In a non-HTTP thread with explicit zone:
 * Instant todayStart = TimezoneContext.todayStart(ZoneId.of("Asia/Seoul"));
 * }</pre>
 */
public final class TimezoneContext {

    private static final ThreadLocal<ZoneId> ZONE_ID = new ThreadLocal<>();

    private TimezoneContext() {
        throw new AssertionError("Cannot instantiate TimezoneContext");
    }

    /**
     * Set the timezone for the current request thread.
     *
     * @param zoneId IANA timezone (e.g., Asia/Seoul)
     */
    public static void set(ZoneId zoneId) {
        ZONE_ID.set(zoneId);
    }

    /**
     * Get the timezone for the current request thread.
     *
     * @return the timezone, or null if not set
     */
    public static ZoneId getZoneId() {
        return ZONE_ID.get();
    }

    /**
     * Get the timezone for the current request, with a fallback.
     *
     * @param fallback timezone to use if none is set
     * @return the request timezone, or the fallback
     */
    public static ZoneId getZoneIdOrDefault(ZoneId fallback) {
        ZoneId zone = ZONE_ID.get();
        return zone != null ? zone : fallback;
    }

    /**
     * Compute the start-of-today instant in the current request timezone.
     * Falls back to UTC if no timezone is set.
     *
     * @return midnight instant in the request timezone
     */
    public static Instant todayStart() {
        return todayStart(getZoneIdOrDefault(ZoneOffset.UTC));
    }

    /**
     * Compute the start-of-today instant in the given timezone.
     *
     * @param zone the timezone for "today" boundary calculation
     * @return midnight instant in the given timezone
     */
    public static Instant todayStart(ZoneId zone) {
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    /**
     * Clear the thread-local state. Must be called after request processing
     * to prevent memory leaks.
     */
    public static void clear() {
        ZONE_ID.remove();
    }
}
