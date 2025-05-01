/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

/**
 * Default formatter implementation
 * Handles basic string conversion with no specific pattern
 */
@Slf4j
public class DefaultFormatter extends AbstractFormatter<Object> {
    
    public DefaultFormatter() {
        super("");
    }
    
    @Override
    protected String doFormat(Object value, String pattern) {
        return value.toString();
    }
    
    @Override
    protected Object doParse(String value, String pattern) {
        return value;
    }
} 