/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * SimpliX Excel Module Configuration Properties
 * Configurable in application.yml or application.properties
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "simplix.excel")
public class SimplixExcelProperties {

    /**
     * Template configuration
     */
    @NestedConfigurationProperty
    private TemplateProperties template = new TemplateProperties();

    /**
     * Export configuration
     */
    @NestedConfigurationProperty
    private ExportProperties export = new ExportProperties();

    /**
     * CSV configuration
     */
    @NestedConfigurationProperty
    private CsvProperties csv = new CsvProperties();

    /**
     * Format configuration
     */
    @NestedConfigurationProperty
    private FormatProperties format = new FormatProperties();

    /**
     * Cache configuration
     */
    @NestedConfigurationProperty
    private CacheProperties cache = new CacheProperties();

    @Getter
    @Setter
    public static class TemplateProperties {
        /**
         * Template file path
         */
        private String path = "templates/default-template.xlsx";

        /**
         * Default sheet name
         */
        private String defaultSheetName = "Data";

        /**
         * Default column width (in characters)
         */
        private int defaultColumnWidth = 15;

        /**
         * Whether to use JXLS markers by default
         */
        private boolean useJxlsMarkers = true;

        /**
         * Whether to apply header styles by default
         */
        private boolean applyHeaderStyle = true;
    }

    @Getter
    @Setter
    public static class ExportProperties {
        /**
         * Default page size for batch processing
         */
        private int pageSize = 1000;

        /**
         * POI window size (number of rows to keep in memory)
         */
        private int windowSize = 100;

        /**
         * Default sheet name
         */
        private String defaultSheetName = "Data";

        /**
         * Whether to enable streaming mode
         */
        private boolean streamingEnabled = false;

        /**
         * Whether to hide grid lines by default
         */
        private boolean hideGridLines = false;

        /**
         * Whether to auto-size columns by default
         */
        private boolean autoSizeColumns = false;

        /**
         * Whether to enable formula processing by default
         */
        private boolean enableFormulas = true;
    }

    @Getter
    @Setter
    public static class CsvProperties {
        /**
         * CSV delimiter
         */
        private String delimiter = ",";

        /**
         * CSV encoding
         */
        private String encoding = "UTF-8";

        /**
         * Whether to auto-quote strings
         */
        private boolean quoteStrings = true;

        /**
         * Whether to include headers by default
         */
        private boolean includeHeaders = true;

        /**
         * Line separator (default: system line separator)
         */
        private String lineSeparator = System.lineSeparator();
    }

    @Getter
    @Setter
    public static class FormatProperties {
        /**
         * Default date format pattern
         */
        private String dateFormat = "yyyy-MM-dd";

        /**
         * Default time format pattern
         */
        private String timeFormat = "HH:mm:ss";

        /**
         * Default datetime format pattern
         */
        private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

        /**
         * Default number format pattern
         */
        private String numberFormat = "#,##0";

        /**
         * Default decimal format pattern
         */
        private String decimalFormat = "#,##0.00";

        /**
         * Default percentage format pattern
         */
        private String percentageFormat = "#,##0.00%";

        /**
         * Default currency format pattern
         */
        private String currencyFormat = "¤#,##0.00";

        /**
         * Default boolean format (true value)
         */
        private String booleanTrueValue = "Y";

        /**
         * Default boolean format (false value)
         */
        private String booleanFalseValue = "N";
    }

    @Getter
    @Setter
    public static class CacheProperties {
        /**
         * Whether to enable template caching
         */
        private boolean templateCacheEnabled = true;

        /**
         * Whether to enable field caching
         */
        private boolean fieldCacheEnabled = true;

        /**
         * Whether to enable column caching
         */
        private boolean columnCacheEnabled = true;

        /**
         * Maximum template cache size
         */
        private int maxTemplateCacheSize = 100;

        /**
         * Maximum field cache size
         */
        private int maxFieldCacheSize = 100;

        /**
         * Maximum column cache size
         */
        private int maxColumnCacheSize = 100;
    }
} 