/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.api.CsvExporter;
import dev.simplecore.simplix.excel.convert.TypeConverter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Unified CSV Export Implementation
 * Supports both streaming and standard modes.
 * 
 * @param <T> Type of data to export
 */
@Slf4j
public class UnifiedCsvExporter<T> extends AbstractExporter<T> implements CsvExporter<T> {
    
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
    
    // CSV format settings
    private String delimiter;
    private String dateFormat;
    private String numberFormat;
    private boolean includeHeader;
    private boolean quoteStrings;
    private String lineEnding;
    private Encoding encoding;
    
    // Batch processing
    private int batchSize;
    private Function<PageRequest, Page<T>> dataProvider;
    
    /**
     * Creates CSV exporter for specified DTO class
     * 
     * @param dataClass Data class to export
     */
    public UnifiedCsvExporter(Class<T> dataClass) {
        super(dataClass);
        
        // Default settings
        this.filename = "export.csv";
        this.delimiter = ",";
        this.dateFormat = "yyyy-MM-dd";
        this.numberFormat = "#,##0.###";
        this.includeHeader = true;
        this.quoteStrings = true;
        this.lineEnding = "\r\n";
        this.encoding = Encoding.UTF8;
        this.useStreaming = false;
        this.batchSize = DEFAULT_PAGE_SIZE;
    }
    
    @Override
    public UnifiedCsvExporter<T> filename(String filename) {
        super.filename(filename);
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
        super.streaming(useStreaming);
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
        
        logExportStart(items, "Starting CSV export of {} items of type {}");
        configureCsvResponse(response, encoding.getName());
        
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
            
            logExportCompletion(start);
        }
    }
    
    @Override
    public void export(Collection<T> items, OutputStream outputStream) throws IOException {
        Instant start = Instant.now();
        
        logExportStart(items, "Starting CSV export of {} items of type {} to OutputStream");
        
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
        
        logExportCompletion(start);
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
                writeRow(item, writer);
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
                writeRow(item, writer);
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
        processBatches(items, batchSize, batch -> {
            for (T item : batch) {
                writeRow(item, writer);
            }
            writer.flush();
        });
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
     * Write header row
     */
    private void writeHeader(Writer writer) throws IOException {
        String headerLine = exportFields.stream()
                .map(field -> {
                    String title = field.getAnnotation(ExcelColumn.class).name();
                    return quoteStrings ? "\"" + title + "\"" : title;
                })
                .collect(Collectors.joining(delimiter));
        
        writer.write(headerLine);
        writer.write(lineEnding);
    }
    
    /**
     * Write data row
     */
    private void writeRow(T item, Writer writer) throws IOException {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Field field : exportFields) {
            try {
                field.setAccessible(true);
                Object value = field.get(item);
                ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                String cellValue = formatValue(value, column);
                
                if (value != null && quoteStrings && (value instanceof String || 
                    value instanceof LocalDate || value instanceof LocalDateTime || value instanceof LocalTime ||
                    value instanceof OffsetDateTime || value instanceof OffsetTime || value instanceof ZonedDateTime ||
                    value instanceof Instant || value instanceof Year || value instanceof YearMonth || 
                    value instanceof MonthDay || value instanceof Duration || value instanceof Period)) {
                    cellValue = "\"" + cellValue + "\"";
                }
                
                joiner.add(cellValue != null ? cellValue : "");
            } catch (IllegalAccessException e) {
                log.error("Failed to access field: " + field.getName(), e);
                joiner.add("");
            }
        }
        writer.write(joiner.toString());
        writer.write(lineEnding);
    }
    
    /**
     * Format value based on field type and annotation
     */
    private String formatValue(Object value, ExcelColumn column) {
        if (value == null) {
            return "";
        }
        
        // Use simplified conversion logic
        if (value instanceof Date) {
            return TypeConverter.formatDate((Date) value, dateFormat);
        } else if (value instanceof Calendar) {
            return TypeConverter.formatDate(((Calendar) value).getTime(), dateFormat);
        } else if (value instanceof Number) {
            return TypeConverter.formatNumber((Number) value, numberFormat);
        } else if (value instanceof Enum<?>) {
            // Check if implements SimpliXLabeledEnum
            if (value instanceof dev.simplecore.simplix.core.enums.SimpliXLabeledEnum) {
                return ((dev.simplecore.simplix.core.enums.SimpliXLabeledEnum) value).getLabel();
            } else {
                return ((Enum<?>) value).name();
            }
        } else {
            return TypeConverter.toString(value, "");
        }
    }
    
    /**
     * Quote string value
     */
    private String quote(String value) {
        return "\"" + value + "\"";
    }

    protected void setResponseHeaders(HttpServletResponse response, String filename) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", String.format("attachment; filename=%s", filename));
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    protected void writeCsvContent(Collection<?> data, Writer writer) throws IOException {
        if (data == null || data.isEmpty()) {
            return;
        }
        
        // Get field information from the first item
        Object firstItem = data.iterator().next();
        List<Field> fields = getExportableFields(firstItem.getClass());
        
        // Write header
        writeHeader(fields, writer);
        
        // Write data rows
        for (Object item : data) {
            writeRow(item, fields, writer);
        }
        
        writer.flush();
    }
    
    private void writeHeader(List<Field> fields, Writer writer) throws IOException {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Field field : fields) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            String header = annotation != null ? annotation.name() : field.getName();
            joiner.add(quoteIfNeeded(header));
        }
        writer.write(joiner.toString());
        writer.write("\n");
    }
    
    private void writeRow(Object item, List<Field> fields, Writer writer) throws IOException {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(item);
                ExcelColumn column = field.getAnnotation(ExcelColumn.class);
                String cellValue = formatValue(value, column);
                joiner.add(quoteStrings && value instanceof String ? quote(cellValue) : cellValue);
            } catch (IllegalAccessException e) {
                log.error("Failed to access field: " + field.getName(), e);
                joiner.add("");
            }
        }
        writer.write(joiner.toString());
        writer.write(lineEnding);
    }
    
    private String quoteIfNeeded(String value) {
        if (value == null) {
            return "";
        }
        if (quoteStrings && (value.contains(delimiter) || value.contains("\"") || value.contains("\n"))) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    protected List<Field> getExportableFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isAnnotationPresent(ExcelColumn.class) || 
                           !field.getAnnotation(ExcelColumn.class).ignore())
            .sorted(Comparator.comparingInt(field -> {
                ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
                return annotation != null ? annotation.order() : Integer.MAX_VALUE;
            }))
            .collect(Collectors.toList());
    }
    
    protected String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof Date) {
            return new SimpleDateFormat(dateFormat).format(value);
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ofPattern(dateFormat));
        } else if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else if (value instanceof LocalTime) {
            return ((LocalTime) value).format(DateTimeFormatter.ISO_LOCAL_TIME);
        } else if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } else if (value instanceof OffsetTime) {
            return ((OffsetTime) value).format(DateTimeFormatter.ISO_OFFSET_TIME);
        } else if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        } else if (value instanceof Instant) {
            return ((Instant) value).toString();
        } else if (value instanceof Year) {
            return ((Year) value).toString();
        } else if (value instanceof YearMonth) {
            return ((YearMonth) value).toString();
        } else if (value instanceof MonthDay) {
            return ((MonthDay) value).toString();
        } else if (value instanceof Duration) {
            return ((Duration) value).toString();
        } else if (value instanceof Period) {
            return ((Period) value).toString();
        } else if (value instanceof Number) {
            return new DecimalFormat(numberFormat).format(value);
        } else if (value instanceof Boolean) {
            return value.toString();
        }
        
        return value.toString();
    }
} 