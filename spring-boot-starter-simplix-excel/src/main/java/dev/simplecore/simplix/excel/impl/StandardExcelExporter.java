/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.api.ExcelExporter;
import dev.simplecore.simplix.excel.convert.TypeConverter;
import dev.simplecore.simplix.excel.style.ExcelStyleManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Standard Excel Export Implementation
 * Optimized for large data processing with streaming mode support.
 *
 * @param <T> Type of data to export
 */
@Slf4j
public class StandardExcelExporter<T> implements ExcelExporter<T> {

    // Default settings
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final String DEFAULT_SHEET_NAME = "Data";
    
    // Class and field information
    private final Class<T> dataClass;
    private List<Field> exportFields;
    
    // Output settings
    private String filename;
    private String sheetName;
    private int pageSize;
    private int windowSize;
    private boolean autoSizeColumns;
    private boolean useStreaming;
    
    // Streaming settings
    private Function<PageRequest, Page<T>> dataProvider;
    
    /**
     * Creates Excel exporter for specified DTO class
     *
     * @param dataClass Data class to export
     */
    public StandardExcelExporter(Class<T> dataClass) {
        this.dataClass = dataClass;
        
        // Default settings
        this.filename = "export.xlsx";
        this.sheetName = DEFAULT_SHEET_NAME;
        this.pageSize = DEFAULT_PAGE_SIZE;
        this.windowSize = DEFAULT_WINDOW_SIZE;
        this.autoSizeColumns = false;
        this.useStreaming = false;
        
        // Extract export fields (fields with @ExcelColumn annotation and not ignored)
        this.exportFields = Arrays.stream(dataClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
                .filter(field -> !field.getAnnotation(ExcelColumn.class).ignore())
                .sorted(Comparator.comparingInt(field -> 
                    field.getAnnotation(ExcelColumn.class).order()))
                .collect(Collectors.toList());
    }
    
    @Override
    public StandardExcelExporter<T> filename(String filename) {
        this.filename = filename;
        return this;
    }
    
    @Override
    public StandardExcelExporter<T> sheetName(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }
    
    /**
     * Set page size for batch processing
     */
    public StandardExcelExporter<T> pageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }
    
    /**
     * Set POI window size (number of rows to keep in memory)
     */
    public StandardExcelExporter<T> windowSize(int windowSize) {
        this.windowSize = windowSize;
        return this;
    }
    
    @Override
    public StandardExcelExporter<T> autoSizeColumns(boolean autoSizeColumns) {
        this.autoSizeColumns = autoSizeColumns;
        return this;
    }
    
    @Override
    public StandardExcelExporter<T> streaming(boolean useStreaming) {
        this.useStreaming = useStreaming;
        return this;
    }
    
    /**
     * Set data provider (for streaming mode)
     */
    public StandardExcelExporter<T> dataProvider(Function<PageRequest, Page<T>> dataProvider) {
        this.dataProvider = dataProvider;
        return this;
    }
    
    @Override
    public void export(Collection<T> items, HttpServletResponse response) throws IOException {
        try {
            if (useStreaming && dataProvider == null && items != null && items.size() > DEFAULT_PAGE_SIZE) {
                // Use virtual paging from collection if streaming mode but no dataProvider
                exportStreamingFromCollection(items, response);
            } else if (useStreaming && dataProvider != null) {
                // Streaming mode + dataProvider available
                exportStreamingWithDataProvider(response);
            } else {
                // Normal mode
                exportStandard(items, response);
            }
        } catch (IOException e) {
            log.error("Excel export failed: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Excel export: {}", e.getMessage(), e);
            throw new dev.simplecore.simplix.excel.exception.ExcelExportException("Excel export failed", e);
        }
    }
    
    @Override
    public void export(Collection<T> items, OutputStream outputStream) throws IOException {
        if (useStreaming && dataProvider == null && items != null && items.size() > DEFAULT_PAGE_SIZE) {
            // Use virtual paging from collection if streaming mode but no dataProvider
            exportStreamingFromCollection(items, outputStream);
        } else if (useStreaming && dataProvider != null) {
            // Streaming mode + dataProvider available
            exportStreamingWithDataProvider(outputStream);
        } else {
            // Normal mode
            exportStandard(items, outputStream);
        }
    }
    
    /**
     * Export data in normal mode
     */
    private void exportStandard(Collection<T> items, HttpServletResponse response) throws IOException {
        int totalItems = items != null ? items.size() : 0;
        
        log.info("Starting Excel export of {} items of type {}", 
                totalItems, dataClass.getSimpleName());
        
        configureResponse(response);
        
        try (OutputStream os = response.getOutputStream()) {
            exportStandard(items, os);
        }
    }
    
    /**
     * Export data in normal mode
     */
    private void exportStandard(Collection<T> items, OutputStream outputStream) throws IOException {
        Instant start = Instant.now();
        int totalItems = items != null ? items.size() : 0;
        
        log.info("Starting Excel export of {} items of type {}", 
                totalItems, dataClass.getSimpleName());
        
        // Create workbook (non-streaming mode)
        try (Workbook workbook = new SXSSFWorkbook(windowSize)) {
            Sheet sheet = workbook.createSheet(sheetName);
            
            // Create style manager
            ExcelStyleManager styleManager = new ExcelStyleManager(workbook);
            
            // Create header row
            createHeaderRow(sheet, styleManager);
            
            // Create data rows if data exists
            if (items != null && !items.isEmpty()) {
                createDataRows(sheet, items, styleManager);
            }
            
            // Auto-size columns if needed
            if (autoSizeColumns) {
                for (int i = 0; i < exportFields.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }
            
            // Save file
            workbook.write(outputStream);
            
            // Clean up temporary files
            ((SXSSFWorkbook) workbook).dispose();
            
            Instant end = Instant.now();
            log.info("Completed Excel export in {} seconds", 
                    Duration.between(start, end).getSeconds());
        }
    }
    
    /**
     * Export streaming with data provider
     */
    private void exportStreamingWithDataProvider(HttpServletResponse response) throws IOException {
        if (dataProvider == null) {
            throw new IllegalStateException("Data provider function must be set for streaming mode");
        }
        
        log.info("Starting streaming Excel export of {} data", dataClass.getSimpleName());
        
        configureResponse(response);
        
        try (OutputStream os = response.getOutputStream()) {
            exportStreamingWithDataProvider(os);
        }
    }
    
    /**
     * Export streaming with data provider
     */
    private void exportStreamingWithDataProvider(OutputStream outputStream) throws IOException {
        if (dataProvider == null) {
            throw new IllegalStateException("Data provider function must be set for streaming mode");
        }
        
        Instant start = Instant.now();
        log.info("Starting streaming Excel export of {} data", dataClass.getSimpleName());
        
        // Create streaming workbook
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize)) {
            Sheet sheet = workbook.createSheet(sheetName);
            
            // Create style manager
            ExcelStyleManager styleManager = new ExcelStyleManager(workbook);
            
            // Create header row
            createHeaderRow(sheet, styleManager);
            
            // Process data through paging
            processDataInPages(sheet, styleManager);
            
            // Auto-size columns if needed - Note: May cause performance degradation for large datasets
            if (autoSizeColumns) {
                for (int i = 0; i < exportFields.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }
            
            // Save file
            workbook.write(outputStream);
            
            // Clean up temporary files
            workbook.dispose();
            
            Instant end = Instant.now();
            log.info("Completed streaming Excel export in {} seconds", 
                    Duration.between(start, end).getSeconds());
        }
    }
    
    /**
     * Export streaming from collection using virtual paging
     */
    private void exportStreamingFromCollection(Collection<T> items, HttpServletResponse response) throws IOException {
        int totalItems = items != null ? items.size() : 0;
        
        log.info("Starting streaming Excel export from collection with {} items", totalItems);
        
        configureResponse(response);
        
        try (OutputStream os = response.getOutputStream()) {
            exportStreamingFromCollection(items, os);
        }
    }
    
    /**
     * Export streaming from collection using virtual paging
     */
    private void exportStreamingFromCollection(Collection<T> items, OutputStream outputStream) throws IOException {
        if (items == null || items.isEmpty()) {
            // Create empty file if data is empty
            exportStandard(Collections.emptyList(), outputStream);
            return;
        }
        
        Instant start = Instant.now();
        int totalItems = items.size();
        
        log.info("Starting streaming Excel export from collection with {} items", totalItems);
        
        // Create streaming workbook
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(windowSize)) {
            Sheet sheet = workbook.createSheet(sheetName);
            
            // Create style manager
            ExcelStyleManager styleManager = new ExcelStyleManager(workbook);
            
            // Create header row
            createHeaderRow(sheet, styleManager);
            
            // Batch processing (virtual paging)
            int rowIndex = 1; // Start after header
            List<T> batch = new ArrayList<>(pageSize);
            Iterator<T> iterator = items.iterator();
            
            AtomicInteger processed = new AtomicInteger(0);
            
            while (iterator.hasNext()) {
                batch.clear();
                
                // Fill batch
                for (int i = 0; i < pageSize && iterator.hasNext(); i++) {
                    batch.add(iterator.next());
                }
                
                // Process batch
                for (T item : batch) {
                    createDataRow(sheet, rowIndex++, item, styleManager);
                }
                
                int currentProcessed = processed.addAndGet(batch.size());
                log.debug("Processed {}/{} items ({}%)", 
                        currentProcessed, totalItems, (currentProcessed * 100 / totalItems));
            }
            
            // Auto-size columns if needed - Note: May cause performance degradation for large datasets
            if (autoSizeColumns) {
                for (int i = 0; i < exportFields.size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }
            
            // Save file
            workbook.write(outputStream);
            
            // Clean up temporary files
            workbook.dispose();
            
            Instant end = Instant.now();
            log.info("Completed streaming Excel export in {} seconds, total rows: {}", 
                    Duration.between(start, end).getSeconds(), rowIndex - 1);
        }
    }
    
    /**
     * Configure HTTP response headers
     */
    private void configureResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Create header row
     */
    private void createHeaderRow(Sheet sheet, ExcelStyleManager styleManager) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = styleManager.createHeaderStyle();
        
        for (int i = 0; i < exportFields.size(); i++) {
            Field field = exportFields.get(i);
            ExcelColumn column = field.getAnnotation(ExcelColumn.class);
            
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(column.name());
            cell.setCellStyle(headerStyle);
            
            // Set column width
            sheet.setColumnWidth(i, column.width() * 256);
        }
    }
    
    /**
     * Create rows from collection data
     */
    private void createDataRows(Sheet sheet, Collection<T> items, ExcelStyleManager styleManager) {
        int rowIndex = 1; // Start after header
        
        for (T item : items) {
            createDataRow(sheet, rowIndex++, item, styleManager);
        }
    }
    
    /**
     * Process data through paging
     */
    private void processDataInPages(Sheet sheet, ExcelStyleManager styleManager) {
        int rowNum = 1; // Start after header
        int pageNum = 0;
        Page<T> page;
        
        do {
            PageRequest pageRequest = PageRequest.of(pageNum, pageSize);
            page = dataProvider.apply(pageRequest);
            
            for (T item : page.getContent()) {
                createDataRow(sheet, rowNum++, item, styleManager);
            }
            
            log.debug("Processed page {} of {}, total rows: {}", 
                    pageNum + 1, page.getTotalPages(), rowNum - 1);
            
            pageNum++;
        } while (page.hasNext());
        
        log.info("Total rows exported: {}", rowNum - 1);
    }
    
    /**
     * Create data row
     */
    private void createDataRow(Sheet sheet, int rowNum, T item, ExcelStyleManager styleManager) {
        Row row = sheet.createRow(rowNum);
        
        for (int i = 0; i < exportFields.size(); i++) {
            Field field = exportFields.get(i);
            field.setAccessible(true);
            
            Cell cell = row.createCell(i);
            
            try {
                Object value = field.get(item);
                ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                
                setCellValue(cell, value);
                
                // Set style only if value exists
                if (value != null) {
                    CellStyle style = styleManager.createDataStyle(value, column);
                    cell.setCellStyle(style);
                }
                
            } catch (IllegalAccessException e) {
                log.error("Error accessing field: " + field.getName(), e);
                cell.setCellValue("");
            }
        }
    }
    
    /**
     * Set cell value based on type
     */
    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        
        if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof LocalDate) {
            LocalDate localDate = (LocalDate) value;
            cell.setCellValue(localDate);
            CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
            dateStyle.setDataFormat((short) 14); // Excel date format: mm/dd/yyyy
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime) {
            LocalDateTime localDateTime = (LocalDateTime) value;
            cell.setCellValue(localDateTime);
            CellStyle dateTimeStyle = cell.getSheet().getWorkbook().createCellStyle();
            dateTimeStyle.setDataFormat((short) 22); // Excel date/time format: mm/dd/yyyy hh:mm
            cell.setCellStyle(dateTimeStyle);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }
} 