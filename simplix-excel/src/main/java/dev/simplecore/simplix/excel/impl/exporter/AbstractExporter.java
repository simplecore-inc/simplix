/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Abstract base class for exporters with common functionality
 * 
 * @param <T> Type of data to export
 */
@Slf4j
public abstract class AbstractExporter<T> {
    
    protected final Class<T> dataClass;
    protected List<Field> exportFields;
    protected String filename;
    protected boolean useStreaming;
    
    /**
     * Creates an exporter for the specified data class
     *
     * @param dataClass Data class to export
     */
    protected AbstractExporter(Class<T> dataClass) {
        this.dataClass = dataClass;
        
        // Extract export fields (fields with @ExcelColumn annotation and not ignored)
        this.exportFields = Arrays.stream(dataClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
                .filter(field -> !field.getAnnotation(ExcelColumn.class).ignore())
                .sorted(Comparator.comparingInt(field -> 
                    field.getAnnotation(ExcelColumn.class).order()))
                .collect(Collectors.toList());
    }
    
    /**
     * Set file name
     * 
     * @param filename File name
     * @return this instance (for method chaining)
     */
    public AbstractExporter<T> filename(String filename) {
        this.filename = filename;
        return this;
    }
    
    /**
     * Set streaming mode
     * 
     * @param useStreaming Whether to enable streaming mode
     * @return this instance (for method chaining)
     */
    public AbstractExporter<T> streaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
        return this;
    }
    
    /**
     * Configure HTTP response headers for Excel files
     */
    protected void configureExcelResponse(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Configure HTTP response headers for CSV files
     */
    protected void configureCsvResponse(HttpServletResponse response, String charset) {
        response.setContentType("text/csv; charset=" + charset);
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Log export start
     */
    protected void logExportStart(Collection<T> items, String format) {
        int totalItems = items != null ? items.size() : 0;
        log.info(format, totalItems, dataClass.getSimpleName());
    }
    
    /**
     * Log export completion
     */
    protected void logExportCompletion(Instant start) {
        Instant end = Instant.now();
        log.info("Completed export in {} seconds", 
                Duration.between(start, end).getSeconds());
    }
    
    /**
     * Process data in batches
     * 
     * @param items Collection of items to process
     * @param batchSize Size of each batch
     * @param batchProcessor Function to process each batch
     * @throws IOException If an I/O error occurs
     */
    protected void processBatches(Collection<T> items, int batchSize, 
            BatchProcessor<T> batchProcessor) throws IOException {
        
        if (items == null || items.isEmpty()) {
            return;
        }
        
        int totalItems = items.size();
        List<T> batch = new ArrayList<>(batchSize);
        java.util.Iterator<T> iterator = items.iterator();
        int processed = 0;
        
        while (iterator.hasNext()) {
            batch.clear();
            
            // Fill batch
            for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                batch.add(iterator.next());
            }
            
            // Process batch
            batchProcessor.processBatch(batch);
            
            processed += batch.size();
            log.debug("Processed {}/{} items ({}%)", 
                    processed, totalItems, (processed * 100 / totalItems));
        }
    }
    
    /**
     * Functional interface for batch processing
     */
    @FunctionalInterface
    public interface BatchProcessor<T> {
        void processBatch(List<T> batch) throws IOException;
    }
} 