package dev.simplecore.simplix.core.convert.datetime;

import java.time.ZoneId;
import java.time.temporal.Temporal;

/**
 * Interface for converting between date/time objects and strings
 */
public interface DateTimeConverter {
    /**
     * Converts text representation to a specific type of temporal object.
     *
     * @param value String value to convert
     * @param targetType Class of the target temporal object
     * @param <T> Target temporal type
     * @return Converted temporal object
     */
    <T extends Temporal> T fromString(String value, Class<T> targetType);

    /**
     * Converts temporal object to string.
     *
     * @param value Temporal object to convert
     * @return Converted string representation
     */
    String toString(Temporal value);

    /**
     * Creates a DateTimeConverter with default timezone.
     *
     * @return DateTimeConverter using system default timezone
     */
    static DateTimeConverter getDefault() {
        return new StandardDateTimeConverter();
    }

    /**
     * Creates a DateTimeConverter with specified timezone.
     *
     * @param zoneId Timezone to use
     * @return DateTimeConverter using the specified timezone
     */
    static DateTimeConverter of(ZoneId zoneId) {
        return new StandardDateTimeConverter(zoneId);
    }
} 