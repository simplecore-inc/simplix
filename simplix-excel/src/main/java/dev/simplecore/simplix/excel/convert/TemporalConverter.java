/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Converter for Java 8+ temporal types (LocalDate, LocalDateTime, etc.)
 * Handles parsing and formatting of java.time.* classes
 */
@Slf4j
public class TemporalConverter implements Converter<Object> {
    
    private static ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    
    /**
     * Set the default zone for temporal conversions.
     * This should be called during application startup to use application timezone.
     */
    public static void setDefaultZone(ZoneId zoneId) {
        DEFAULT_ZONE = zoneId != null ? zoneId : ZoneId.systemDefault();
    }
    private static final ConcurrentMap<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<String>> FORMAT_PATTERNS = new HashMap<>();
    
    static {
        // Initialize common format patterns for each temporal type
        FORMAT_PATTERNS.put(LocalDateTime.class, Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
                "yyyy-MM-dd'T'HH:mm:ss.SSS",     // ISO-8601 without timezone
                "yyyy-MM-dd'T'HH:mm:ss",         // ISO-8601 without millis
                "yyyy-MM-dd HH:mm:ss.SSS",       // Common datetime with millis
                "yyyy-MM-dd HH:mm:ss",           // Common datetime
                "yyyy-MM-dd HH:mm",              // Short datetime
                "yyyy.MM.dd HH:mm:ss",           // Dot format
                "yyyyMMddHHmmss"                 // Compact format
        ));
        
        FORMAT_PATTERNS.put(LocalDate.class, Arrays.asList(
                "yyyy-MM-dd",                    // ISO date
                "yyyy/MM/dd",                    // Common date with slash
                "yyyyMMdd",                      // Compact date
                "yyyy.MM.dd",                    // Dot format
                "dd-MM-yyyy",                    // European format
                "dd/MM/yyyy"                     // European slash format
        ));
        
        FORMAT_PATTERNS.put(LocalTime.class, Arrays.asList(
                "HH:mm:ss.SSS",                  // Full time with millis
                "HH:mm:ss",                      // Full time
                "HH:mm",                         // Short time
                "HHmmss"                         // Compact format
        ));
        
        FORMAT_PATTERNS.put(ZonedDateTime.class, Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
                "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with timezone, no millis
                "yyyy-MM-dd HH:mm:ss z",         // Human readable with timezone
                "yyyy-MM-dd'T'HH:mm:ss z",       // ISO time with space-separated zone
                "yyyy-MM-dd'T'HH:mm:ss[XXX][X]"  // ISO time with flexible zone
        ));
        
        FORMAT_PATTERNS.put(OffsetDateTime.class, Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with offset
                "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO-8601 with offset, no millis
                "yyyy-MM-dd HH:mm:ss XXX",       // Human readable with offset
                "yyyy-MM-dd'T'HH:mm:ss XXX"      // ISO time with space-separated offset
        ));
        
        FORMAT_PATTERNS.put(Instant.class, Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",  // ISO-8601 in UTC with millis
                "yyyy-MM-dd'T'HH:mm:ss'Z'",      // ISO-8601 in UTC without millis
                "yyyy-MM-dd HH:mm:ss'Z'",        // Human readable in UTC
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO-8601 with timezone
                "yyyy-MM-dd'T'HH:mm:ssXXX"       // ISO-8601 with timezone, no millis
        ));
    }
    
    /**
     * Check if a class is a temporal type
     *
     * @param clazz Class to check
     * @return true if it's a temporal type
     */
    public static boolean isTemporal(Class<?> clazz) {
        return Temporal.class.isAssignableFrom(clazz) || 
               FORMAT_PATTERNS.containsKey(clazz);
    }
    
    /**
     * Format a temporal object using the given pattern
     *
     * @param temporal Temporal object
     * @param pattern Date/time pattern
     * @return Formatted string
     */
    public static String formatTemporal(Temporal temporal, String pattern) {
        if (temporal == null) {
            return "";
        }
        
        // Set default pattern if not provided
        if (pattern == null || pattern.isEmpty()) {
            if (temporal instanceof LocalDate) {
                pattern = "yyyy-MM-dd";
            } else if (temporal instanceof LocalTime) {
                pattern = "HH:mm:ss";
            } else if (temporal instanceof LocalDateTime) {
                pattern = "yyyy-MM-dd HH:mm:ss";
            } else if (temporal instanceof ZonedDateTime || 
                       temporal instanceof OffsetDateTime) {
                pattern = "yyyy-MM-dd'T'HH:mm:ssXXX";
            } else if (temporal instanceof Instant) {
                pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
            } else {
                return temporal.toString();
            }
        }
        
        try {
            DateTimeFormatter formatter = getFormatter(pattern);
            
            if (temporal instanceof ZonedDateTime) {
                return formatter.format(((ZonedDateTime) temporal).withZoneSameInstant(DEFAULT_ZONE));
            } else if (temporal instanceof OffsetDateTime) {
                return formatter.format(((OffsetDateTime) temporal).atZoneSameInstant(DEFAULT_ZONE));
            } else if (temporal instanceof Instant) {
                return formatter.format(((Instant) temporal).atZone(DEFAULT_ZONE));
            } else {
                return formatter.format(temporal);
            }
        } catch (Exception e) {
            log.warn("Error formatting temporal value: {}", e.getMessage());
            return temporal.toString();
        }
    }
    
    /**
     * Format LocalDate
     */
    public static String formatLocalDate(LocalDate date, String pattern) {
        return formatTemporal(date, pattern);
    }
    
    /**
     * Format LocalTime
     */
    public static String formatLocalTime(LocalTime time, String pattern) {
        return formatTemporal(time, pattern);
    }
    
    /**
     * Format LocalDateTime
     */
    public static String formatLocalDateTime(LocalDateTime dateTime, String pattern) {
        return formatTemporal(dateTime, pattern);
    }
    
    /**
     * Format ZonedDateTime
     */
    public static String formatZonedDateTime(ZonedDateTime dateTime, String pattern) {
        return formatTemporal(dateTime, pattern);
    }
    
    /**
     * Format OffsetDateTime
     */
    public static String formatOffsetDateTime(OffsetDateTime dateTime, String pattern) {
        return formatTemporal(dateTime, pattern);
    }
    
    /**
     * Format Instant
     */
    public static String formatInstant(Instant instant, String pattern) {
        return formatTemporal(instant, pattern);
    }
    
    /**
     * Parse string to temporal object of the specified type
     *
     * @param value String to parse
     * @param targetType Target temporal type
     * @return Parsed temporal object or null if parsing fails
     */
    @SuppressWarnings("unchecked")
    public static <T extends Temporal> T parseTemporalFromString(String value, Class<T> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        List<String> patterns = FORMAT_PATTERNS.get(targetType);
        if (patterns == null) {
            log.warn("Unsupported temporal type: {}", targetType.getName());
            return null;
        }
        
        // Try each pattern for the target type
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = getFormatter(pattern);
                
                if (targetType == LocalDateTime.class) {
                    return (T) LocalDateTime.parse(value, formatter);
                } else if (targetType == LocalDate.class) {
                    return (T) LocalDate.parse(value, formatter);
                } else if (targetType == LocalTime.class) {
                    return (T) LocalTime.parse(value, formatter);
                } else if (targetType == ZonedDateTime.class) {
                    return (T) ZonedDateTime.parse(value, formatter).withZoneSameInstant(DEFAULT_ZONE);
                } else if (targetType == OffsetDateTime.class) {
                    return (T) OffsetDateTime.parse(value, formatter).atZoneSameInstant(DEFAULT_ZONE)
                            .toOffsetDateTime();
                } else if (targetType == Instant.class) {
                    return (T) Instant.from(formatter.parse(value));
                }
            } catch (DateTimeParseException ignored) {
                // Try next pattern
            }
        }
        
        // Special case: try to parse a timestamp (milliseconds since epoch)
        if (targetType == Instant.class || targetType == LocalDateTime.class || 
            targetType == ZonedDateTime.class || targetType == OffsetDateTime.class) {
            try {
                long epochMilli = Long.parseLong(value.trim());
                Instant instant = Instant.ofEpochMilli(epochMilli);
                
                if (targetType == Instant.class) {
                    return (T) instant;
                } else if (targetType == LocalDateTime.class) {
                    return (T) LocalDateTime.ofInstant(instant, DEFAULT_ZONE);
                } else if (targetType == ZonedDateTime.class) {
                    return (T) ZonedDateTime.ofInstant(instant, DEFAULT_ZONE);
                } else { // OffsetDateTime.class
                    return (T) OffsetDateTime.ofInstant(instant, DEFAULT_ZONE);
                }
            } catch (NumberFormatException ignored) {
                // Not a timestamp
            }
        }
        
        log.warn("Unable to parse '{}' as {}", value, targetType.getSimpleName());
        return null;
    }
    
    /**
     * Parse LocalDate
     */
    public static LocalDate parseLocalDate(String value) {
        return parseTemporalFromString(value, LocalDate.class);
    }
    
    /**
     * Parse LocalTime
     */
    public static LocalTime parseLocalTime(String value) {
        return parseTemporalFromString(value, LocalTime.class);
    }
    
    /**
     * Parse LocalDateTime
     */
    public static LocalDateTime parseLocalDateTime(String value) {
        return parseTemporalFromString(value, LocalDateTime.class);
    }
    
    /**
     * Parse ZonedDateTime
     */
    public static ZonedDateTime parseZonedDateTime(String value) {
        return parseTemporalFromString(value, ZonedDateTime.class);
    }
    
    /**
     * Parse OffsetDateTime
     */
    public static OffsetDateTime parseOffsetDateTime(String value) {
        return parseTemporalFromString(value, OffsetDateTime.class);
    }
    
    /**
     * Parse Instant
     */
    public static Instant parseInstant(String value) {
        return parseTemporalFromString(value, Instant.class);
    }
    
    /**
     * Get cached formatter for pattern
     *
     * @param pattern Date/time pattern
     * @return DateTimeFormatter
     */
    private static DateTimeFormatter getFormatter(String pattern) {
        return FORMATTERS.computeIfAbsent(pattern, p -> 
            DateTimeFormatter.ofPattern(p).withZone(DEFAULT_ZONE));
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Object fromString(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        if (isTemporal(targetType)) {
            return parseTemporalFromString(value, (Class<? extends Temporal>) targetType);
        }
        
        return null;
    }
    
    @Override
    public String toString(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof Temporal) {
            return formatTemporal((Temporal) value, pattern);
        }
        
        return value.toString();
    }
} 