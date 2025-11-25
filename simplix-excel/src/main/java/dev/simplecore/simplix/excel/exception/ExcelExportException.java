/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.exception;

/**
 * Excel Export Exception
 * Wraps all exceptions that occur during the export process.
 */
public class ExcelExportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor
     */
    public ExcelExportException() {
        super("Excel export operation failed");
    }

    /**
     * Constructor with message
     * 
     * @param message Exception message
     */
    public ExcelExportException(String message) {
        super(message);
    }

    /**
     * Constructor with cause
     * 
     * @param cause Cause of the exception
     */
    public ExcelExportException(Throwable cause) {
        super("Excel export operation failed", cause);
    }

    /**
     * Constructor with message and cause
     * 
     * @param message Exception message
     * @param cause Cause of the exception
     */
    public ExcelExportException(String message, Throwable cause) {
        super(message, cause);
    }
} 