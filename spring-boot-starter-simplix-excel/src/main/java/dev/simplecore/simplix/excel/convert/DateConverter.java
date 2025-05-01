/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

import dev.simplecore.simplix.excel.format.FormatterCache;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Converter for traditional Java Date and Calendar types
 * Handles parsing and formatting of java.util.Date and java.util.Calendar
 */
@Slf4j
public class DateConverter implements Converter<Object> {
    
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
            "dd MMM yyyy",
            "HH:mm:ss",
            "HH:mm"
    );
    
    /**
     * Format date using cached formatter
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        
        if (pattern == null || pattern.isEmpty()) {
            pattern = "yyyy-MM-dd";
        }
        
        try {
            return FormatterCache.getLegacyDateFormatter(pattern).format(date);
        } catch (Exception e) {
            log.warn("Error formatting date with pattern {}: {}", pattern, e.getMessage());
            return date.toString();
        }
    }
    
    /**
     * Format calendar using cached formatter
     */
    public static String formatCalendar(Calendar calendar, String pattern) {
        if (calendar == null) {
            return "";
        }
        return formatDate(calendar.getTime(), pattern);
    }
    
    /**
     * Parse string to Date
     */
    public static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        // Try all common patterns
        for (String pattern : COMMON_DATE_PATTERNS) {
            try {
                return FormatterCache.getLegacyDateFormatter(pattern).parse(value);
            } catch (ParseException ignored) {
                // Try next pattern
            }
        }
        
        try {
            // Try milliseconds since epoch
            long time = Long.parseLong(value);
            return new Date(time);
        } catch (NumberFormatException ignored) {
            // Not a timestamp
        }
        
        log.warn("Unable to parse date value: {}", value);
        return null;
    }
    
    /**
     * Parse string to Calendar
     */
    public static Calendar parseCalendar(String value) {
        Date date = parseDate(value);
        if (date == null) {
            return null;
        }
        
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(FormatterCache.getDefaultZone()));
        calendar.setTime(date);
        return calendar;
    }
    
    @Override
    public Object fromString(String value, Class<?> targetType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        if (targetType == Date.class) {
            return parseDate(value);
        } else if (targetType == Calendar.class) {
            return parseCalendar(value);
        }
        
        return null;
    }
    
    @Override
    public String toString(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof Date) {
            return formatDate((Date) value, pattern);
        } else if (value instanceof Calendar) {
            return formatCalendar((Calendar) value, pattern);
        }
        
        return value.toString();
    }
} 