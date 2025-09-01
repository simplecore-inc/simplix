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

/**
 * Common Excel Export Interface
 * Defines the base API for all Excel export implementations.
 * 
 * @param <T> Type of data to export
 */
public interface ExcelExporter<T> {
    
    /**
     * Set export file name
     * 
     * @param filename File name
     * @return this instance (for method chaining)
     */
    ExcelExporter<T> filename(String filename);
    
    /**
     * Set sheet name
     * 
     * @param sheetName Sheet name
     * @return this instance (for method chaining)
     */
    ExcelExporter<T> sheetName(String sheetName);
    
    /**
     * Set streaming mode for large data processing
     * 
     * @param useStreaming Whether to use streaming mode
     * @return this instance (for method chaining)
     */
    ExcelExporter<T> streaming(boolean useStreaming);
    
    /**
     * Set automatic column width adjustment
     * (May impact performance with large datasets)
     * 
     * @param autoSizeColumns Whether to use automatic column width adjustment
     * @return this instance (for method chaining)
     */
    ExcelExporter<T> autoSizeColumns(boolean autoSizeColumns);
    
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
    
    /**
     * Execute export (output to HTTP response) with custom filename
     *
     * @param items Collection of data to export
     * @param response HTTP response object
     * @param filename Download file name (UTF-8 safe)
     * @throws IOException If an I/O error occurs
     */
    default void export(Collection<T> items, HttpServletResponse response, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encodedFilename = java.net.URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
        export(items, response.getOutputStream());
    }
} 