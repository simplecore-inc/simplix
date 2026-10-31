/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.export;

/**
 * The file kinds a list export is written as, whose lower-case name is the file extension.
 */
public enum ExportFormat {
    CSV("csv"),
    XLSX("xlsx");

    private final String parameter;

    ExportFormat(String parameter) {
        this.parameter = parameter;
    }

    /** The request value and file extension of this format. */
    public String parameter() {
        return parameter;
    }
}
