/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date formatter implementation
 * Handles formatting and parsing of java.util.Date objects
 */
@Slf4j
public class DateFormatter extends AbstractFormatter<Date> {
    
    private static final String DEFAULT_PATTERN = "yyyy-MM-dd";
    
    public DateFormatter() {
        super(DEFAULT_PATTERN);
    }
    
    @Override
    protected String doFormat(Date value, String pattern) throws Exception {
        SimpleDateFormat formatter = FormatterCache.getLegacyDateFormatter(pattern);
        return formatter.format(value);
    }
    
    @Override
    protected Date doParse(String value, String pattern) throws ParseException {
        SimpleDateFormat formatter = FormatterCache.getLegacyDateFormatter(pattern);
        return formatter.parse(value);
    }
} 