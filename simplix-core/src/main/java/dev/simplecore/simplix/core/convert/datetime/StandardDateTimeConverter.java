package dev.simplecore.simplix.core.convert.datetime;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard implementation of DateTimeConverter
 */
public class StandardDateTimeConverter implements DateTimeConverter {
    private static final String ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
    private static final Map<Class<?>, String[]> FORMATS = new HashMap<>();
    
    static {
        FORMATS.put(LocalDateTime.class, new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
            "yyyy-MM-dd'T'HH:mm:ss.SSS",     // ISO-8601 without timezone
            "yyyy-MM-dd'T'HH:mm:ss",         // ISO-8601 without millis
            "yyyy-MM-dd HH:mm:ss.SSS",       // Common datetime with millis
            "yyyy-MM-dd HH:mm:ss",           // Common datetime
            "yyyy-MM-dd HH:mm",              // Short datetime
            "yyyy.MM.dd HH:mm:ss",           // Dot format
            "yyyy.MM.dd HH:mm",              // Short dot format
            "yyyy/MM/dd HH:mm:ss",           // Slash format
            "yyyy/MM/dd HH:mm",              // Short slash format
            "yyyyMMddHHmmss",                // Compact format
            "yyyyMMddHHmm"                   // Short compact format
        });
        FORMATS.put(LocalDate.class, new String[] {
            "yyyy-MM-dd",                    // ISO date
            "yyyy/MM/dd",                    // Common date with slash
            "yyyyMMdd",                      // Compact date
            "yyyy.MM.dd",                    // Dot format
            "dd-MM-yyyy",                    // European format
            "dd/MM/yyyy",                    // European slash format
            "dd.MM.yyyy",                    // European dot format
            "MM-dd-yyyy",                    // US format
            "MM/dd/yyyy",                    // US slash format
            "MM.dd.yyyy"                     // US dot format
        });
        FORMATS.put(LocalTime.class, new String[] {
            "HH:mm:ss.SSS",                  // Full time with millis
            "HH:mm:ss",                      // Full time
            "HH:mm",                         // Short time
            "hh:mm:ss a",                    // 12-hour format with seconds
            "hh:mm a",                       // 12-hour format
            "HHmmss",                        // Compact format
            "HHmm"                           // Short compact format
        });
        FORMATS.put(ZonedDateTime.class, new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with timezone, no millis
            "yyyy-MM-dd HH:mm:ss z",         // Human readable with timezone
            "yyyy.MM.dd HH:mm:ss z",         // Dot format with timezone
            "yyyy/MM/dd HH:mm:ss z"          // Slash format with timezone
        });
        FORMATS.put(Instant.class, new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with timezone, no millis
            "yyyy-MM-dd HH:mm:ss'Z'",        // UTC format
            "yyyy-MM-dd HH:mm:ss.SSS'Z'"     // UTC format with millis
        });
    }
    
    private final ZoneId zoneId;

    /**
     * Creates converter with system default timezone
     */
    public StandardDateTimeConverter() {
        this(ZoneId.systemDefault());
    }

    /**
     * Creates converter with specified timezone
     *
     * @param zoneId Timezone to use
     */
    public StandardDateTimeConverter(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    @Override
    public <T extends Temporal> T fromString(String value, Class<T> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String[] formats = FORMATS.get(targetType);
        if (formats == null) {
            throw new IllegalArgumentException("Unsupported temporal type: " + targetType);
        }
        
        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                if (targetType == LocalDateTime.class) {
                    return targetType.cast(LocalDateTime.parse(value, formatter));
                } else if (targetType == LocalDate.class) {
                    return targetType.cast(LocalDate.parse(value, formatter));
                } else if (targetType == LocalTime.class) {
                    return targetType.cast(LocalTime.parse(value, formatter));
                } else if (targetType == ZonedDateTime.class) {
                    return targetType.cast(ZonedDateTime.parse(value, formatter).withZoneSameInstant(zoneId));
                } else if (targetType == Instant.class) {
                    return targetType.cast(Instant.from(formatter.parse(value)));
                }
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        throw new IllegalArgumentException("Unable to parse datetime value: " + value);
    }

    @Override
    public String toString(Temporal value) {
        if (value == null) return null;
        
        try {
            if (value instanceof LocalDateTime) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((LocalDateTime) value).atZone(zoneId));
            } else if (value instanceof ZonedDateTime) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((ZonedDateTime) value).withZoneSameInstant(zoneId));
            } else if (value instanceof Instant) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((Instant) value).atZone(zoneId));
            } else if (value instanceof LocalDate) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((LocalDate) value).atStartOfDay(zoneId));
            } else if (value instanceof LocalTime) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((LocalTime) value).atDate(LocalDate.now(zoneId))
                    .atZone(zoneId));
            }
        } catch (Exception e) {
            return value.toString();
        }
        return value.toString();
    }
} 