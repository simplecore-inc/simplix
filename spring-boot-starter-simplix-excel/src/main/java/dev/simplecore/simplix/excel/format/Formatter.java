/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

/**
 * Value formatter interface
 * Defines contract for formatting values of specific types
 *
 * @param <T> Type of value to format
 */
public interface Formatter<T> {
    
    /**
     * Format value using specified pattern
     *
     * @param value Value to format
     * @param pattern Format pattern
     * @return Formatted string
     */
    String format(T value, String pattern);
    
    /**
     * Parse string value to target type
     *
     * @param value String value to parse
     * @param pattern Format pattern
     * @return Parsed value
     */
    T parse(String value, String pattern);
    
    /**
     * Get default pattern for this formatter
     *
     * @return Default pattern string
     */
    String getDefaultPattern();
} 