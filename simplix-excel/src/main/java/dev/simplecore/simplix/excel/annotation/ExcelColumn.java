/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.annotation;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking entity fields as Excel columns with styling options.
 * 
 * <p>This annotation provides basic features for Excel export:</p>
 * <ul>
 *   <li><b>Basic properties:</b> Set column name, order, width, etc.</li>
 *   <li><b>Styling options:</b> Control font, alignment, colors, etc.</li>
 * </ul>
 * 
 * <h2>Usage Examples</h2>
 * 
 * <h3>Basic Usage</h3>
 * <pre>
 * // Simple field export
 * &#64;ExcelColumn(name = "User Name", order = 1)
 * private String username;
 * </pre>
 * 
 * <h3>Styling Options</h3>
 * <pre>
 * // Column with custom styling
 * &#64;ExcelColumn(
 *     name = "Account Balance", 
 *     order = 2,
 *     alignment = HorizontalAlignment.RIGHT,
 *     fontColor = IndexedColors.RED,
 *     bold = true
 * )
 * private String balance;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    /**
     * Column title in Excel
     */
    String name() default "";
    
    /**
     * Column order (0-based)
     */
    int order() default Integer.MAX_VALUE;
    
    /**
     * Whether to ignore this column
     */
    boolean ignore() default false;
    
    /**
     * Column width in Excel units
     */
    int width() default 15;
    
    /**
     * Font name for the column
     */
    String fontName() default "Arial";
    
    /**
     * Font size for the column
     */
    short fontSize() default 10;
    
    /**
     * Whether to make the font bold
     */
    boolean bold() default false;
    
    /**
     * Whether to make the font italic
     */
    boolean italic() default false;
    
    /**
     * Text alignment using HorizontalAlignment enum
     * Example: alignment = HorizontalAlignment.CENTER
     */
    HorizontalAlignment alignment() default HorizontalAlignment.LEFT;
    
    /**
     * Background color using IndexedColors enum
     * Example: backgroundColor = IndexedColors.YELLOW
     */
    IndexedColors backgroundColor() default IndexedColors.AUTOMATIC;
    
    /**
     * Font color using IndexedColors enum
     * Example: fontColor = IndexedColors.RED
     */
    IndexedColors fontColor() default IndexedColors.AUTOMATIC;
    
    /**
     * Whether to wrap text in the column
     */
    boolean wrapText() default true;
    
    /**
     * Format pattern for numbers and dates
     * (e.g., "#,##0.00" for numbers, "yyyy-MM-dd" for dates)
     */
    String format() default "";
} 