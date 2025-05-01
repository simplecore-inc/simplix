/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base formatter implementation
 * Provides common functionality for all formatters
 *
 * @param <T> Type of value to format
 */
@Slf4j
public abstract class AbstractFormatter<T> implements Formatter<T> {
    
    private final String defaultPattern;
    
    protected AbstractFormatter(String defaultPattern) {
        this.defaultPattern = defaultPattern;
    }
    
    @Override
    public String format(T value, String pattern) {
        if (value == null) {
            return "";
        }
        
        pattern = normalizePattern(pattern);
        
        try {
            return doFormat(value, pattern);
        } catch (Exception e) {
            handleFormatError(value, pattern, e);
            return value.toString();
        }
    }
    
    @Override
    public T parse(String value, String pattern) {
        if (isNullOrEmpty(value)) {
            return null;
        }
        
        pattern = normalizePattern(pattern);
        
        try {
            return doParse(value.trim(), pattern);
        } catch (Exception e) {
            handleParseError(value, pattern, e);
            return null;
        }
    }
    
    @Override
    public String getDefaultPattern() {
        return defaultPattern;
    }
    
    /**
     * Perform actual formatting
     *
     * @param value Value to format
     * @param pattern Normalized pattern
     * @return Formatted string
     * @throws Exception if formatting fails
     */
    protected abstract String doFormat(T value, String pattern) throws Exception;
    
    /**
     * Perform actual parsing
     *
     * @param value String to parse
     * @param pattern Normalized pattern
     * @return Parsed value
     * @throws Exception if parsing fails
     */
    protected abstract T doParse(String value, String pattern) throws Exception;
    
    /**
     * Handle format error
     *
     * @param value Value that failed to format
     * @param pattern Pattern that was used
     * @param error Exception that occurred
     */
    protected void handleFormatError(T value, String pattern, Exception error) {
        log.warn("Error formatting {} value with pattern {}: {}", 
                value.getClass().getSimpleName(), pattern, error.getMessage());
    }
    
    /**
     * Handle parse error
     *
     * @param value String that failed to parse
     * @param pattern Pattern that was used
     * @param error Exception that occurred
     */
    protected void handleParseError(String value, String pattern, Exception error) {
        log.warn("Error parsing '{}' with pattern {}: {}", 
                value, pattern, error.getMessage());
    }
    
    /**
     * Normalize pattern string
     *
     * @param pattern Pattern to normalize
     * @return Normalized pattern
     */
    protected String normalizePattern(String pattern) {
        return pattern != null && !pattern.isEmpty() ? pattern : defaultPattern;
    }
    
    /**
     * Check if string is null or empty
     *
     * @param value String to check
     * @return true if string is null or empty
     */
    protected boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
} 