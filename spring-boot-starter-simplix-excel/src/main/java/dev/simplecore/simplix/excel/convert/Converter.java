/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

/**
 * Generic converter interface for Excel value conversion
 *
 * @param <T> Type of value to convert
 */
public interface Converter<T> {
    
    /**
     * Convert string to target type
     *
     * @param value String value to convert
     * @param targetType Target type class
     * @return Converted value
     */
    T fromString(String value, Class<?> targetType);
    
    /**
     * Convert value to string
     *
     * @param value Value to convert
     * @param pattern Format pattern (optional)
     * @return String representation
     */
    String toString(T value, String pattern);
} 