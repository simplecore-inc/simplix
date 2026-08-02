/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import dev.simplecore.simplix.excel.i18n.ExcelLabels;
import dev.simplecore.simplix.excel.properties.SimplixExcelProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an exported file reads as: translated headings, enum labels, and rows that survive a
 * value carrying the delimiter.
 */
class ExporterLabelAndEscapingTest {

    enum Standing {
        ACTIVE
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Row2 {
        @ExcelColumn(name = "{entities.Contract.customer}", order = 0)
        private String customer;

        @ExcelColumn(name = "Plain heading", order = 1)
        private String note;

        @ExcelColumn(name = "{entities.Contract.status}", order = 2)
        private Standing status;

        @ExcelColumn(name = "{entities.Contract.active}", order = 3)
        private Boolean active;
    }

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("entities.Contract.customer", Locale.ENGLISH, "Customer");
        messages.addMessage("entities.Contract.status", Locale.ENGLISH, "Status");
        messages.addMessage("entities.Contract.active", Locale.ENGLISH, "In force");
        messages.addMessage("enums.Standing.ACTIVE", Locale.ENGLISH, "In use");
        ExcelLabels.configure(messages, List.of(), new SimplixExcelProperties.FormatProperties());
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        ExcelLabels.reset();
    }

    @Test
    void csvHeadersAreTranslatedAndEnumsAreLabelled() throws IOException {
        UnifiedCsvExporter<Row2> exporter = new UnifiedCsvExporter<>(Row2.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        exporter.export(List.of(new Row2("Acme", "note", Standing.ACTIVE, true)), bytes);

        String csv = bytes.toString("UTF-8");
        assertThat(csv).startsWith("\"Customer\",\"Plain heading\",\"Status\",\"In force\"");
        assertThat(csv).contains("\"Acme\",\"note\",In use,Y");
    }

    @Test
    void csvQuotesACellCarryingTheDelimiterOrAQuote() throws IOException {
        UnifiedCsvExporter<Row2> exporter = new UnifiedCsvExporter<>(Row2.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        exporter.export(List.of(new Row2("Acme, Inc. \"AC\"", "x", Standing.ACTIVE, false)), bytes);

        String csv = bytes.toString("UTF-8");
        assertThat(csv).contains("\"Acme, Inc. \"\"AC\"\"\"");
    }

    @Test
    void csvKeepsAFormulaFromBeingEvaluated() throws IOException {
        UnifiedCsvExporter<Row2> exporter = new UnifiedCsvExporter<>(Row2.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        exporter.export(List.of(new Row2("=1+1", "x", Standing.ACTIVE, false)), bytes);

        assertThat(bytes.toString("UTF-8")).contains("\"'=1+1\"");
    }

    @Test
    void excelHeadersAreTranslatedAndEnumsAreLabelled() throws IOException {
        StandardExcelExporter<Row2> exporter = new StandardExcelExporter<>(Row2.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        exporter.export(List.of(new Row2("Acme", "note", Standing.ACTIVE, true)), bytes);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Customer");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Plain heading");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Status");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("In force");

            Row first = sheet.getRow(1);
            assertThat(first.getCell(2).getStringCellValue()).isEqualTo("In use");
            assertThat(first.getCell(3).getStringCellValue()).isEqualTo("Y");
        }
    }
}
