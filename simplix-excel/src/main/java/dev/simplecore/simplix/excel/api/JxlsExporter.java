/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.api;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

/**
 * JXLS Template-based Excel Export Interface
 * Exports data to Excel based on defined template files.
 */
public interface JxlsExporter<T> {
    
    /**
     * Set file name
     * 
     * @param filename File name
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> filename(String filename);
    
    /**
     * Set template path
     * 
     * @param templatePath Template file path (relative to classpath)
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> template(String templatePath);
    
    /**
     * Set formula processor activation
     * 
     * @param enableFormulaProcessor Whether to enable formula processor
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> enableFormulas(boolean enableFormulaProcessor);
    
    /**
     * Set streaming mode
     * 
     * @param streamingEnabled Whether to enable streaming mode
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> streaming(boolean streamingEnabled);
    
    /**
     * Set grid lines visibility
     * 
     * @param hideGridLines Whether to hide grid lines
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> hideGridLines(boolean hideGridLines);
    
    /**
     * Set row access window size
     * 
     * @param windowSize Window size
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> rowAccessWindowSize(int windowSize);
    
    /**
     * Set sheet name
     * 
     * @param sheetName Sheet name
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> sheetName(String sheetName);
    
    /**
     * Add parameter to the export context
     * 
     * @param name Parameter name
     * @param value Parameter value
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> parameter(String name, Object value);
    
    /**
     * Add parameters to the export context
     * 
     * @param parameters Map of parameters
     * @return this instance (for method chaining)
     */
    JxlsExporter<T> parameters(Map<String, Object> parameters);
    
    /**
     * Execute export (output to HTTP response)
     * 
     * @param items Collection of items to export
     * @param response HTTP response object
     * @throws IOException If an I/O error occurs
     */
    void export(Collection<T> items, HttpServletResponse response) throws IOException;
    
    /**
     * Execute export (output to OutputStream)
     * 
     * @param items Collection of items to export
     * @param outputStream Output stream
     * @throws IOException If an I/O error occurs
     */
    void export(Collection<T> items, OutputStream outputStream) throws IOException;
    
    /**
     * Execute export with specified filename (output to HTTP response)
     * 
     * @param items Collection of items to export
     * @param response HTTP response object
     * @param filename File name
     * @throws IOException If an I/O error occurs
     */
    void export(Collection<T> items, HttpServletResponse response, String filename) throws IOException;
} 