/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.template;

import dev.simplecore.simplix.core.util.UuidUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified Excel Template Manager
 * Central component for template management, generation, loading, and caching
 */
@Slf4j
public final class ExcelTemplateManager {
    
    private static final String DEFAULT_TEMPLATE_PATH = "templates/default-template.xlsx";
    private static final String HEADERS_MARKER = "${headers}";
    private static final String ROWS_MARKER = "${rows}";
    private static final Map<String, byte[]> TEMPLATE_CACHE = new ConcurrentHashMap<>();
    
    private ExcelTemplateManager() {
        // Prevent instantiation
    }
    
    /**
     * Get template resource
     * Creates default template if it doesn't exist
     *
     * @param templatePath Template path (optional)
     * @return Template resource
     * @throws IOException If template loading fails
     */
    public static Resource getTemplate(String templatePath) throws IOException {
        templatePath = templatePath != null ? templatePath : DEFAULT_TEMPLATE_PATH;
        
        // Try loading from classpath first
        Resource resource = new ClassPathResource(templatePath);
        if (resource.exists()) {
            return resource;
        }
        
        // Try loading from file system
        resource = new FileSystemResource(templatePath);
        if (resource.exists()) {
            return resource;
        }
        
        // Generate default template if not exists
        String tempPath = System.getProperty("java.io.tmpdir");
        String filename = "template-" + UuidUtils.generateUuidV7() + ".xlsx";
        Path templateFilePath = Paths.get(tempPath, filename);
        
        generateDefaultTemplate(templateFilePath.toString());
        
        log.info("Generated template at: {}", templateFilePath);
        return new FileSystemResource(templateFilePath.toFile());
    }
    
    /**
     * Load template data with caching
     *
     * @param templatePath Template path
     * @return Template data
     * @throws IOException If template loading fails
     */
    public static byte[] loadTemplateData(String templatePath) throws IOException {
        return TEMPLATE_CACHE.computeIfAbsent(templatePath, path -> {
            try {
                Resource resource = getTemplate(path);
                return FileCopyUtils.copyToByteArray(resource.getInputStream());
            } catch (IOException e) {
                log.error("Failed to load template: {}", path, e);
                return null;
            }
        });
    }
    
    /**
     * Generate default template
     *
     * @param outputPath Output path
     * @throws IOException If template generation fails
     */
    public static void generateDefaultTemplate(String outputPath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            
            // Create header area with JXLS markers
            Row headerRow = sheet.createRow(0);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("jx:area(lastCell=\"Z100\")");
            
            Row headerMarkerRow = sheet.createRow(1);
            Cell headerMarkerCell = headerMarkerRow.createCell(0);
            headerMarkerCell.setCellValue("jx:each(items=\"headers\" var=\"header\" direction=\"RIGHT\" lastCell=\"Z1\")");
            
            Cell headerValueCell = headerMarkerRow.createCell(1);
            headerValueCell.setCellValue(HEADERS_MARKER);
            
            // Create data area with JXLS markers
            Row dataMarkerRow = sheet.createRow(2);
            Cell dataMarkerCell = dataMarkerRow.createCell(0);
            dataMarkerCell.setCellValue("jx:each(items=\"rows\" var=\"row\" lastCell=\"Z100\")");
            
            Row dataValueRow = sheet.createRow(3);
            Cell dataValueCell = dataValueRow.createCell(0);
            dataValueCell.setCellValue("jx:each(items=\"row\" var=\"cell\" direction=\"RIGHT\" lastCell=\"Z3\")");
            
            Cell cellValueCell = dataValueRow.createCell(1);
            cellValueCell.setCellValue(ROWS_MARKER);
            
            // Apply styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            headerValueCell.setCellStyle(headerStyle);
            
            // Set default column width
            for (int i = 0; i < 10; i++) {
                sheet.setColumnWidth(i, 15 * 256); // 15 characters width
            }
            
            // Add comments
            addTemplateComments(workbook, sheet, headerCell);
            
            // Ensure directory exists
            Path outputDir = Paths.get(outputPath).getParent();
            if (outputDir != null && !Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            // Save template
            try (OutputStream os = new FileOutputStream(outputPath)) {
                workbook.write(os);
            }
            
            log.info("Generated default template at: {}", outputPath);
        }
    }
    
    /**
     * Create header style
     *
     * @param workbook Workbook instance
     * @return Cell style
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * Add template comments
     *
     * @param workbook Workbook instance
     * @param sheet Sheet instance
     * @param headerCell Header cell
     */
    private static void addTemplateComments(Workbook workbook, Sheet sheet, Cell headerCell) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 1, 1, 3, 3);
        
        Comment comment = drawing.createCellComment(anchor);
        RichTextString text = workbook.getCreationHelper().createRichTextString(
                "JXLS template markers:\n" +
                "${headers} - Column headers will be placed here\n" +
                "${rows} - Data rows will be placed here\n" +
                "This template supports dynamic data binding and formatting.");
        comment.setString(text);
        
        headerCell.setCellComment(comment);
    }
    
    /**
     * Clear template cache
     */
    public static void clearCache() {
        TEMPLATE_CACHE.clear();
        log.info("Template cache cleared");
    }
    
    /**
     * Remove specific template from cache
     *
     * @param templatePath Path of template to remove
     */
    public static void removeFromCache(String templatePath) {
        TEMPLATE_CACHE.remove(templatePath);
        log.debug("Template removed from cache: {}", templatePath);
    }
    
    /**
     * Check if template exists
     *
     * @param templatePath Template path
     * @return true if template exists, false otherwise
     */
    public static boolean templateExists(String templatePath) {
        if (templatePath == null) {
            return false;
        }
        
        // Check classpath
        Resource resource = new ClassPathResource(templatePath);
        if (resource.exists()) {
            return true;
        }
        
        // Check file system
        resource = new FileSystemResource(templatePath);
        return resource.exists();
    }
    
    /**
     * Get template as input stream
     *
     * @param templatePath Template path
     * @return Template input stream
     * @throws IOException If template loading fails
     */
    public static InputStream getTemplateStream(String templatePath) throws IOException {
        byte[] data = loadTemplateData(templatePath);
        if (data == null) {
            throw new IOException("Failed to load template data: " + templatePath);
        }
        return new ByteArrayInputStream(data);
    }
} 