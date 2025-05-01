/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Centralized cache for various formatters used in Excel export
 * Provides thread-safe access to cached formatter instances
 */
@Slf4j
public final class FormatterCache {
    
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    
    private static final ConcurrentMap<String, DateTimeFormatter> temporalFormatters = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, SimpleDateFormat> legacyDateFormatters = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, DecimalFormat> numberFormatters = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Function<Enum<?>, String>> enumValueExtractors = new ConcurrentHashMap<>();
    
    private FormatterCache() {
        // Prevent instantiation
    }
    
    /**
     * Get cached DateTimeFormatter for pattern
     *
     * @param pattern Date/time pattern
     * @return DateTimeFormatter instance
     */
    public static DateTimeFormatter getTemporalFormatter(String pattern) {
        return temporalFormatters.computeIfAbsent(pattern,
            p -> DateTimeFormatter.ofPattern(p).withZone(DEFAULT_ZONE));
    }
    
    /**
     * Get cached SimpleDateFormat for pattern
     *
     * @param pattern Date format pattern
     * @return SimpleDateFormat instance
     */
    public static SimpleDateFormat getLegacyDateFormatter(String pattern) {
        return legacyDateFormatters.computeIfAbsent(pattern, p -> {
            SimpleDateFormat formatter = new SimpleDateFormat(p);
            formatter.setTimeZone(TimeZone.getTimeZone(DEFAULT_ZONE));
            return formatter;
        });
    }
    
    /**
     * Get cached DecimalFormat for pattern
     *
     * @param pattern Number format pattern
     * @return DecimalFormat instance
     */
    public static DecimalFormat getNumberFormatter(String pattern) {
        return numberFormatters.computeIfAbsent(pattern, DecimalFormat::new);
    }
    
    /**
     * Register enum value extractor
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> void registerEnumValueExtractor(Class<E> enumClass, Function<E, String> extractor) {
        enumValueExtractors.put(enumClass, (Function<Enum<?>, String>) extractor);
    }
    
    /**
     * Get enum value extractor
     */
    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> Function<E, String> getEnumValueExtractor(Class<E> enumClass) {
        return (Function<E, String>) enumValueExtractors.get(enumClass);
    }
    
    /**
     * Clear all formatter caches
     */
    public static void clearAll() {
        temporalFormatters.clear();
        legacyDateFormatters.clear();
        numberFormatters.clear();
        enumValueExtractors.clear();
        log.info("All formatter caches cleared");
    }
    
    /**
     * Get default zone ID used by formatters
     *
     * @return Default ZoneId
     */
    public static ZoneId getDefaultZone() {
        return DEFAULT_ZONE;
    }
} 