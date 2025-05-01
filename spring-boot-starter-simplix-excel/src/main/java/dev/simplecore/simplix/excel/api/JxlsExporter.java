/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.api;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * JXLS Template-based Excel Export Interface
 * Exports data to Excel based on defined template files.
 */
public interface JxlsExporter {
    
    /**
     * Set file name
     * 
     * @param filename File name
     * @return this instance (for method chaining)
     */
    JxlsExporter filename(String filename);
    
    /**
     * Set template path
     * 
     * @param templatePath Template file path (relative to classpath)
     * @return this instance (for method chaining)
     */
    JxlsExporter template(String templatePath);
    
    /**
     * Set formula processor activation
     * 
     * @param enableFormulaProcessor Whether to enable formula processor
     * @return this instance (for method chaining)
     */
    JxlsExporter enableFormulas(boolean enableFormulaProcessor);
    
    /**
     * Set streaming mode
     * 
     * @param streamingEnabled Whether to enable streaming mode
     * @return this instance (for method chaining)
     */
    JxlsExporter streaming(boolean streamingEnabled);
    
    /**
     * Set grid lines visibility
     * 
     * @param hideGridLines Whether to hide grid lines
     * @return this instance (for method chaining)
     */
    JxlsExporter hideGridLines(boolean hideGridLines);
    
    /**
     * Set row access window size
     * 
     * @param windowSize Window size
     * @return this instance (for method chaining)
     */
    JxlsExporter rowAccessWindowSize(int windowSize);
    
    /**
     * Set sheet name
     * 
     * @param sheetName Sheet name
     * @return this instance (for method chaining)
     */
    JxlsExporter sheetName(String sheetName);
    
    /**
     * Execute export (output to HTTP response)
     * 
     * @param model Data model
     * @param response HTTP response object
     * @throws IOException If an I/O error occurs
     */
    void export(Map<String, Object> model, HttpServletResponse response) throws IOException;
    
    /**
     * Execute export (output to OutputStream)
     * 
     * @param model Data model
     * @param outputStream Output stream
     * @throws IOException If an I/O error occurs
     */
    void export(Map<String, Object> model, OutputStream outputStream) throws IOException;
} 