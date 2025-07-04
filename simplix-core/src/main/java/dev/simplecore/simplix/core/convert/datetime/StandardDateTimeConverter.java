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
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX'['VV']'",  // ISO-8601 with zone ID
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",         // ISO-8601 with offset
            "yyyy-MM-dd'T'HH:mm:ssXXX",             // ISO-8601 with offset, no millis
            "yyyy-MM-dd HH:mm:ss z",                // Human readable with timezone
            "yyyy.MM.dd HH:mm:ss z",                // Dot format with timezone
            "yyyy/MM/dd HH:mm:ss z"                 // Slash format with timezone
        });
        FORMATS.put(OffsetDateTime.class, new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with offset
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with offset, no millis
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",    // ISO-8601 with short offset
            "yyyy-MM-dd'T'HH:mm:ssX",        // ISO-8601 with short offset, no millis
            "yyyy-MM-dd HH:mm:ss XXX",       // Human readable with offset
            "yyyy.MM.dd HH:mm:ss XXX",       // Dot format with offset
            "yyyy/MM/dd HH:mm:ss XXX"        // Slash format with offset
        });
        FORMATS.put(Instant.class, new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with timezone, no millis
            "yyyy-MM-dd HH:mm:ss'Z'",        // UTC format
            "yyyy-MM-dd HH:mm:ss.SSS'Z'"     // UTC format with millis
        });
    }
    
    private final ZoneId zoneId;
    private final ZoneOffset zoneOffset;

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
        this.zoneOffset = ZoneOffset.from(zoneId.getRules().getOffset(Instant.now()));
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
                } else if (targetType == OffsetDateTime.class) {
                    return targetType.cast(OffsetDateTime.parse(value, formatter).withOffsetSameInstant(zoneOffset));
                } else if (targetType == Instant.class) {
                    return targetType.cast(Instant.from(formatter.parse(value)));
                }
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        // Fallback: try to parse as other types and convert
        try {
            return convertFromFallback(value, targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse datetime value: " + value + " to " + targetType.getSimpleName(), e);
        }
    }

    /**
     * Fallback conversion method
     */
    private <T extends Temporal> T convertFromFallback(String value, Class<T> targetType) {
        // Try parsing as different types and convert
        try {
            // Try ZonedDateTime first (most complete)
            ZonedDateTime zdt = ZonedDateTime.parse(value);
            return convertFromZonedDateTime(zdt, targetType);
        } catch (Exception ignored) {}
        
        try {
            // Try OffsetDateTime
            OffsetDateTime odt = OffsetDateTime.parse(value);
            return convertFromOffsetDateTime(odt, targetType);
        } catch (Exception ignored) {}
        
        try {
            // Try LocalDateTime with default zone
            LocalDateTime ldt = LocalDateTime.parse(value);
            return convertFromLocalDateTime(ldt, targetType);
        } catch (Exception ignored) {}
        
        throw new IllegalArgumentException("Unable to parse: " + value);
    }

    @SuppressWarnings("unchecked")
    private <T extends Temporal> T convertFromZonedDateTime(ZonedDateTime zdt, Class<T> targetType) {
        if (targetType == ZonedDateTime.class) {
            return (T) zdt.withZoneSameInstant(zoneId);
        } else if (targetType == OffsetDateTime.class) {
            return (T) zdt.toOffsetDateTime().withOffsetSameInstant(zoneOffset);
        } else if (targetType == LocalDateTime.class) {
            return (T) zdt.withZoneSameInstant(zoneId).toLocalDateTime();
        } else if (targetType == LocalDate.class) {
            return (T) zdt.withZoneSameInstant(zoneId).toLocalDate();
        } else if (targetType == LocalTime.class) {
            return (T) zdt.withZoneSameInstant(zoneId).toLocalTime();
        } else if (targetType == Instant.class) {
            return (T) zdt.toInstant();
        }
        throw new IllegalArgumentException("Unsupported conversion to: " + targetType);
    }

    @SuppressWarnings("unchecked")
    private <T extends Temporal> T convertFromOffsetDateTime(OffsetDateTime odt, Class<T> targetType) {
        if (targetType == OffsetDateTime.class) {
            return (T) odt.withOffsetSameInstant(zoneOffset);
        } else if (targetType == ZonedDateTime.class) {
            return (T) odt.atZoneSameInstant(zoneId);
        } else if (targetType == LocalDateTime.class) {
            return (T) odt.withOffsetSameInstant(zoneOffset).toLocalDateTime();
        } else if (targetType == LocalDate.class) {
            return (T) odt.withOffsetSameInstant(zoneOffset).toLocalDate();
        } else if (targetType == LocalTime.class) {
            return (T) odt.withOffsetSameInstant(zoneOffset).toLocalTime();
        } else if (targetType == Instant.class) {
            return (T) odt.toInstant();
        }
        throw new IllegalArgumentException("Unsupported conversion to: " + targetType);
    }

    @SuppressWarnings("unchecked")
    private <T extends Temporal> T convertFromLocalDateTime(LocalDateTime ldt, Class<T> targetType) {
        if (targetType == LocalDateTime.class) {
            return (T) ldt;
        } else if (targetType == ZonedDateTime.class) {
            return (T) ldt.atZone(zoneId);
        } else if (targetType == OffsetDateTime.class) {
            return (T) ldt.atOffset(zoneOffset);
        } else if (targetType == LocalDate.class) {
            return (T) ldt.toLocalDate();
        } else if (targetType == LocalTime.class) {
            return (T) ldt.toLocalTime();
        } else if (targetType == Instant.class) {
            return (T) ldt.atZone(zoneId).toInstant();
        }
        throw new IllegalArgumentException("Unsupported conversion to: " + targetType);
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
            } else if (value instanceof OffsetDateTime) {
                return DateTimeFormatter.ofPattern(ISO_FORMAT)
                    .format(((OffsetDateTime) value).withOffsetSameInstant(zoneOffset));
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