/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Type Converter Registry
 * Centralizes type conversion functionality to eliminate duplicate code.
 */
@Slf4j
public class ConverterRegistry {

    // Type-specific converter mapping
    private static final Map<Class<?>, Function<Object, String>> TO_STRING_CONVERTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Function<String, Object>> FROM_STRING_CONVERTERS = new ConcurrentHashMap<>();
    
    /**
     * Register String conversion converter
     * 
     * @param <T> Source type
     * @param sourceType Source type class
     * @param converter Conversion function
     */
    @SuppressWarnings("unchecked")
    public static <T> void registerToStringConverter(Class<T> sourceType, Function<T, String> converter) {
        TO_STRING_CONVERTERS.put(sourceType, obj -> converter.apply((T) obj));
    }
    
    /**
     * Register converter from String
     * 
     * @param <T> Target type
     * @param targetType Target type class
     * @param converter Conversion function
     */
    public static <T> void registerFromStringConverter(Class<T> targetType, Function<String, T> converter) {
        FROM_STRING_CONVERTERS.put(targetType, str -> converter.apply(str));
    }
    
    /**
     * Convert object to string
     * 
     * @param value Object to convert
     * @param defaultValue Default value (on conversion failure)
     * @return Converted string
     */
    public static String toString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        Class<?> type = value.getClass();
        
        // Search registered converter
        Function<Object, String> converter = TO_STRING_CONVERTERS.get(type);
        if (converter != null) {
            try {
                return converter.apply(value);
            } catch (Exception e) {
                log.warn("Error converting {} to String: {}", type.getSimpleName(), e.getMessage());
                return defaultValue;
            }
        }
        
        // Search supertype
        for (Map.Entry<Class<?>, Function<Object, String>> entry : TO_STRING_CONVERTERS.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                try {
                    return entry.getValue().apply(value);
                } catch (Exception e) {
                    log.warn("Error converting {} to String: {}", type.getSimpleName(), e.getMessage());
                    return defaultValue;
                }
            }
        }
        
        // Use default toString if no converter found
        return value.toString();
    }
    
    /**
     * Convert string to specified type
     * 
     * @param <T> Target type
     * @param str String to convert
     * @param targetType Target type class
     * @param defaultValue Default value (on conversion failure)
     * @return Converted object
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromString(String str, Class<T> targetType, T defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        
        // Search registered converter
        Function<String, Object> converter = FROM_STRING_CONVERTERS.get(targetType);
        if (converter != null) {
            try {
                return (T) converter.apply(str);
            } catch (Exception e) {
                log.warn("Error converting String to {}: {}", targetType.getSimpleName(), e.getMessage());
                return defaultValue;
            }
        }
        
        // Return directly if target type is String
        if (targetType == String.class) {
            return (T) str;
        }
        
        // Return default value on conversion failure
        log.warn("No converter found for type: {}", targetType.getSimpleName());
        return defaultValue;
    }
    
    /**
     * Clear all converters
     */
    public static void clear() {
        TO_STRING_CONVERTERS.clear();
        FROM_STRING_CONVERTERS.clear();
    }
} 