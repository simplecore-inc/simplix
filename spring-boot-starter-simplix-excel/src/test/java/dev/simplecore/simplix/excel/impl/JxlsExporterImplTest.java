/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.template.DefaultTemplateInitializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JxlsExporterImplTest {

    @BeforeAll
    static void setup() {
        // Initialize default template
        DefaultTemplateInitializer.initializeDefaultTemplate();
    }

    @Test
    void testBasicExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "Jane Smith", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        Map<String, Object> model = new HashMap<>();
        model.put("users", users);
        model.put("title", "User List");
        
        // 2. Execute export
        JxlsExporterImpl exporter = new JxlsExporterImpl();
        exporter.filename("test.xlsx");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(model, outputStream);
        
        // 3. Verify results
        byte[] excelData = outputStream.toByteArray();
        assertTrue(excelData.length > 0, "Excel data should be generated");
    }
    
    @Test
    void testHttpResponseExport() throws IOException {
        // 1. Prepare test data
        Map<String, Object> model = new HashMap<>();
        model.put("title", "Test Document");
        
        // 2. Prepare Mock HttpServletResponse
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 3. Execute export
        JxlsExporterImpl exporter = new JxlsExporterImpl();
        exporter.filename("test.xlsx");
        exporter.export(model, response);
        
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
        // Export with custom settings
        JxlsExporterImpl exporter = new JxlsExporterImpl();
        exporter.filename("customized.xlsx")
                .template("templates/default-template.xlsx")
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
        Map<String, Object> model = new HashMap<>();
        exporter.export(model, outputStream);
        
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