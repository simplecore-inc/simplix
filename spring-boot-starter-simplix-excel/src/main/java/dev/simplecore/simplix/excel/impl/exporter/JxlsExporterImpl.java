/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.api.JxlsExporter;
import lombok.extern.slf4j.Slf4j;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * JXLS Template-based Excel Export Implementation
 * Exports data to Excel based on a defined template file.
 */
@Slf4j
public class JxlsExporterImpl<T> extends AbstractExporter<T> implements JxlsExporter<T> {
    
    private String templatePath;
    private boolean enableFormulaProcessor;
    private boolean hideGridLines;
    private int rowAccessWindowSize;
    private String sheetName;
    private Map<String, Object> parameters;
    
    /**
     * Creates a JXLS exporter for the specified data class
     *
     * @param dataClass The class of the data objects to export
     */
    public JxlsExporterImpl(Class<T> dataClass) {
        super(dataClass);
        this.parameters = new HashMap<>();
        this.filename = "export.xlsx";
        this.enableFormulaProcessor = true;
        this.useStreaming = false;
        this.hideGridLines = false;
        this.rowAccessWindowSize = 100;
        this.sheetName = "Sheet1";
    }
    
    @Override
    public JxlsExporterImpl<T> template(String templatePath) {
        this.templatePath = templatePath;
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> filename(String filename) {
        super.filename(filename);
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> parameters(Map<String, Object> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> parameter(String name, Object value) {
        this.parameters.put(name, value);
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> enableFormulas(boolean enableFormulaProcessor) {
        this.enableFormulaProcessor = enableFormulaProcessor;
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> streaming(boolean streamingEnabled) {
        super.streaming(streamingEnabled);
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> hideGridLines(boolean hideGridLines) {
        this.hideGridLines = hideGridLines;
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> rowAccessWindowSize(int windowSize) {
        this.rowAccessWindowSize = windowSize;
        return this;
    }
    
    @Override
    public JxlsExporterImpl<T> sheetName(String sheetName) {
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
        return useStreaming;
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
    public void export(Collection<T> items, HttpServletResponse response) throws IOException {
        if (templatePath == null || templatePath.isEmpty()) {
            throw new IllegalStateException("Template path must be set");
        }

        configureExcelResponse(response);

        try (OutputStream outputStream = response.getOutputStream()) {
            export(items, outputStream);
        }
    }
    
    @Override
    public void export(Collection<T> items, OutputStream outputStream) throws IOException {
        if (templatePath == null || templatePath.isEmpty()) {
            throw new IllegalStateException("Template path must be set");
        }

        Instant start = Instant.now();
        logExportStart(items, "Starting JXLS template export of {} items of type {}");

        try (InputStream templateStream = getClass().getResourceAsStream(templatePath)) {
            if (templateStream == null) {
                // Try to load from file system if not found in classpath
                try (FileInputStream fileInputStream = new FileInputStream(templatePath)) {
                    processTemplate(fileInputStream, items, outputStream);
                } catch (FileNotFoundException e) {
                    throw new IllegalArgumentException("Template not found: " + templatePath, e);
                }
            } else {
                processTemplate(templateStream, items, outputStream);
            }
        }
        
        logExportCompletion(start);
    }
    
    /**
     * Process JXLS template
     */
    private void processTemplate(InputStream templateStream, Collection<?> items, OutputStream outputStream) throws IOException {
        Context context = new Context();
        context.putVar("items", items);

        // Add all parameters to context
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            context.putVar(entry.getKey(), entry.getValue());
        }

        JxlsHelper.getInstance()
            .setUseFastFormulaProcessor(enableFormulaProcessor)
            .processTemplate(templateStream, outputStream, context);
    }
    
    @Override
    public void export(Collection<T> items, HttpServletResponse response, String filename) throws IOException {
        this.filename = filename;
        export(items, response);
    }
} 