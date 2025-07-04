/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Unified Type Converter
 * A utility class that supports conversion of various data types
 */
@Slf4j
public final class TypeConverter {
    
    private static final DateConverter DATE_CONVERTER = new DateConverter();
    private static final TemporalConverter TEMPORAL_CONVERTER = new TemporalConverter();
    
    private static final Map<String, DecimalFormat> NUMBER_FORMATTERS = new ConcurrentHashMap<>();
    private static ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    
    /**
     * Set the default zone for type conversions.
     * This should be called during application startup to use application timezone.
     */
    public static void setDefaultZone(ZoneId zoneId) {
        DEFAULT_ZONE = zoneId != null ? zoneId : ZoneId.systemDefault();
    }
    
    // Common patterns to try when converting date formats
    private static final List<String> COMMON_DATE_PATTERNS = Arrays.asList(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyyMMdd",
            "dd-MMM-yyyy",
            "dd MMM yyyy"
    );
    
    private static final Map<Class<? extends Enum<?>>, Function<Enum<?>, String>> ENUM_VALUE_EXTRACTORS = new ConcurrentHashMap<>();
    
    private TypeConverter() {
        // Prevent instantiation
    }
    
    /**
     * Register custom enum value extractor
     * 
     * @param enumClass Enum class
     * @param extractor Function to extract display value from enum value
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <E extends Enum<E>> void registerEnumValueExtractor(Class<E> enumClass, Function<E, String> extractor) {
        ENUM_VALUE_EXTRACTORS.put(enumClass, (Function) extractor);
    }
    
    /**
     * Convert object to string, safely handling null values and various data types
     * 
     * @param value Value to convert
     * @param pattern Date or number format pattern
     * @return String representation of the value
     */
    public static String toString(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        
        try {
            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Number) {
                return formatNumber((Number) value, pattern);
            } else if (value instanceof Date) {
                return formatDate((Date) value, pattern);
            } else if (value instanceof Calendar) {
                return formatDate(((Calendar) value).getTime(), pattern);
            } else if (value instanceof LocalDate) {
                return formatLocalDate((LocalDate) value, pattern);
            } else if (value instanceof LocalDateTime) {
                return formatLocalDateTime((LocalDateTime) value, pattern);
            } else if (value instanceof LocalTime) {
                return formatLocalTime((LocalTime) value, pattern);
            } else if (value instanceof ZonedDateTime) {
                return formatZonedDateTime((ZonedDateTime) value, pattern);
            } else if (value instanceof OffsetDateTime) {
                return formatOffsetDateTime((OffsetDateTime) value, pattern);
            } else if (value instanceof Instant) {
                return formatInstant((Instant) value, pattern);
            } else if (value instanceof Boolean) {
                return (Boolean) value ? "Y" : "N";
            } else if (value instanceof Map) {
                return formatMap((Map<?, ?>) value);
            } else if (value instanceof Collection) {
                return formatCollection((Collection<?>) value);
            } else if (value instanceof Enum) {
                return formatEnum((Enum<?>) value);
            } else if (value.getClass().isArray()) {
                return formatArray(value);
            }
        } catch (Exception e) {
            log.warn("Error converting value of type {} to string: {}", 
                    value.getClass().getName(), e.getMessage());
        }
        
        return value.toString();
    }
    
    /**
     * Format number using cached formatter
     */
    public static String formatNumber(Number number, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = "#,##0.###";
        }
        
        try {
            return NUMBER_FORMATTERS.computeIfAbsent(pattern, 
                    p -> new DecimalFormat(p)).format(number);
        } catch (Exception e) {
            log.warn("Error formatting number with pattern {}: {}", pattern, e.getMessage());
            return number.toString();
        }
    }
    
    /**
     * Format Date
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        return DATE_CONVERTER.toString(date, pattern);
    }
    
    /**
     * Format LocalDate
     */
    public static String formatLocalDate(LocalDate date, String pattern) {
        if (date == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(date, pattern);
    }
    
    /**
     * Format LocalDateTime
     */
    public static String formatLocalDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(dateTime, pattern);
    }
    
    /**
     * Format LocalTime
     */
    public static String formatLocalTime(LocalTime time, String pattern) {
        if (time == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(time, pattern);
    }
    
    /**
     * Format ZonedDateTime
     */
    public static String formatZonedDateTime(ZonedDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(dateTime, pattern);
    }
    
    /**
     * Format OffsetDateTime
     */
    public static String formatOffsetDateTime(OffsetDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(dateTime, pattern);
    }
    
    /**
     * Format Instant
     */
    public static String formatInstant(Instant instant, String pattern) {
        if (instant == null) {
            return "";
        }
        return TEMPORAL_CONVERTER.toString(instant, pattern);
    }
    
    /**
     * Format Enum value using registered extractor
     */
    public static String formatEnum(Enum<?> enumValue) {
        if (enumValue == null) {
            return "";
        }
        Function<Enum<?>, String> extractor = ENUM_VALUE_EXTRACTORS.get(enumValue.getClass());
        return extractor != null ? extractor.apply(enumValue) : enumValue.name();
    }
    
    /**
     * Format Map
     */
    public static String formatMap(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + toString(e.getValue(), null))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
    
    /**
     * Format Collection
     */
    public static String formatCollection(Collection<?> collection) {
        return collection.stream()
                .map(item -> toString(item, null))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
    
    /**
     * Format Array
     */
    public static String formatArray(Object array) {
        return Arrays.stream(toObjectArray(array))
                .map(item -> toString(item, null))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
    
    private static Object[] toObjectArray(Object array) {
        Class<?> componentType = array.getClass().getComponentType();
        if (componentType.isPrimitive()) {
            int length = java.lang.reflect.Array.getLength(array);
            Object[] result = new Object[length];
            for (int i = 0; i < length; i++) {
                result[i] = java.lang.reflect.Array.get(array, i);
            }
            return result;
        }
        return (Object[]) array;
    }
    
    /**
     * Convert string to specified target type
     * 
     * @param value String to convert
     * @param targetType Target type class
     * @return Converted object
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object fromString(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            if (targetType == String.class) {
                return value;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            } else if (targetType == BigDecimal.class) {
                return new BigDecimal(value);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value) || 
                       "Y".equalsIgnoreCase(value) || 
                       "Yes".equalsIgnoreCase(value) || 
                       "True".equalsIgnoreCase(value) || 
                       "1".equals(value);
            } else if (targetType == Date.class) {
                return parseDate(value);
            } else if (targetType == Calendar.class) {
                Date date = parseDate(value);
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    return cal;
                }
                return null;
            } else if (targetType == LocalDate.class) {
                return parseLocalDate(value);
            } else if (targetType == LocalDateTime.class) {
                return parseLocalDateTime(value);
            } else if (targetType == LocalTime.class) {
                return parseLocalTime(value);
            } else if (targetType == ZonedDateTime.class) {
                return parseZonedDateTime(value);
            } else if (targetType == OffsetDateTime.class) {
                return parseOffsetDateTime(value);
            } else if (targetType == Instant.class) {
                return parseInstant(value);
            } else if (targetType.isEnum()) {
                return parseEnum(value, (Class<? extends Enum>) targetType);
            }
        } catch (Exception e) {
            log.warn("Error converting string to {}: {}", targetType.getName(), e.getMessage());
        }
        
        return value;
    }
    
    /**
     * Convert string to Date
     */
    public static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // Try converting with common patterns
        for (String pattern : COMMON_DATE_PATTERNS) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern);
                formatter.setTimeZone(TimeZone.getTimeZone(DEFAULT_ZONE));
                return formatter.parse(value.trim());
            } catch (Exception ignored) {
                // Try next pattern
            }
        }
        
        // Try converting to timestamp
        try {
            long timestamp = Long.parseLong(value.trim());
            return new Date(timestamp);
        } catch (NumberFormatException ignored) {
            // Not a timestamp
        }
        
        log.warn("Unable to parse date value: {}", value);
        return null;
    }
    
    /**
     * Convert string to LocalDate
     */
    public static LocalDate parseLocalDate(String value) {
        return (LocalDate) TEMPORAL_CONVERTER.fromString(value, LocalDate.class);
    }
    
    /**
     * Convert string to LocalDateTime
     */
    public static LocalDateTime parseLocalDateTime(String value) {
        return (LocalDateTime) TEMPORAL_CONVERTER.fromString(value, LocalDateTime.class);
    }
    
    /**
     * Convert string to LocalTime
     */
    public static LocalTime parseLocalTime(String value) {
        return (LocalTime) TEMPORAL_CONVERTER.fromString(value, LocalTime.class);
    }
    
    /**
     * Convert string to ZonedDateTime
     */
    public static ZonedDateTime parseZonedDateTime(String value) {
        return (ZonedDateTime) TEMPORAL_CONVERTER.fromString(value, ZonedDateTime.class);
    }
    
    /**
     * Convert string to OffsetDateTime
     */
    public static OffsetDateTime parseOffsetDateTime(String value) {
        return (OffsetDateTime) TEMPORAL_CONVERTER.fromString(value, OffsetDateTime.class);
    }
    
    /**
     * Convert string to Instant
     */
    public static Instant parseInstant(String value) {
        return (Instant) TEMPORAL_CONVERTER.fromString(value, Instant.class);
    }
    
    /**
     * Convert string to Enum
     */
    public static <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // Try converting by name
        try {
            return Enum.valueOf(enumClass, value.trim());
        } catch (IllegalArgumentException ignored) {
            // Not exact enum name
        }
        
        // Try converting by name ignoring case
        for (E constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(value.trim())) {
                return constant;
            }
        }
        
        // Try converting by ordinal
        try {
            int ordinal = Integer.parseInt(value.trim());
            E[] constants = enumClass.getEnumConstants();
            if (ordinal >= 0 && ordinal < constants.length) {
                return constants[ordinal];
            }
        } catch (NumberFormatException ignored) {
            // Not a number
        }
        
        log.warn("Unable to parse '{}' as {}", value, enumClass.getSimpleName());
        return null;
    }
    
    /**
     * Convert string to Temporal type
     */
    public static <T extends Temporal> T parseTemporal(String value, Class<T> temporalType) {
        return TemporalConverter.parseTemporalFromString(value, temporalType);
    }
} 