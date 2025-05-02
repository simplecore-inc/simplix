/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.style;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;

import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel Style Manager
 * Creates and caches reusable cell styles.
 * Also provides color conversion utilities.
 */
@Slf4j
public class ExcelStyleManager {
    
    private final Map<String, CellStyle> styleCache = new ConcurrentHashMap<>();
    private final Workbook workbook;

    /**
     * Constructor
     * 
     * @param workbook Workbook to apply styles to
     */
    public ExcelStyleManager(Workbook workbook) {
        this.workbook = workbook;
    }
    
    /**
     * Create header cell style
     * 
     * @return Header style
     */
    public CellStyle createHeaderStyle() {
        String cacheKey = "header_style";
        return styleCache.computeIfAbsent(cacheKey, k -> {
            CellStyle headerStyle = workbook.createCellStyle();
            
            // Set alignment
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            
            // Set borders
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            
            // Set background color
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            
            // Set font
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            
            return headerStyle;
        });
    }
    
    /**
     * Create data cell style
     * 
     * @param value Cell value
     * @param column Column configuration annotation
     * @return Data cell style
     */
    public CellStyle createDataStyle(Object value, ExcelColumn column) {
        String styleKey = getStyleKey(column, value);
        
        return styleCache.computeIfAbsent(styleKey, k -> {
            CellStyle style = workbook.createCellStyle();
            
            // Set font
            Font font = workbook.createFont();
            font.setFontName(column.fontName());
            font.setFontHeightInPoints(column.fontSize());
            font.setBold(column.bold());
            font.setItalic(column.italic());
            
            if (column.fontColor() != IndexedColors.AUTOMATIC) {
                try {
                    font.setColor(column.fontColor().getIndex());
                } catch (Exception e) {
                    log.warn("Failed to set font color: {}", column.fontColor(), e);
                }
            }
            
            style.setFont(font);
            
            // Set alignment
            style.setAlignment(column.alignment());
            
            // Set vertical alignment to CENTER
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            
            // Set background color
            if (column.backgroundColor() != IndexedColors.AUTOMATIC) {
                try {
                    style.setFillForegroundColor(column.backgroundColor().getIndex());
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                } catch (Exception e) {
                    log.warn("Failed to set background color: {}", column.backgroundColor(), e);
                }
            }
            
            // Set text wrap
            style.setWrapText(column.wrapText());
            
            // Apply data format by type
            applyDataFormatByType(style, value, column);
            
            return style;
        });
    }
    
    /**
     * Apply data format by type
     * 
     * @param style Cell style
     * @param value Cell value
     * @param column Column configuration annotation
     */
    private void applyDataFormatByType(CellStyle style, Object value, ExcelColumn column) {
        DataFormat format = workbook.createDataFormat();
        
        if (value instanceof Date || value instanceof Calendar || value instanceof Temporal) {
            // Date/Time format
            if (!column.format().isEmpty()) {
                style.setDataFormat(format.getFormat(column.format()));
            }
        } else if (value instanceof Number) {
            // Number format
            if (!column.format().isEmpty()) {
                style.setDataFormat(format.getFormat(column.format()));
            }
        }
    }
    
    /**
     * Create style cache key
     * 
     * @param column Column configuration annotation
     * @param value Cell value
     * @return Cache key
     */
    private String getStyleKey(ExcelColumn column, Object value) {
        String valueType = value != null ? value.getClass().getSimpleName() : "null";
        
        return String.format("%s_%s_%s_%s_%s_%s_%s_%s_%s_%s_%s",
                column.fontName(), column.fontSize(), column.bold(), column.italic(),
                column.alignment(), "CENTER_VERTICAL", column.backgroundColor(), column.fontColor(),
                column.wrapText(), valueType, 
                value instanceof Date || value instanceof Temporal ? column.format() : 
                value instanceof Number ? column.format() : "");
    }
    
    /**
     * Clear style cache
     */
    public void clearCache() {
        styleCache.clear();
    }
} 