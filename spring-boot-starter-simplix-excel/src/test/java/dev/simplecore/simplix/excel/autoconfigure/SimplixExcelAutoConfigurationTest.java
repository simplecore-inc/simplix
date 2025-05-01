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
            // 기본 빈들이 등록되었는지 확인
            assertThat(context).hasSingleBean(JxlsExporter.class);
            assertThat(context).hasSingleBean(CsvExporter.class);
            assertThat(context).hasSingleBean(SimplixExcelProperties.class);
            
            // 기본 속성값 확인
            SimplixExcelProperties properties = context.getBean(SimplixExcelProperties.class);
            assertThat(properties.getDefaultTemplatePath()).isEqualTo("templates/default-template.xlsx");
            assertThat(properties.getDefaultSheetName()).isEqualTo("Data");
            assertThat(properties.getCsvDelimiter()).isEqualTo(",");
        });
    }
    
    @Test
    void testCustomProperties() {
        contextRunner
            .withPropertyValues(
                "simplix.excel.default-template-path=templates/custom-template.xlsx",
                "simplix.excel.default-sheet-name=CustomSheet",
                "simplix.excel.csv-delimiter=;",
                "simplix.excel.csv-encoding=UTF-8",
                "simplix.excel.streaming-mode-enabled=true"
            )
            .run(context -> {
                // 사용자 정의 속성값 확인
                SimplixExcelProperties properties = context.getBean(SimplixExcelProperties.class);
                assertThat(properties.getDefaultTemplatePath()).isEqualTo("templates/custom-template.xlsx");
                assertThat(properties.getDefaultSheetName()).isEqualTo("CustomSheet");
                assertThat(properties.getCsvDelimiter()).isEqualTo(";");
                assertThat(properties.getCsvEncoding()).isEqualTo("UTF-8");
                assertThat(properties.isStreamingModeEnabled()).isTrue();
            });
    }
} 