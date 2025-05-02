/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        private Long id;
        private String name;
        private String email;
        private LocalDate createdAt;
    }
} 