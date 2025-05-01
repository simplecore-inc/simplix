/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.ParseException;

/**
 * Number formatter implementation
 * Handles formatting and parsing of numeric values
 */
@Slf4j
public class NumberFormatter extends AbstractFormatter<Number> {
    
    private static final String DEFAULT_PATTERN = "#,##0.###";
    
    public NumberFormatter() {
        super(DEFAULT_PATTERN);
    }
    
    @Override
    protected String doFormat(Number value, String pattern) throws Exception {
        DecimalFormat formatter = FormatterCache.getNumberFormatter(pattern);
        return formatter.format(value);
    }
    
    @Override
    protected Number doParse(String value, String pattern) throws ParseException {
        DecimalFormat formatter = FormatterCache.getNumberFormatter(pattern);
        Number parsed = formatter.parse(value);
        return convertToAppropriateType(parsed);
    }
    
    /**
     * Convert number to most appropriate type
     *
     * @param number Number to convert
     * @return Converted number
     */
    private Number convertToAppropriateType(Number number) {
        if (number instanceof Long) {
            long longVal = number.longValue();
            if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
                return (int) longVal;
            }
            return longVal;
        } else if (number instanceof Double) {
            double doubleVal = number.doubleValue();
            if (doubleVal == Math.floor(doubleVal) && !Double.isInfinite(doubleVal)) {
                if (doubleVal >= Integer.MIN_VALUE && doubleVal <= Integer.MAX_VALUE) {
                    return (int) doubleVal;
                } else if (doubleVal >= Long.MIN_VALUE && doubleVal <= Long.MAX_VALUE) {
                    return (long) doubleVal;
                }
            }
            return doubleVal;
        }
        
        return number;
    }
} 