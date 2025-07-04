package dev.simplecore.simplix.core.util;

import dev.simplecore.simplix.core.annotation.I18nTitle;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling I18nTitle annotations
 */
public class I18nTitleUtils {
    
    /**
     * Get the title for a specific language from I18nTitle annotation
     * 
     * @param field the field with I18nTitle annotation
     * @param language the language code (e.g., "ko", "en", "ja")
     * @return the title for the specified language, or null if not found
     */
    public static String getTitle(Field field, String language) {
        I18nTitle annotation = field.getAnnotation(I18nTitle.class);
        if (annotation == null) {
            return null;
        }
        
        return parseTitle(annotation.value(), language);
    }
    
    /**
     * Get the title for a specific language from I18nTitle annotation on a class
     * 
     * @param clazz the class with I18nTitle annotation
     * @param language the language code (e.g., "ko", "en", "ja")
     * @return the title for the specified language, or null if not found
     */
    public static String getTitle(Class<?> clazz, String language) {
        I18nTitle annotation = clazz.getAnnotation(I18nTitle.class);
        if (annotation == null) {
            return null;
        }
        
        return parseTitle(annotation.value(), language);
    }
    
    /**
     * Get all titles from I18nTitle annotation as a map
     * 
     * @param field the field with I18nTitle annotation
     * @return map of language codes to titles
     */
    public static Map<String, String> getAllTitles(Field field) {
        I18nTitle annotation = field.getAnnotation(I18nTitle.class);
        if (annotation == null) {
            return new HashMap<>();
        }
        
        return parseAllTitles(annotation.value());
    }
    
    /**
     * Get all titles from I18nTitle annotation on a class as a map
     * 
     * @param clazz the class with I18nTitle annotation
     * @return map of language codes to titles
     */
    public static Map<String, String> getAllTitles(Class<?> clazz) {
        I18nTitle annotation = clazz.getAnnotation(I18nTitle.class);
        if (annotation == null) {
            return new HashMap<>();
        }
        
        return parseAllTitles(annotation.value());
    }
    
    /**
     * Parse title from language-value pairs
     * 
     * @param values array of "language=value" pairs
     * @param language the target language
     * @return the title for the specified language, or null if not found
     */
    private static String parseTitle(String[] values, String language) {
        for (String value : values) {
            String[] parts = value.split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(language)) {
                return parts[1].trim();
            }
        }
        return null;
    }
    
    /**
     * Parse all titles from language-value pairs
     * 
     * @param values array of "language=value" pairs
     * @return map of language codes to titles
     */
    private static Map<String, String> parseAllTitles(String[] values) {
        Map<String, String> titles = new HashMap<>();
        for (String value : values) {
            String[] parts = value.split("=", 2);
            if (parts.length == 2) {
                titles.put(parts[0].trim(), parts[1].trim());
            }
        }
        return titles;
    }
} 