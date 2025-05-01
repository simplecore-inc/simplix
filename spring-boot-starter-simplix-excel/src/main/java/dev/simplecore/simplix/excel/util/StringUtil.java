/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.util;

/**
 * Utility class for string operations.
 */
public class StringUtil {
    
    private StringUtil() {
        // Prevent instantiation
    }
    
    /**
     * Check if a string has text (not null and not empty after trimming).
     *
     * @param str the string to check
     * @return true if the string has text, false otherwise
     */
    public static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * Check if string is empty
     * 
     * @param str String to check
     * @return true if empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
    
    /**
     * Check if string is blank (including trim)
     * 
     * @param str String to check
     * @return true if blank
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Return default value if string is empty
     * 
     * @param str String
     * @param defaultValue Default value
     * @return Original string if not empty, default value otherwise
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }
} 