/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.api;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Common CSV Export Interface
 * Defines the base API for all CSV export implementations.
 * 
 * @param <T> Type of data to export
 */
public interface CsvExporter<T> {
    
    /**
     * Set export file name
     * 
     * @param filename File name
     * @return this instance (for method chaining)
     */
    CsvExporter<T> filename(String filename);
    
    /**
     * Set CSV delimiter
     * 
     * @param delimiter Delimiter (default: ",")
     * @return this instance (for method chaining)
     */
    CsvExporter<T> delimiter(String delimiter);
    
    /**
     * Set whether to include header
     * 
     * @param includeHeader Whether to include header
     * @return this instance (for method chaining)
     */
    CsvExporter<T> includeHeader(boolean includeHeader);
    
    /**
     * Set whether to quote string values
     * 
     * @param quoteStrings Whether to quote strings
     * @return this instance (for method chaining)
     */
    CsvExporter<T> quoteStrings(boolean quoteStrings);
    
    /**
     * Set streaming mode for large data processing
     * 
     * @param useStreaming Whether to use streaming mode
     * @return this instance (for method chaining)
     */
    CsvExporter<T> streaming(boolean useStreaming);
    
    /**
     * Set character encoding
     * 
     * @param encoding Encoding (e.g. "UTF-8")
     * @return this instance (for method chaining)
     */
    CsvExporter<T> encoding(String encoding);
    
    /**
     * Execute export (output to HTTP response)
     * 
     * @param items Collection of data to export
     * @param response HTTP response object
     * @throws IOException If an I/O error occurs
     */
    void export(Collection<T> items, HttpServletResponse response) throws IOException;
    
    /**
     * Execute export (output to OutputStream)
     * 
     * @param items Collection of data to export
     * @param outputStream Output stream
     * @throws IOException If an I/O error occurs
     */
    void export(Collection<T> items, OutputStream outputStream) throws IOException;
} 