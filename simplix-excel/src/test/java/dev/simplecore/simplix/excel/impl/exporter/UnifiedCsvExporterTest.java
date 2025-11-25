/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.exporter;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedCsvExporterTest {

    @Test
    void testBasicCsvExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "Jane Smith", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        // 2. Execute export
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .delimiter(",")
                .quoteStrings(true)
                .includeHeader(true);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        String csvContent = outputStream.toString("UTF-8");
        assertNotNull(csvContent);
        
        // Check header row
        assertTrue(csvContent.contains("\"ID\",\"Name\",\"Email\",\"Created Date\""));
        
        // Check data rows
        assertTrue(csvContent.contains("1,\"John Doe\",\"user1@example.com\",\"2023-01-15\""));
        assertTrue(csvContent.contains("2,\"Jane Smith\",\"user2@example.com\",\"2023-02-20\""));
    }
    
    @Test
    void testCsvExportWithCustomDelimiter() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15))
        );
        
        // 2. Execute export with semicolon delimiter
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .delimiter(";")
                .quoteStrings(true)
                .includeHeader(true);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        String csvContent = outputStream.toString("UTF-8");
        
        // Check header and data with semicolon delimiter
        assertTrue(csvContent.contains("\"ID\";\"Name\";\"Email\";\"Created Date\""));
        assertTrue(csvContent.contains("1;\"John Doe\";\"user1@example.com\";\"2023-01-15\""));
    }
    
    @Test
    void testHttpResponseExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15))
        );
        
        // 2. Prepare Mock HttpServletResponse
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 3. Execute export
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .encoding("UTF-8");
        exporter.export(users, response);
        
        // 4. Verify response
        assertEquals("text/csv; charset=UTF-8", response.getContentType());
        assertEquals("attachment; filename=test.csv", 
                response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Name", order = 2)
        private String name;
        
        @ExcelColumn(name = "Email", order = 3)
        private String email;
        
        @ExcelColumn(name = "Created Date", order = 4, format = "yyyy-MM-dd")
        private LocalDate createdAt;
    }
} 