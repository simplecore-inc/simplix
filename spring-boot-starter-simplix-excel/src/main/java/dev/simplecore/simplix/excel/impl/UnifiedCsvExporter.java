/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.api.CsvExporter;
import dev.simplecore.simplix.excel.convert.TypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unified CSV Export Implementation
 * Supports both streaming and standard modes.
 * 
 * @param <T> Type of data to export
 */
@Slf4j
public class UnifiedCsvExporter<T> implements CsvExporter<T> {
    
    /**
     * CSV Export Encoding Options
     */
    public enum Encoding {
        UTF8("UTF-8", StandardCharsets.UTF_8, false),
        UTF8_BOM("UTF-8", StandardCharsets.UTF_8, true),
        ISO_8859_1("ISO-8859-1", StandardCharsets.ISO_8859_1, false),
        UTF16_LE("UTF-16LE", StandardCharsets.UTF_16LE, true),
        UTF16_BE("UTF-16BE", StandardCharsets.UTF_16BE, true);
        
        private final String name;
        private final Charset charset;
        private final boolean includeBom;
        
        Encoding(String name, Charset charset, boolean includeBom) {
            this.name = name;
            this.charset = charset;
            this.includeBom = includeBom;
        }
        
        public String getName() {
            return name;
        }
        
        public Charset getCharset() {
            return charset;
        }
        
        public boolean isIncludeBom() {
            return includeBom;
        }
    }
    
    // Default settings
    private static final int DEFAULT_PAGE_SIZE = 1000;
    
    // Class and field information
    private final Class<T> dataClass;
    private List<Field> exportFields;
    
    // Output settings
    private String filename;
    private String delimiter;
    private String dateFormat;
    private String numberFormat;
    private boolean includeHeader;
    private boolean quoteStrings;
    private String lineEnding;
    private Encoding encoding;
    private boolean escapeSpecialChars;
    
    // Streaming settings
    private boolean useStreaming;
    private int batchSize;
    private Function<PageRequest, Page<T>> dataProvider;
    
    /**
     * Creates CSV exporter for specified DTO class
     * 
     * @param dataClass Data class to export
     */
    public UnifiedCsvExporter(Class<T> dataClass) {
        this.dataClass = dataClass;
        
        // Default settings
        this.filename = "export.csv";
        this.delimiter = ",";
        this.dateFormat = "yyyy-MM-dd";
        this.numberFormat = "#,##0.###";
        this.includeHeader = true;
        this.quoteStrings = true;
        this.lineEnding = "\r\n";
        this.encoding = Encoding.UTF8;
        this.escapeSpecialChars = true;
        this.useStreaming = false;
        this.batchSize = DEFAULT_PAGE_SIZE;
        
        // Extract export fields (fields with @ExcelColumn annotation)
        this.exportFields = Arrays.stream(dataClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
                .sorted(Comparator.comparingInt(field -> 
                    field.getAnnotation(ExcelColumn.class).order()))
                .collect(Collectors.toList());
    }
    
    @Override
    public UnifiedCsvExporter<T> filename(String filename) {
        this.filename = filename;
        return this;
    }
    
    @Override
    public UnifiedCsvExporter<T> delimiter(String delimiter) {
        this.delimiter = delimiter;
        return this;
    }
    
    /**
     * Set date format pattern
     */
    public UnifiedCsvExporter<T> dateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
        return this;
    }
    
    /**
     * Set number format pattern
     */
    public UnifiedCsvExporter<T> numberFormat(String numberFormat) {
        this.numberFormat = numberFormat;
        return this;
    }
    
    @Override
    public UnifiedCsvExporter<T> includeHeader(boolean includeHeader) {
        this.includeHeader = includeHeader;
        return this;
    }
    
    @Override
    public UnifiedCsvExporter<T> quoteStrings(boolean quoteStrings) {
        this.quoteStrings = quoteStrings;
        return this;
    }
    
    /**
     * Set line ending character (default: \r\n)
     */
    public UnifiedCsvExporter<T> lineEnding(String lineEnding) {
        this.lineEnding = lineEnding;
        return this;
    }
    
    @Override
    public UnifiedCsvExporter<T> streaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
        return this;
    }
    
    @Override
    public UnifiedCsvExporter<T> encoding(String encoding) {
        try {
            this.encoding = Encoding.valueOf(encoding.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid encoding: {}. Using UTF8.", encoding);
            this.encoding = Encoding.UTF8;
        }
        return this;
    }
    
    /**
     * Set encoding using Encoding enum
     */
    public UnifiedCsvExporter<T> encoding(Encoding encoding) {
        this.encoding = encoding;
        return this;
    }
    
    /**
     * Set special characters escape option
     */
    public UnifiedCsvExporter<T> escapeSpecialChars(boolean escapeSpecialChars) {
        this.escapeSpecialChars = escapeSpecialChars;
        return this;
    }
    
    /**
     * Set batch size (for streaming mode)
     */
    public UnifiedCsvExporter<T> batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }
    
    /**
     * Set data provider (for streaming mode)
     */
    public UnifiedCsvExporter<T> dataProvider(Function<PageRequest, Page<T>> dataProvider) {
        this.dataProvider = dataProvider;
        return this;
    }
    
    @Override
    public void export(Collection<T> items, HttpServletResponse response) throws IOException {
        Instant start = Instant.now();
        int totalItems = items != null ? items.size() : 0;
        
        log.info("Starting CSV export of {} items of type {}", 
                totalItems, dataClass.getSimpleName());
        
        configureResponse(response);
        
        try (OutputStream os = response.getOutputStream()) {
            // Write BOM if needed
            if (encoding.isIncludeBom()) {
                writeBom(os);
            }
            
            // Distinguish between streaming and standard modes
            if (useStreaming && dataProvider != null) {
                try (Writer writer = new OutputStreamWriter(os, encoding.getCharset())) {
                    exportWithPaging(writer);
                }
            } else {
                try (Writer writer = new BufferedWriter(new OutputStreamWriter(os, encoding.getCharset()))) {
                    exportCollection(items, writer);
                }
            }
            
            Instant end = Instant.now();
            log.info("Completed CSV export in {} seconds", 
                    Duration.between(start, end).getSeconds());
        }
    }
    
    @Override
    public void export(Collection<T> items, OutputStream outputStream) throws IOException {
        Instant start = Instant.now();
        int totalItems = items != null ? items.size() : 0;
        
        log.info("Starting CSV export of {} items of type {} to OutputStream", 
                totalItems, dataClass.getSimpleName());
        
        // Write BOM if needed
        if (encoding.isIncludeBom()) {
            writeBom(outputStream);
        }
        
        // Distinguish between streaming and standard modes
        if (useStreaming && dataProvider != null) {
            try (Writer writer = new OutputStreamWriter(outputStream, encoding.getCharset())) {
                exportWithPaging(writer);
            }
        } else {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, encoding.getCharset()))) {
                exportCollection(items, writer);
            }
        }
        
        Instant end = Instant.now();
        log.info("Completed CSV export in {} seconds", 
                Duration.between(start, end).getSeconds());
    }
    
    /**
     * Export collection data to CSV
     */
    private void exportCollection(Collection<T> items, Writer writer) throws IOException {
        // Write header if needed
        if (includeHeader) {
            writeHeader(writer);
        }
        
        // Exit if no data
        if (items == null || items.isEmpty()) {
            writer.flush();
            return;
        }
        
        // Use batch processing if needed
        if (items.size() > batchSize) {
            exportInBatches(items, writer);
        } else {
            // Write all data rows
            for (T item : items) {
                writeRow(writer, item);
            }
        }
        
        writer.flush();
    }
    
    /**
     * Export streaming using paging
     */
    private void exportWithPaging(Writer writer) throws IOException {
        if (dataProvider == null) {
            throw new IllegalStateException("Data provider function must be set for streaming mode");
        }
        
        // Write header if needed
        if (includeHeader) {
            writeHeader(writer);
        }
        
        int rowCount = 0;
        int pageNum = 0;
        Page<T> page;
        
        do {
            PageRequest pageRequest = PageRequest.of(pageNum, batchSize);
            page = dataProvider.apply(pageRequest);
            
            for (T item : page.getContent()) {
                writeRow(writer, item);
                rowCount++;
            }
            
            log.debug("Processed page {} of {}, total rows: {}", 
                    pageNum + 1, page.getTotalPages(), rowCount);
            
            // Flush periodically to minimize memory usage
            writer.flush();
            pageNum++;
        } while (page.hasNext());
        
        log.info("Total rows exported: {}", rowCount);
    }
    
    /**
     * Export data in batches
     */
    private void exportInBatches(Collection<T> items, Writer writer) throws IOException {
        final int totalItems = items.size();
        final AtomicInteger processed = new AtomicInteger(0);
        
        List<T> batch = new ArrayList<>(batchSize);
        Iterator<T> iterator = items.iterator();
        
        while (iterator.hasNext()) {
            batch.clear();
            
            // Fill batch
            int count = 0;
            while (iterator.hasNext() && count < batchSize) {
                batch.add(iterator.next());
                count++;
            }
            
            // Process batch
            for (T item : batch) {
                writeRow(writer, item);
            }
            
            int currentProcessed = processed.addAndGet(batch.size());
            log.debug("Processed {}/{} items ({}%)", 
                    currentProcessed, totalItems, (currentProcessed * 100 / totalItems));
            
            // Flush periodically to minimize memory usage
            writer.flush();
        }
    }
    
    /**
     * Write BOM (Byte Order Mark)
     */
    private void writeBom(OutputStream os) throws IOException {
        if (encoding == Encoding.UTF8_BOM) {
            // UTF-8 BOM
            os.write(0xEF);
            os.write(0xBB);
            os.write(0xBF);
        } else if (encoding == Encoding.UTF16_LE) {
            // UTF-16 LE BOM
            os.write(0xFF);
            os.write(0xFE);
        } else if (encoding == Encoding.UTF16_BE) {
            // UTF-16 BE BOM
            os.write(0xFE);
            os.write(0xFF);
        }
    }
    
    /**
     * Configure HTTP response headers
     */
    private void configureResponse(HttpServletResponse response) {
        response.setContentType("text/csv; charset=" + encoding.getName());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Write header row
     */
    private void writeHeader(Writer writer) throws IOException {
        String headerLine = exportFields.stream()
                .map(field -> {
                    ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                    String title = column.title();
                    return quoteStrings ? quote(title) : title;
                })
                .collect(Collectors.joining(delimiter));
        
        writer.write(headerLine);
        writer.write(lineEnding);
    }
    
    /**
     * Write data row
     */
    private void writeRow(Writer writer, T item) throws IOException {
        String dataLine = exportFields.stream()
                .map(field -> {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(item);
                        ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                        
                        if (value == null) {
                            return "";
                        }
                        
                        String formattedValue = formatValue(value, column);
                        // Quote if string or contains delimiter/newline
                        return shouldQuote(value, formattedValue) ? 
                                quote(escapeSpecialChars ? escapeSpecialCharacters(formattedValue) : formattedValue) : 
                                formattedValue;
                    } catch (IllegalAccessException e) {
                        log.error("Error accessing field: " + field.getName(), e);
                        return "";
                    }
                })
                .collect(Collectors.joining(delimiter));
        
        writer.write(dataLine);
        writer.write(lineEnding);
    }
    
    /**
     * Determine if value should be quoted
     */
    private boolean shouldQuote(Object value, String stringValue) {
        if (quoteStrings && value instanceof String) {
            return true;
        }
        
        // Always quote if contains delimiter, quote, or newline characters
        return stringValue.contains(delimiter) || 
               stringValue.contains("\"") || 
               stringValue.contains("\r") || 
               stringValue.contains("\n");
    }
    
    /**
     * Format value based on field type and annotation
     */
    private String formatValue(Object value, ExcelColumn column) {
        // Use class-level format patterns with fallback to annotation values
        String pattern = value instanceof Number ? 
            (numberFormat != null ? numberFormat : column.numberFormat()) :
            (dateFormat != null ? dateFormat : column.dateFormat());
        
        if (value instanceof Date) {
            return TypeConverter.formatDate((Date) value, pattern);
        } else if (value instanceof Calendar) {
            return TypeConverter.formatDate(((Calendar) value).getTime(), pattern);
        } else if (value instanceof Number) {
            return TypeConverter.formatNumber((Number) value, pattern);
        } else {
            return TypeConverter.toString(value, pattern);
        }
    }
    
    /**
     * Escape special characters for CSV
     */
    private String escapeSpecialCharacters(String value) {
        // Escape quotes by doubling them
        String escaped = value.replace("\"", "\"\"");
        
        // Remove control characters
        escaped = escaped.replaceAll("[\\p{Cntrl}]", " ");
        
        return escaped;
    }
    
    /**
     * Quote string value
     */
    private String quote(String value) {
        return "\"" + value + "\"";
    }
} 