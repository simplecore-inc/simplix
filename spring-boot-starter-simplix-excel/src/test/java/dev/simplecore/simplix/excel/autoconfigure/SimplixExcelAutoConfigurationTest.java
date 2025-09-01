/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.autoconfigure;

import dev.simplecore.simplix.excel.api.CsvExporter;
import dev.simplecore.simplix.excel.api.JxlsExporter;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SimplixExcelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimplixExcelAutoConfiguration.class));

    @Test
    void testDefaultConfiguration() {
        contextRunner.run(context -> {
            // Check if default beans are registered
            assertThat(context).hasSingleBean(JxlsExporter.class);
            assertThat(context).hasSingleBean(CsvExporter.class);
            assertThat(context).hasSingleBean(SimplixExcelProperties.class);
            
            // Check default property values
            SimplixExcelProperties properties = context.getBean(SimplixExcelProperties.class);
            assertThat(properties.getTemplate().getPath()).isEqualTo("templates/default-template.xlsx");
            assertThat(properties.getExport().getDefaultSheetName()).isEqualTo("Data");
            assertThat(properties.getCsv().getDelimiter()).isEqualTo(",");
        });
    }
    
    @Test
    void testCustomProperties() {
        contextRunner
            .withPropertyValues(
                "simplix.excel.template.path=templates/custom-template.xlsx",
                "simplix.excel.export.default-sheet-name=CustomSheet",
                "simplix.excel.csv.delimiter=;",
                "simplix.excel.csv.encoding=UTF-8",
                "simplix.excel.export.streaming-enabled=true"
            )
            .run(context -> {
                // Check custom property values
                SimplixExcelProperties properties = context.getBean(SimplixExcelProperties.class);
                assertThat(properties.getTemplate().getPath()).isEqualTo("templates/custom-template.xlsx");
                assertThat(properties.getExport().getDefaultSheetName()).isEqualTo("CustomSheet");
                assertThat(properties.getCsv().getDelimiter()).isEqualTo(";");
                assertThat(properties.getCsv().getEncoding()).isEqualTo("UTF-8");
                assertThat(properties.getExport().isStreamingEnabled()).isTrue();
            });
    }
} 