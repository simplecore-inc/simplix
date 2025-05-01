/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.api.JxlsExporter;
import dev.simplecore.simplix.excel.template.ExcelTemplateManager;
import dev.simplecore.simplix.excel.util.StringUtil;
import dev.simplecore.simplix.excel.exception.ExcelExportException;
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
import java.util.Collection;
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
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        
        try (InputStream templateStream = getTemplateStream()) {
            Context context = new Context();
            context.putVar("data", model);
            
            JxlsHelper.getInstance()
                .setUseFastFormulaProcessor(enableFormulaProcessor)
                .processTemplate(templateStream, outputStream, context);
                
            log.info("Completed JXLS export in {} seconds", Duration.between(Instant.now(), Instant.now()).getSeconds());
        } catch (IOException e) {
            log.error("Failed to export data using JXLS", e);
            throw new ExcelExportException("Failed to export data using JXLS", e);
        }
    }
    
    /**
     * Configure HTTP response headers
     */
    private void configureResponse(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", String.format("attachment; filename=%s", filename));
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
    }
    
    /**
     * Load template stream (with caching)
     */
    private InputStream getTemplateStream() throws IOException {
        // Load template through ExcelTemplateManager (with caching)
        return ExcelTemplateManager.getTemplateStream(templatePath);
    }
} 