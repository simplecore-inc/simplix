/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.format;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.Map;

/**
 * Temporal formatter implementation
 * Handles formatting and parsing of java.time.* classes
 */
@Slf4j
public class TemporalFormatter<T extends Temporal> extends AbstractFormatter<T> {
    
    private final Class<T> temporalType;
    private final Map<String, String> commonPatterns;
    
    public TemporalFormatter(Class<T> temporalType, String defaultPattern) {
        super(defaultPattern);
        this.temporalType = temporalType;
        this.commonPatterns = initializeCommonPatterns();
    }
    
    @Override
    protected String doFormat(T value, String pattern) throws Exception {
        DateTimeFormatter formatter = FormatterCache.getTemporalFormatter(pattern);
        
        if (value instanceof ZonedDateTime) {
            return formatter.format(((ZonedDateTime) value)
                .withZoneSameInstant(FormatterCache.getDefaultZone()));
        } else if (value instanceof OffsetDateTime) {
            return formatter.format(((OffsetDateTime) value)
                .atZoneSameInstant(FormatterCache.getDefaultZone()));
        } else if (value instanceof Instant) {
            return formatter.format(((Instant) value)
                .atZone(FormatterCache.getDefaultZone()));
        } else {
            return formatter.format(value);
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    protected T doParse(String value, String pattern) throws Exception {
        // Try specified pattern first
        T result = tryParse(value, pattern);
        if (result != null) {
            return result;
        }
        
        // Try common patterns
        for (String commonPattern : commonPatterns.values()) {
            result = tryParse(value, commonPattern);
            if (result != null) {
                return result;
            }
        }
        
        // Try timestamp (milliseconds since epoch)
        if (temporalType == Instant.class) {
            try {
                long timestamp = Long.parseLong(value);
                return (T) Instant.ofEpochMilli(timestamp);
            } catch (NumberFormatException ignored) {
                // Not a timestamp
            }
        }
        
        throw new DateTimeParseException("Unable to parse temporal value", value, 0);
    }
    
    @SuppressWarnings("unchecked")
    private T tryParse(String value, String pattern) {
        try {
            DateTimeFormatter formatter = FormatterCache.getTemporalFormatter(pattern);
            
            if (temporalType == LocalDateTime.class) {
                return (T) LocalDateTime.parse(value, formatter);
            } else if (temporalType == LocalDate.class) {
                return (T) LocalDate.parse(value, formatter);
            } else if (temporalType == LocalTime.class) {
                return (T) LocalTime.parse(value, formatter);
            } else if (temporalType == ZonedDateTime.class) {
                return (T) ZonedDateTime.parse(value, formatter)
                    .withZoneSameInstant(FormatterCache.getDefaultZone());
            } else if (temporalType == OffsetDateTime.class) {
                return (T) OffsetDateTime.parse(value, formatter)
                    .atZoneSameInstant(FormatterCache.getDefaultZone())
                    .toOffsetDateTime();
            } else if (temporalType == Instant.class) {
                return (T) Instant.from(formatter.parse(value));
            }
        } catch (DateTimeParseException ignored) {
            // Try next pattern
        }
        return null;
    }
    
    private Map<String, String> initializeCommonPatterns() {
        Map<String, String> patterns = new HashMap<>();
        
        if (temporalType == LocalDateTime.class || temporalType == ZonedDateTime.class || 
            temporalType == OffsetDateTime.class || temporalType == Instant.class) {
            patterns.put("iso8601", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            patterns.put("iso8601_no_millis", "yyyy-MM-dd'T'HH:mm:ssXXX");
            patterns.put("datetime", "yyyy-MM-dd HH:mm:ss");
            patterns.put("datetime_no_seconds", "yyyy-MM-dd HH:mm");
            patterns.put("compact", "yyyyMMddHHmmss");
        } else if (temporalType == LocalDate.class) {
            patterns.put("iso8601", "yyyy-MM-dd");
            patterns.put("date_slash", "yyyy/MM/dd");
            patterns.put("date_dot", "yyyy.MM.dd");
            patterns.put("compact", "yyyyMMdd");
            patterns.put("european", "dd-MM-yyyy");
            patterns.put("european_slash", "dd/MM/yyyy");
        } else if (temporalType == LocalTime.class) {
            patterns.put("time", "HH:mm:ss.SSS");
            patterns.put("time_no_millis", "HH:mm:ss");
            patterns.put("time_no_seconds", "HH:mm");
            patterns.put("compact", "HHmmss");
        }
        
        return patterns;
    }
} 