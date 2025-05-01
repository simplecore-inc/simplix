/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking entity fields as Excel columns with enhanced styling options
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
     * Date format pattern (for date fields)
     */
    String format() default "";
    
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
     * Text alignment (LEFT, CENTER, RIGHT)
     */
    String alignment() default "LEFT";
    
    /**
     * Background color in hex format (e.g., "#FFFFFF")
     */
    String backgroundColor() default "";
    
    /**
     * Font color in hex format (e.g., "#000000")
     */
    String fontColor() default "";
    
    /**
     * Whether to wrap text in the column
     */
    boolean wrapText() default true;
    
    /**
     * Whether to merge cells vertically when values are the same
     */
    boolean mergeVertical() default false;
    
    /**
     * Conditional formatting expression
     * Format: "condition:style,condition:style"
     * Example: "value > 1000:#FF0000,value < 0:#0000FF"
     */
    String conditionalFormat() default "";
} 