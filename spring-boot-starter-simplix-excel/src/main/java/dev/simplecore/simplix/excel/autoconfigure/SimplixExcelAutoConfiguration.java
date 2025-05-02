/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.autoconfigure;

import dev.simplecore.simplix.excel.api.CsvExporter;
import dev.simplecore.simplix.excel.api.ExcelExporter;
import dev.simplecore.simplix.excel.api.JxlsExporter;
import dev.simplecore.simplix.excel.convert.ExcelConverter;
import dev.simplecore.simplix.excel.format.ValueFormatter;
import dev.simplecore.simplix.excel.impl.exporter.JxlsExporterImpl;
import dev.simplecore.simplix.excel.impl.exporter.StandardExcelExporter;
import dev.simplecore.simplix.excel.impl.exporter.UnifiedCsvExporter;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import dev.simplecore.simplix.excel.template.ExcelTemplateGenerator;
import dev.simplecore.simplix.excel.template.ExcelTemplateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * SimpliX Excel Module Auto-configuration
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ExcelExporter.class, StandardExcelExporter.class})
@EnableConfigurationProperties(SimplixExcelProperties.class)
public class SimplixExcelAutoConfiguration {

    private final SimplixExcelProperties properties;

    public SimplixExcelAutoConfiguration(SimplixExcelProperties properties) {
        this.properties = properties;
        
        // Configure formatters and converters
        ValueFormatter.configure(properties.getFormat());
        ExcelConverter.configure(properties);
        
        log.info("SimpliX Excel module initialized with configuration: {}", properties);
    }

    /**
     * JXLS template-based exporter bean
     */
    @Bean
    @ConditionalOnMissingBean
    public JxlsExporter<?> jxlsExporter() {
        JxlsExporterImpl<?> exporter = new JxlsExporterImpl<>(Object.class);
        exporter.template(properties.getTemplate().getPath());
        
        // Configure template options
        exporter.sheetName(properties.getTemplate().getDefaultSheetName());
        
        // Configure export options
        exporter.streaming(properties.getExport().isStreamingEnabled())
               .hideGridLines(properties.getExport().isHideGridLines())
               .rowAccessWindowSize(properties.getExport().getWindowSize())
               .enableFormulas(properties.getExport().isEnableFormulas());
        
        log.debug("JxlsExporter bean registered with configuration: template={}, streaming={}, windowSize={}",
                properties.getTemplate().getPath(),
                properties.getExport().isStreamingEnabled(),
                properties.getExport().getWindowSize());
        
        return exporter;
    }

    /**
     * CSV exporter bean
     */
    @Bean
    @ConditionalOnMissingBean
    public CsvExporter<?> csvExporter() {
        UnifiedCsvExporter<?> exporter = new UnifiedCsvExporter<>(Object.class);
        
        // Configure CSV options
        exporter.delimiter(properties.getCsv().getDelimiter())
                .encoding(properties.getCsv().getEncoding())
                .quoteStrings(properties.getCsv().isQuoteStrings())
                .includeHeader(properties.getCsv().isIncludeHeaders())
                .lineEnding(properties.getCsv().getLineSeparator());
        
        log.debug("CsvExporter bean registered with configuration: delimiter={}, encoding={}, quoteStrings={}",
                properties.getCsv().getDelimiter(),
                properties.getCsv().getEncoding(),
                properties.getCsv().isQuoteStrings());
        
        return exporter;
    }

    /**
     * Template generator bean
     */
    @Bean
    @ConditionalOnMissingBean
    public ExcelTemplateGenerator.TemplateOptions templateOptions() {
        return ExcelTemplateGenerator.TemplateOptions.builder()
                .sheetName(properties.getTemplate().getDefaultSheetName())
                .defaultColumnWidth(properties.getTemplate().getDefaultColumnWidth())
                .useJxlsMarkers(properties.getTemplate().isUseJxlsMarkers())
                .applyHeaderStyle(properties.getTemplate().isApplyHeaderStyle())
                .build();
    }

    /**
     * Initialize caches based on configuration
     */
    @Bean
    public void initializeCaches() {
        if (!properties.getCache().isTemplateCacheEnabled()) {
            ExcelTemplateManager.clearCache();
            log.debug("Template cache disabled");
        }
        
        if (!properties.getCache().isFieldCacheEnabled() || 
            !properties.getCache().isColumnCacheEnabled()) {
            // Clear converter caches if either is disabled
            dev.simplecore.simplix.excel.convert.ExcelConverter.clearCaches();
            log.debug("Converter caches cleared: fieldCache={}, columnCache={}", 
                    properties.getCache().isFieldCacheEnabled(),
                    properties.getCache().isColumnCacheEnabled());
        }
    }
} 