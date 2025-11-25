/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Value formatter utility class
 * Provides centralized access to type-specific formatters
 */
@Slf4j
public final class ValueFormatter {
    
    private static final Map<Class<?>, Formatter<?>> formatters = new ConcurrentHashMap<>();
    private static SimplixExcelProperties.FormatProperties formatProperties;
    
    static {
        initializeDefaultFormatters();
    }
    
    private ValueFormatter() {
        // Prevent instantiation
    }
    
    /**
     * Initialize formatters with default settings
     */
    private static void initializeDefaultFormatters() {
        // Register default formatters
        DateFormatter dateFormatter = new DateFormatter();
        registerFormatter(Date.class, dateFormatter);
        // Calendar values are converted to Date before formatting
        registerFormatter(Calendar.class, new Formatter<Calendar>() {
            @Override
            public String format(Calendar value, String pattern) {
                return value != null ? dateFormatter.format(value.getTime(), pattern) : "";
            }
            
            @Override
            public Calendar parse(String value, String pattern) {
                Date date = dateFormatter.parse(value, pattern);
                if (date != null) {
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(FormatterCache.getDefaultZone()));
                    cal.setTime(date);
                    return cal;
                }
                return null;
            }
            
            @Override
            public String getDefaultPattern() {
                return dateFormatter.getDefaultPattern();
            }
        });
        registerFormatter(Number.class, new NumberFormatter());
        
        // Register temporal formatters
        registerFormatter(LocalDateTime.class, new TemporalFormatter<>(LocalDateTime.class, getDefaultPattern(LocalDateTime.class)));
        registerFormatter(LocalDate.class, new TemporalFormatter<>(LocalDate.class, getDefaultPattern(LocalDate.class)));
        registerFormatter(LocalTime.class, new TemporalFormatter<>(LocalTime.class, getDefaultPattern(LocalTime.class)));
        registerFormatter(ZonedDateTime.class, new TemporalFormatter<>(ZonedDateTime.class, getDefaultPattern(ZonedDateTime.class)));
        registerFormatter(OffsetDateTime.class, new TemporalFormatter<>(OffsetDateTime.class, getDefaultPattern(OffsetDateTime.class)));
        registerFormatter(Instant.class, new TemporalFormatter<>(Instant.class, getDefaultPattern(Instant.class)));
        
        // Register default formatter for unknown types
        registerFormatter(Object.class, new DefaultFormatter());
    }
    
    /**
     * Configure formatter with properties
     *
     * @param properties Format properties
     */
    public static void configure(SimplixExcelProperties.FormatProperties properties) {
        formatProperties = properties;
        initializeDefaultFormatters();
        log.info("Value formatter configured with properties: {}", properties);
    }
    
    /**
     * Get default pattern for type
     *
     * @param type Type class
     * @return Default pattern
     */
    private static String getDefaultPattern(Class<?> type) {
        if (formatProperties == null) {
            return getBuiltInDefaultPattern(type);
        }
        
        if (LocalDateTime.class.isAssignableFrom(type)) {
            return formatProperties.getDateTimeFormat();
        } else if (LocalDate.class.isAssignableFrom(type)) {
            return formatProperties.getDateFormat();
        } else if (LocalTime.class.isAssignableFrom(type)) {
            return formatProperties.getTimeFormat();
        } else if (ZonedDateTime.class.isAssignableFrom(type) || OffsetDateTime.class.isAssignableFrom(type)) {
            return formatProperties.getDateTimeFormat() + "XXX";
        } else if (Instant.class.isAssignableFrom(type)) {
            return formatProperties.getDateTimeFormat() + "'Z'";
        } else if (Number.class.isAssignableFrom(type)) {
            return formatProperties.getNumberFormat();
        }
        
        return getBuiltInDefaultPattern(type);
    }
    
    /**
     * Get built-in default pattern
     *
     * @param type Type class
     * @return Built-in default pattern
     */
    private static String getBuiltInDefaultPattern(Class<?> type) {
        if (LocalDateTime.class.isAssignableFrom(type)) {
            return "yyyy-MM-dd HH:mm:ss";
        } else if (LocalDate.class.isAssignableFrom(type)) {
            return "yyyy-MM-dd";
        } else if (LocalTime.class.isAssignableFrom(type)) {
            return "HH:mm:ss";
        } else if (ZonedDateTime.class.isAssignableFrom(type) || OffsetDateTime.class.isAssignableFrom(type)) {
            return "yyyy-MM-dd'T'HH:mm:ssXXX";
        } else if (Instant.class.isAssignableFrom(type)) {
            return "yyyy-MM-dd'T'HH:mm:ss'Z'";
        } else if (Number.class.isAssignableFrom(type)) {
            return "#,##0.00";
        }
        return "";
    }
    
    /**
     * Register a formatter for a specific type
     *
     * @param type Type class
     * @param formatter Formatter instance
     * @param <T> Type parameter
     */
    public static <T> void registerFormatter(Class<T> type, Formatter<T> formatter) {
        formatters.put(type, formatter);
    }
    
    /**
     * Format value based on its type and column annotation
     *
     * @param value Value to format
     * @param column Column annotation
     * @return Formatted string
     */
    @SuppressWarnings("unchecked")
    public static String formatValue(Object value, ExcelColumn column) {
        if (value == null) {
            return "";
        }
        
        // Get appropriate formatter
        Formatter<Object> formatter = (Formatter<Object>) getFormatter(value.getClass());
        
        // Use default pattern based on value type
        String pattern = null;
        if (value instanceof Number) {
            pattern = getDefaultPattern(value.getClass());
        } else if (value instanceof Date || value instanceof Calendar || value instanceof Temporal) {
            pattern = getDefaultPattern(value.getClass());
        }
        
        return formatter.format(value, pattern);
    }
    
    /**
     * Parse string value to target type
     *
     * @param value String value
     * @param targetType Target type class
     * @param pattern Format pattern (optional)
     * @return Parsed value
     */
    @SuppressWarnings("unchecked")
    public static <T> T parseValue(String value, Class<T> targetType, String pattern) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        Formatter<T> formatter = (Formatter<T>) getFormatter(targetType);
        return formatter.parse(value.trim(), pattern);
    }
    
    /**
     * Get formatter for type
     *
     * @param type Type class
     * @return Formatter instance
     */
    private static Formatter<?> getFormatter(Class<?> type) {
        // Try exact type match first
        Formatter<?> formatter = formatters.get(type);
        if (formatter != null) {
            return formatter;
        }
        
        // Try superclass/interface match
        for (Map.Entry<Class<?>, Formatter<?>> entry : formatters.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }
        
        // Use default formatter
        return formatters.get(Object.class);
    }
} 