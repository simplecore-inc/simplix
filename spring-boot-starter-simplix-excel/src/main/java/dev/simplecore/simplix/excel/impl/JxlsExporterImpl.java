/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.api.JxlsExporter;
import dev.simplecore.simplix.excel.template.ExcelTemplateManager;
import dev.simplecore.simplix.excel.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jxls.common.Context;
import org.jxls.transform.poi.PoiTransformer;
import org.jxls.util.JxlsHelper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * JXLS Template-based Excel Export Implementation
 * Exports data to Excel based on a defined template file.
 */
@Slf4j
public class JxlsExporterImpl implements JxlsExporter {
    
    private String templatePath;
    private String filename;
    private boolean enableFormulaProcessor;
    private boolean streamingEnabled;
    private boolean hideGridLines;
    private int rowAccessWindowSize;
    private String sheetName;
    
    /**
     * Constructor using default template
     */
    public JxlsExporterImpl() {
        this("templates/default-template.xlsx");
    }
    
    /**
     * Constructor using specified template path
     * 
     * @param templatePath Template file path (relative to classpath)
     */
    public JxlsExporterImpl(String templatePath) {
        this.templatePath = templatePath;
        this.filename = "export.xlsx";
        this.enableFormulaProcessor = true;
        this.streamingEnabled = false;
        this.hideGridLines = false;
        this.rowAccessWindowSize = 100;
        this.sheetName = "Sheet1";
    }
    
    @Override
    public JxlsExporterImpl filename(String filename) {
        this.filename = filename;
        return this;
    }
    
    @Override
    public JxlsExporterImpl template(String templatePath) {
        this.templatePath = templatePath;
        return this;
    }
    
    @Override
    public JxlsExporterImpl enableFormulas(boolean enableFormulaProcessor) {
        this.enableFormulaProcessor = enableFormulaProcessor;
        return this;
    }
    
    @Override
    public JxlsExporterImpl streaming(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
        return this;
    }
    
    @Override
    public JxlsExporterImpl hideGridLines(boolean hideGridLines) {
        this.hideGridLines = hideGridLines;
        return this;
    }
    
    @Override
    public JxlsExporterImpl rowAccessWindowSize(int windowSize) {
        this.rowAccessWindowSize = windowSize;
        return this;
    }
    
    @Override
    public JxlsExporterImpl sheetName(String sheetName) {
        this.sheetName = sheetName;
        return this;
    }
    
    /**
     * Get template path
     * 
     * @return Template path
     */
    public String getTemplatePath() {
        return templatePath;
    }
    
    /**
     * Get filename
     * 
     * @return Filename
     */
    public String getFilename() {
        return filename;
    }
    
    /**
     * Check if formula processor is enabled
     * 
     * @return Formula processor enabled status
     */
    public boolean isEnableFormulaProcessor() {
        return enableFormulaProcessor;
    }
    
    /**
     * Check if streaming mode is enabled
     * 
     * @return Streaming mode enabled status
     */
    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }
    
    /**
     * Check if grid lines are hidden
     * 
     * @return Grid lines hidden status
     */
    public boolean isHideGridLines() {
        return hideGridLines;
    }
    
    /**
     * Get row access window size
     * 
     * @return Window size
     */
    public int getRowAccessWindowSize() {
        return rowAccessWindowSize;
    }
    
    /**
     * Get sheet name
     * 
     * @return Sheet name
     */
    public String getSheetName() {
        return sheetName;
    }
    
    @Override
    public void export(Map<String, Object> model, HttpServletResponse response) throws IOException {
        log.info("Starting JXLS template-based export using template: {}", templatePath);
        
        // Configure HTTP response
        configureResponse(response);
        
        try (OutputStream os = response.getOutputStream()) {
            export(model, os);
        }
    }
    
    @Override
    public void export(Map<String, Object> model, OutputStream outputStream) throws IOException {
        Instant start = Instant.now();
        log.info("Starting JXLS template-based export using template: {}", templatePath);
        
        try (InputStream templateStream = getTemplateStream()) {
            // Prepare JXLS context
            Context context = new Context();
            model.forEach(context::putVar);
            
            // Add sheet name to context
            context.putVar("sheetName", sheetName);
            
            // Configure and execute JXLS export
            JxlsHelper jxlsHelper = JxlsHelper.getInstance();
            jxlsHelper.setUseFastFormulaProcessor(enableFormulaProcessor);
            jxlsHelper.setProcessFormulas(enableFormulaProcessor);
            
            // Apply streaming settings
            if (streamingEnabled) {
                // Streaming mode: Load XSSFWorkbook from template -> Convert to SXSSFWorkbook
                try (XSSFWorkbook xssfWorkbook = new XSSFWorkbook(templateStream)) {
                    SXSSFWorkbook sxssfWorkbook = new SXSSFWorkbook(xssfWorkbook, rowAccessWindowSize);
                    sxssfWorkbook.setCompressTempFiles(true);
                    
                    if (hideGridLines) {
                        for (int i = 0; i < sxssfWorkbook.getNumberOfSheets(); i++) {
                            sxssfWorkbook.getSheetAt(i).setDisplayGridlines(false);
                        }
                    }
                    
                    // Set sheet name
                    if (StringUtil.hasText(sheetName) && sxssfWorkbook.getNumberOfSheets() > 0) {
                        sxssfWorkbook.setSheetName(0, sheetName);
                    }
                    
                    PoiTransformer transformer = PoiTransformer.createTransformer(sxssfWorkbook);
                    jxlsHelper.processTemplate(context, transformer);
                    
                    // Write workbook directly
                    sxssfWorkbook.write(outputStream);
                    
                    // Clean up temporary files
                    sxssfWorkbook.dispose();
                }
            } else {
                // Normal mode
                jxlsHelper.processTemplate(templateStream, outputStream, context);
            }
            
            Instant end = Instant.now();
            log.info("Completed JXLS export in {} seconds", 
                    Duration.between(start, end).getSeconds());
        }
    }
    
    /**
     * Configure HTTP response headers
     */
    private void configureResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        // URL encode filename if it contains spaces
        String encodedFilename = StringUtil.hasText(filename) ? 
                java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replaceAll("\\+", "%20") : 
                "export.xlsx";
        
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
    
    /**
     * Load template stream (with caching)
     */
    private InputStream getTemplateStream() throws IOException {
        // Load template through ExcelTemplateManager (with caching)
        return ExcelTemplateManager.getTemplateStream(templatePath);
    }
} 