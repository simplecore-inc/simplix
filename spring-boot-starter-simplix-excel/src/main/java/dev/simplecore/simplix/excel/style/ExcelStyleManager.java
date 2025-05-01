/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.style;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.IndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.awt.Color;
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
    
    private static final IndexedColorMap COLOR_MAP = new DefaultIndexedColorMap();
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
     * Convert HEX color code to XSSFColor
     */
    public XSSFColor hexToColor(String hexColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) {
            return null;
        }
        
        try {
            Color color = Color.decode(hexColor);
            byte[] rgb = new byte[]{
                (byte) color.getRed(), 
                (byte) color.getGreen(), 
                (byte) color.getBlue()
            };
            return new XSSFColor(rgb, COLOR_MAP);
        } catch (NumberFormatException e) {
            log.warn("Invalid color format: {}", hexColor);
            return null;
        }
    }
    
    /**
     * Find nearest IndexedColors for HEX color
     */
    public IndexedColors findNearestIndexedColor(String hexColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) {
            return IndexedColors.AUTOMATIC;
        }
        
        try {
            Color color = Color.decode(hexColor);
            IndexedColors nearest = IndexedColors.BLACK;
            double minDistance = Double.MAX_VALUE;
            
            for (IndexedColors indexed : IndexedColors.values()) {
                Color indexedColor = getColorFromIndex(indexed);
                double distance = colorDistance(
                    color.getRed(), color.getGreen(), color.getBlue(),
                    indexedColor.getRed(), indexedColor.getGreen(), indexedColor.getBlue()
                );
                
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = indexed;
                }
            }
            
            return nearest;
        } catch (NumberFormatException e) {
            log.warn("Invalid color format: {}", hexColor);
            return IndexedColors.AUTOMATIC;
        }
    }
    
    /**
     * Get Color from IndexedColors
     */
    private Color getColorFromIndex(IndexedColors indexed) {
        return new Color(indexed.getIndex());
    }
    
    /**
     * Calculate color distance (Euclidean)
     */
    private double colorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return Math.sqrt(dr * dr + dg * dg + db * db);
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
            
            // Set font color
            if (!column.fontColor().isEmpty()) {
                try {
                    IndexedColors indexedColor = findNearestIndexedColor(column.fontColor());
                    font.setColor(indexedColor.getIndex());
                } catch (Exception e) {
                    log.warn("Failed to set font color: {}", column.fontColor(), e);
                }
            }
            
            style.setFont(font);
            
            // Set alignment
            if ("CENTER".equalsIgnoreCase(column.alignment())) {
                style.setAlignment(HorizontalAlignment.CENTER);
            } else if ("RIGHT".equalsIgnoreCase(column.alignment())) {
                style.setAlignment(HorizontalAlignment.RIGHT);
            } else {
                style.setAlignment(HorizontalAlignment.LEFT);
            }
            
            // Set background color
            if (!column.backgroundColor().isEmpty()) {
                try {
                    IndexedColors indexedColor = findNearestIndexedColor(column.backgroundColor());
                    style.setFillForegroundColor(indexedColor.getIndex());
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
            style.setDataFormat(format.getFormat(column.dateFormat()));
        } else if (value instanceof Number) {
            // Number format
            style.setDataFormat(format.getFormat(column.numberFormat()));
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
        
        return String.format("%s_%s_%s_%s_%s_%s_%s_%s_%s_%s",
                column.fontName(), column.fontSize(), column.bold(), column.italic(),
                column.alignment(), column.backgroundColor(), column.fontColor(),
                column.wrapText(), valueType, 
                value instanceof Date || value instanceof Temporal ? column.dateFormat() : 
                value instanceof Number ? column.numberFormat() : "");
    }
    
    /**
     * Clear style cache
     */
    public void clearCache() {
        styleCache.clear();
    }
} 