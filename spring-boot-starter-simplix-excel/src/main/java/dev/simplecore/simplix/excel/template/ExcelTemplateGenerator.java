/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.template;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Excel Template Generator
 * Specialized component for generating Excel templates with JXLS markers
 */
@Slf4j
public class ExcelTemplateGenerator {

    private static final String DEFAULT_TEMPLATE_PATH = "templates/default-template.xlsx";
    private static final String HEADERS_MARKER = "${headers}";
    private static final String ROWS_MARKER = "${rows}";
    
    @Builder
    @Getter
    public static class TemplateOptions {
        private final String sheetName;
        private final int defaultColumnWidth;
        private final boolean useJxlsMarkers;
        private final boolean applyHeaderStyle;
        private final List<String> additionalMarkers;
        
        private TemplateOptions(String sheetName, int defaultColumnWidth, boolean useJxlsMarkers, 
                boolean applyHeaderStyle, List<String> additionalMarkers) {
            this.sheetName = sheetName;
            this.defaultColumnWidth = defaultColumnWidth;
            this.useJxlsMarkers = useJxlsMarkers;
            this.applyHeaderStyle = applyHeaderStyle;
            this.additionalMarkers = additionalMarkers;
        }
        
        public static class TemplateOptionsBuilder {
            private String sheetName = "Data";
            private int defaultColumnWidth = 15;
            private boolean useJxlsMarkers = true;
            private boolean applyHeaderStyle = true;
            private List<String> additionalMarkers = new ArrayList<>();
        }
    }
    
    private ExcelTemplateGenerator() {
        // Prevent instantiation
    }
    
    /**
     * Generate default template file
     * 
     * @return Generated template file path
     * @throws IOException When file creation fails
     */
    public static String generateDefaultTemplate() throws IOException {
        Path tempFile = Files.createTempFile("default-template-", ".xlsx");
        try (OutputStream os = new FileOutputStream(tempFile.toFile())) {
            generateDefaultTemplate(os);
        }
        return tempFile.toString();
    }
    
    /**
     * Generate default template file (with specified path)
     * 
     * @param outputPath Output file path
     * @throws IOException When file creation fails
     */
    public static void generateDefaultTemplate(String outputPath) throws IOException {
        try (OutputStream os = new FileOutputStream(outputPath)) {
            generateDefaultTemplate(os);
        }
    }
    
    /**
     * Generate default template file (with output stream)
     * 
     * @param outputStream Output stream
     * @throws IOException When file creation fails
     */
    public static void generateDefaultTemplate(OutputStream outputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            
            // Create header area
            Row headerRow = sheet.createRow(0);
            Cell headerCell = headerRow.createCell(0);
            headerCell.setCellValue("jx:area(lastCell=\"Z100\")");
            
            Row headerMarkerRow = sheet.createRow(1);
            Cell headerMarkerCell = headerMarkerRow.createCell(0);
            headerMarkerCell.setCellValue("jx:each(items=\"headers\" var=\"header\" direction=\"RIGHT\" lastCell=\"Z1\")");
            
            Cell headerValueCell = headerMarkerRow.createCell(1);
            headerValueCell.setCellValue(HEADERS_MARKER);
            
            // Set header style
            CellStyle headerStyle = createHeaderStyle(workbook);
            headerValueCell.setCellStyle(headerStyle);
            
            // Create data area
            Row dataMarkerRow = sheet.createRow(2);
            Cell dataMarkerCell = dataMarkerRow.createCell(0);
            dataMarkerCell.setCellValue("jx:each(items=\"rows\" var=\"row\" lastCell=\"Z100\")");
            
            Row dataValueRow = sheet.createRow(3);
            Cell dataValueCell = dataValueRow.createCell(0);
            dataValueCell.setCellValue("jx:each(items=\"row\" var=\"cell\" direction=\"RIGHT\" lastCell=\"Z3\")");
            
            Cell cellValueCell = dataValueRow.createCell(1);
            cellValueCell.setCellValue(ROWS_MARKER);
            
            // Set default column width
            for (int i = 0; i < 10; i++) {
                sheet.setColumnWidth(i, 15 * 256); // 15 characters width
            }
            
            // Add comment
            Comment comment = createComment(sheet, "This is an automatically generated default template.\nIt contains JXLS markers such as jx:area, jx:each, etc.");
            headerCell.setCellComment(comment);
            
            // Save file
            workbook.write(outputStream);
            log.info("Default Excel template generated successfully");
        }
    }
    
    /**
     * Generate template resource for library
     * 
     * @return Generated template file resource
     * @throws IOException When file creation fails
     */
    public static Resource generateTemplateResource() throws IOException {
        // Check if template exists
        Resource resource = new ClassPathResource(DEFAULT_TEMPLATE_PATH);
        if (resource.exists()) {
            return resource;
        }
        
        // Create in temporary location if not exists
        String tempPath = System.getProperty("java.io.tmpdir");
        String filename = "default-template-" + UUID.randomUUID().toString() + ".xlsx";
        Path templatePath = Paths.get(tempPath, filename);
        
        generateDefaultTemplate(templatePath.toString());
        
        log.info("Generated template at: {}", templatePath);
        return new FileSystemResource(templatePath.toFile());
    }
    
    /**
     * Generate template with default options
     *
     * @param outputStream Output stream to write template to
     * @throws IOException If template generation fails
     */
    public static void generateTemplate(OutputStream outputStream) throws IOException {
        generateTemplate(outputStream, TemplateOptions.builder().build());
    }
    
    /**
     * Generate template with custom options
     *
     * @param outputStream Output stream to write template to
     * @param options Template generation options
     * @throws IOException If template generation fails
     */
    public static void generateTemplate(OutputStream outputStream, TemplateOptions options) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(options.getSheetName());
            
            int currentRow = 0;
            
            // Add JXLS markers if enabled
            if (options.isUseJxlsMarkers()) {
                currentRow = addJxlsMarkers(workbook, sheet);
            }
            
            // Add additional markers
            if (options.getAdditionalMarkers() != null && !options.getAdditionalMarkers().isEmpty()) {
                currentRow = addAdditionalMarkers(workbook, sheet, currentRow, options.getAdditionalMarkers());
            }
            
            // Set default column width
            for (int i = 0; i < 10; i++) {
                sheet.setColumnWidth(i, options.getDefaultColumnWidth() * 256);
            }
            
            // Add template comments
            addTemplateComments(workbook, sheet);
            
            // Save workbook
            workbook.write(outputStream);
            log.debug("Generated Excel template with options: {}", options);
        }
    }
    
    /**
     * Add JXLS markers to template
     *
     * @param workbook Workbook instance
     * @param sheet Sheet instance
     * @return Next row number
     */
    private static int addJxlsMarkers(Workbook workbook, Sheet sheet) {
        // Area marker
        Row areaRow = sheet.createRow(0);
        Cell areaCell = areaRow.createCell(0);
        areaCell.setCellValue("jx:area(lastCell=\"Z100\")");
        
        // Headers section
        Row headerRow = sheet.createRow(1);
        Cell headerMarkerCell = headerRow.createCell(0);
        headerMarkerCell.setCellValue("jx:each(items=\"headers\" var=\"header\" direction=\"RIGHT\" lastCell=\"Z1\")");
        
        Cell headerValueCell = headerRow.createCell(1);
        headerValueCell.setCellValue(HEADERS_MARKER);
        
        // Apply header style
        CellStyle headerStyle = createHeaderStyle(workbook);
        headerValueCell.setCellStyle(headerStyle);
        
        // Data section
        Row dataMarkerRow = sheet.createRow(2);
        Cell dataMarkerCell = dataMarkerRow.createCell(0);
        dataMarkerCell.setCellValue("jx:each(items=\"rows\" var=\"row\" lastCell=\"Z100\")");
        
        Row dataValueRow = sheet.createRow(3);
        Cell dataValueCell = dataValueRow.createCell(0);
        dataValueCell.setCellValue("jx:each(items=\"row\" var=\"cell\" direction=\"RIGHT\" lastCell=\"Z3\")");
        
        Cell cellValueCell = dataValueRow.createCell(1);
        cellValueCell.setCellValue(ROWS_MARKER);
        
        return 4;
    }
    
    /**
     * Add additional markers to template
     *
     * @param workbook Workbook instance
     * @param sheet Sheet instance
     * @param startRow Starting row number
     * @param markers List of markers to add
     * @return Next row number
     */
    private static int addAdditionalMarkers(Workbook workbook, Sheet sheet, int startRow, List<String> markers) {
        int currentRow = startRow;
        
        for (String marker : markers) {
            Row row = sheet.createRow(currentRow++);
            Cell cell = row.createCell(0);
            cell.setCellValue(marker);
        }
        
        return currentRow;
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
     */
    private static void addTemplateComments(Workbook workbook, Sheet sheet) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 1, 1, 3, 3);
        
        Comment comment = drawing.createCellComment(anchor);
        RichTextString text = workbook.getCreationHelper().createRichTextString(
                "Template markers:\n" +
                "${headers} - Column headers\n" +
                "${rows} - Data rows\n" +
                "Additional markers can be added using TemplateOptions.");
        comment.setString(text);
        
        sheet.getRow(0).getCell(0).setCellComment(comment);
    }
    
    /**
     * Create cell comment with specified text
     * 
     * @param sheet Target sheet
     * @param commentText Comment text to add
     * @return Created comment object
     */
    private static Comment createComment(Sheet sheet, String commentText) {
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper factory = sheet.getWorkbook().getCreationHelper();
        
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(3);
        anchor.setCol2(6);
        anchor.setRow1(1);
        anchor.setRow2(4);
        
        Comment comment = drawing.createCellComment(anchor);
        RichTextString str = factory.createRichTextString(commentText);
        comment.setString(str);
        comment.setAuthor("SimpliX");
        
        return comment;
    }
} 