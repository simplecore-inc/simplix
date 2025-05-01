/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.util;

/**
 * String Utility
 * Utility class to remove dependency on Spring's StringUtils.
 */
public final class StringUtil {

    private StringUtil() {
        // Prevent utility class instantiation
    }
    
    /**
     * Check if string has text
     * 
     * @param str String to check
     * @return true if not empty
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