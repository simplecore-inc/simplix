/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JxlsExporterImplTest {

    @TempDir
    static Path tempDir;
    private static File templateFile;

    @BeforeEach
    void setupTemplate() throws IOException {
        templateFile = tempDir.resolve("test-template.xlsx").toFile();
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Data");
            try (FileOutputStream fos = new FileOutputStream(templateFile)) {
                workbook.write(fos);
            }
        }
    }

    @Test
    void testBasicExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "Jane Smith", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        // 2. Execute export
        JxlsExporterImpl<TestUser> exporter = new JxlsExporterImpl<>(TestUser.class);
        exporter.filename("test.xlsx")
                .template(templateFile.getAbsolutePath());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        byte[] excelData = outputStream.toByteArray();
        assertTrue(excelData.length > 0, "Excel data should be generated");
    }
    
    @Test
    void testHttpResponseExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "Test User", "test@example.com", LocalDate.now())
        );
        
        // 2. Prepare Mock HttpServletResponse
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 3. Execute export
        JxlsExporterImpl<TestUser> exporter = new JxlsExporterImpl<>(TestUser.class);
        exporter.filename("test.xlsx")
                .template(templateFile.getAbsolutePath());
        exporter.export(users, response);
        
        // 4. Verify response
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                response.getContentType());
        String contentDisposition = response.getHeader("Content-Disposition");
        assertNotNull(contentDisposition, "Content-Disposition header should not be null");
        assertTrue(contentDisposition.contains("filename"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
    
    @Test
    void testCustomSettings() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "Test User", "test@example.com", LocalDate.now())
        );
        
        // Export with custom settings
        JxlsExporterImpl<TestUser> exporter = new JxlsExporterImpl<>(TestUser.class);
        exporter.filename("customized.xlsx")
                .template(templateFile.getAbsolutePath())
                .enableFormulas(false)
                .streaming(true)
                .hideGridLines(true)
                .rowAccessWindowSize(500)
                .sheetName("CustomSheet");
        
        // Verify settings
        assertEquals("customized.xlsx", exporter.getFilename());
        assertEquals("CustomSheet", exporter.getSheetName());
        
        // Execute export
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        assertTrue(outputStream.toByteArray().length > 0);
    }
    
    @Test
    void fillsEachRowAndParameterIntoTheTemplate() throws IOException {
        File commandTemplate = tempDir.resolve("command-template.xlsx").toFile();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("${title}");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("${user.name}");
            row.createCell(1).setCellValue("${user.email}");
            addComment(workbook, sheet, 0, 0, "jx:area(lastCell=\"B2\")");
            addComment(workbook, sheet, 1, 0, "jx:each(items=\"items\" var=\"user\" lastCell=\"B2\")");
            try (FileOutputStream fos = new FileOutputStream(commandTemplate)) {
                workbook.write(fos);
            }
        }

        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "Jane Smith", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new JxlsExporterImpl<>(TestUser.class)
                .template(commandTemplate.getAbsolutePath())
                .parameter("title", "Users")
                .export(users, outputStream);

        try (Workbook result = new XSSFWorkbook(new ByteArrayInputStream(outputStream.toByteArray()))) {
            Sheet sheet = result.getSheet("Data");
            assertEquals("Users", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("John Doe", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("user1@example.com", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Jane Smith", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("user2@example.com", sheet.getRow(2).getCell(1).getStringCellValue());
        }
    }

    private static void addComment(Workbook workbook, Sheet sheet, int rowIndex, int columnIndex, String text) {
        CreationHelper helper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setRow1(rowIndex);
        anchor.setCol1(columnIndex);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        sheet.getRow(rowIndex).getCell(columnIndex).setCellComment(comment);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestUser {
        private Long id;
        private String name;
        private String email;
        private LocalDate createdAt;
    }
} 