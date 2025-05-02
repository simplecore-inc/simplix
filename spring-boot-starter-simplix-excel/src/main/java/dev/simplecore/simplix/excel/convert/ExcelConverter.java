/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.convert;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Excel Data Converter
 * Handles conversion of entity objects to Excel compatible data format
 */
@Slf4j
public final class ExcelConverter {
    
    private static int batchSize = 1000;
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<String>> COLUMN_CACHE = new ConcurrentHashMap<>();
    
    private ExcelConverter() {
        // Prevent instantiation
    }
    
    /**
     * Configure converter with properties
     */
    public static void configure(SimplixExcelProperties properties) {
        batchSize = properties.getExport().getPageSize();
        
        if (!properties.getCache().isFieldCacheEnabled()) {
            FIELD_CACHE.clear();
        }
        if (!properties.getCache().isColumnCacheEnabled()) {
            COLUMN_CACHE.clear();
        }
        
        log.info("Excel converter configured with batch size: {}, field cache: {}, column cache: {}", 
                batchSize, 
                properties.getCache().isFieldCacheEnabled(),
                properties.getCache().isColumnCacheEnabled());
    }
    
    /**
     * Extract Excel column information from class using @ExcelColumn annotations
     * 
     * @param entityClass Class to extract column information from
     * @return List of column titles in order
     */
    public static List<String> extractColumnTitles(Class<?> entityClass) {
        return COLUMN_CACHE.computeIfAbsent(entityClass, clazz -> 
            getExportFields(clazz).stream()
                .map(field -> field.getAnnotation(ExcelColumn.class).name())
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Convert entity list to row data for Excel export with batching
     * 
     * @param items List of entities
     * @param entityClass Entity class type
     * @return List of row data
     */
    public static List<List<String>> convertToRowData(Collection<?> items, Class<?> entityClass) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Field> exportFields = getExportFields(entityClass);
        List<List<String>> allRows = new ArrayList<>();
        List<Object> batchItems = new ArrayList<>();
        
        for (Object item : items) {
            batchItems.add(item);
            
            if (batchItems.size() >= batchSize) {
                processBatch(batchItems, exportFields, allRows);
                batchItems.clear();
            }
        }
        
        if (!batchItems.isEmpty()) {
            processBatch(batchItems, exportFields, allRows);
        }
        
        return allRows;
    }
    
    /**
     * Process a batch of items for conversion
     * 
     * @param batchItems Batch of items to process
     * @param exportFields Export fields
     * @param allRows All rows collection to add to
     */
    private static void processBatch(List<Object> batchItems, List<Field> exportFields, List<List<String>> allRows) {
        batchItems.stream()
            .map(item -> convertItemToRow(item, exportFields))
            .forEach(allRows::add);
    }
    
    /**
     * Convert single item to row data
     * 
     * @param item Item to convert
     * @param exportFields Export fields
     * @return Row data
     */
    private static List<String> convertItemToRow(Object item, List<Field> exportFields) {
        return exportFields.stream()
            .map(field -> extractFieldValue(field, item))
            .collect(Collectors.toList());
    }
    
    /**
     * Extract field value from item
     * 
     * @param field Field to extract
     * @param item Item to extract from
     * @return Formatted field value
     */
    private static String extractFieldValue(Field field, Object item) {
        try {
            field.setAccessible(true);
            Object value = field.get(item);
            
            if (value == null) {
                return "";
            }
            
            return value.toString();
        } catch (IllegalAccessException e) {
            log.error("Error accessing field: {}", field.getName(), e);
            return "";
        }
    }
    
    /**
     * Get export fields from class
     * 
     * @param entityClass Entity class
     * @return List of export fields
     */
    private static List<Field> getExportFields(Class<?> entityClass) {
        return FIELD_CACHE.computeIfAbsent(entityClass, clazz -> 
            Arrays.stream(clazz.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
                .sorted(Comparator.comparingInt(field -> 
                    field.getAnnotation(ExcelColumn.class).order()))
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Prepare data model for JXLS template
     * 
     * @param items List of entities
     * @param entityClass Entity class type
     * @return Data model map
     */
    public static Map<String, Object> prepareDataModel(Collection<?> items, Class<?> entityClass) {
        Map<String, Object> model = new HashMap<>();
        model.put("headers", extractColumnTitles(entityClass));
        model.put("rows", convertToRowData(items, entityClass));
        return model;
    }
    
    /**
     * Clear caches
     */
    public static void clearCaches() {
        FIELD_CACHE.clear();
        COLUMN_CACHE.clear();
        log.info("Excel converter caches cleared");
    }
} 