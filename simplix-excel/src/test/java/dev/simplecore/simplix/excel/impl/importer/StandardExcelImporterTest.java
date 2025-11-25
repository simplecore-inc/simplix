/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl.importer;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardExcelImporterTest {

    @Test
    void testBasicExcelImport() throws IOException {
        // 1. Create test Excel file
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("Email");
            headerRow.createCell(3).setCellValue("Created Date");
            
            // Create data row
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(1);
            dataRow.createCell(1).setCellValue("John Doe");
            dataRow.createCell(2).setCellValue("john@example.com");
            
            Cell dateCell = dataRow.createCell(3);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat((short) 14); // mm/dd/yyyy
            dateCell.setCellStyle(dateStyle);
            dateCell.setCellValue(LocalDate.of(2023, 1, 15));
            
            workbook.write(outputStream);
        }
        
        // 2. Create importer and import data
        StandardExcelImporter<TestUser> importer = new StandardExcelImporter<>(TestUser.class);
        MockMultipartFile file = new MockMultipartFile(
            "users.xlsx", "users.xlsx", 
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            outputStream.toByteArray()
        );
        
        List<TestUser> users = importer.importFromExcel(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUser user = users.get(0);
        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals(LocalDate.of(2023, 1, 15), user.getCreatedAt());
    }
    
    @Test
    void testCustomColumnMapping() throws IOException {
        // 1. Create test Excel file with different column order
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");
            
            // Create header row with different order
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Email");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("Created Date");
            headerRow.createCell(3).setCellValue("ID");
            
            // Create data row
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("john@example.com");
            dataRow.createCell(1).setCellValue("John Doe");
            
            Cell dateCell = dataRow.createCell(2);
            dateCell.setCellValue(LocalDate.of(2023, 1, 15));
            
            dataRow.createCell(3).setCellValue(1);
            
            workbook.write(outputStream);
        }
        
        // 2. Create importer with custom mapping
        StandardExcelImporter<TestUser> importer = new StandardExcelImporter<>(TestUser.class);
        Map<Integer, String> columnMapping = new HashMap<>();
        columnMapping.put(0, "email");
        columnMapping.put(1, "name");
        columnMapping.put(2, "createdAt");
        columnMapping.put(3, "id");
        importer.setColumnMapping(columnMapping);
        
        MockMultipartFile file = new MockMultipartFile(
            "users.xlsx", "users.xlsx", 
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            outputStream.toByteArray()
        );
        
        List<TestUser> users = importer.importFromExcel(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUser user = users.get(0);
        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals(LocalDate.of(2023, 1, 15), user.getCreatedAt());
    }
    
    @Test
    void testDataTypeConversion() throws IOException {
        // 1. Create test Excel file with various data types
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Active");
            headerRow.createCell(2).setCellValue("Score");
            headerRow.createCell(3).setCellValue("Date");
            headerRow.createCell(4).setCellValue("DateTime");
            headerRow.createCell(5).setCellValue("Type");
            
            // Create data row
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue(1);
            dataRow.createCell(1).setCellValue("true");
            dataRow.createCell(2).setCellValue(95.5);
            
            Cell dateCell = dataRow.createCell(3);
            dateCell.setCellValue(LocalDate.of(2023, 1, 15));
            
            Cell dateTimeCell = dataRow.createCell(4);
            dateTimeCell.setCellValue(LocalDateTime.of(2023, 1, 15, 14, 30));
            
            dataRow.createCell(5).setCellValue("ADMIN");
            
            workbook.write(outputStream);
        }
        
        // 2. Create importer and import data
        StandardExcelImporter<TestUserWithTypes> importer = new StandardExcelImporter<>(TestUserWithTypes.class);
        MockMultipartFile file = new MockMultipartFile(
            "users.xlsx", "users.xlsx", 
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            outputStream.toByteArray()
        );
        
        List<TestUserWithTypes> users = importer.importFromExcel(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUserWithTypes user = users.get(0);
        assertEquals(1L, user.getId());
        assertTrue(user.getActive());
        assertEquals(95.5, user.getScore(), 0.001);
        assertEquals(LocalDate.of(2023, 1, 15), user.getDate());
        assertEquals(LocalDateTime.of(2023, 1, 15, 14, 30), user.getDateTime());
        assertEquals(UserType.ADMIN, user.getType());
    }
    
    @Test
    void testBasicCsvImport() throws IOException {
        // 1. Create test CSV content
        String csvContent = "ID,Name,Email,Created Date\n" +
                          "1,John Doe,john@example.com,2023-01-15";
        
        // 2. Create importer and import data
        StandardExcelImporter<TestUser> importer = new StandardExcelImporter<>(TestUser.class);
        MockMultipartFile file = new MockMultipartFile(
            "users.csv", "users.csv", 
            "text/csv",
            csvContent.getBytes()
        );
        
        List<TestUser> users = importer.importFromCsv(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUser user = users.get(0);
        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals(LocalDate.of(2023, 1, 15), user.getCreatedAt());
    }
    
    @Test
    void testCsvDataTypeConversion() throws IOException {
        // 1. Create test CSV content with various data types
        String csvContent = "ID,Active,Score,Date,DateTime,Type\n" +
                          "1,true,95.5,2023-01-15,2023-01-15 14:30:00,ADMIN";
        
        // 2. Create importer and import data
        StandardExcelImporter<TestUserWithTypes> importer = new StandardExcelImporter<>(TestUserWithTypes.class);
        MockMultipartFile file = new MockMultipartFile(
            "users.csv", "users.csv", 
            "text/csv",
            csvContent.getBytes()
        );
        
        List<TestUserWithTypes> users = importer.importFromCsv(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUserWithTypes user = users.get(0);
        assertEquals(1L, user.getId());
        assertTrue(user.getActive());
        assertEquals(95.5, user.getScore(), 0.001);
        assertEquals(LocalDate.of(2023, 1, 15), user.getDate());
        assertEquals(LocalDateTime.of(2023, 1, 15, 14, 30), user.getDateTime());
        assertEquals(UserType.ADMIN, user.getType());
    }
    
    @Test
    void testCsvCustomColumnMapping() throws IOException {
        // 1. Create test CSV content with different column order
        String csvContent = "Email,Name,Created Date,ID\n" +
                          "john@example.com,John Doe,2023-01-15,1";
        
        // 2. Create importer with custom mapping
        StandardExcelImporter<TestUser> importer = new StandardExcelImporter<>(TestUser.class);
        Map<Integer, String> columnMapping = new HashMap<>();
        columnMapping.put(0, "email");
        columnMapping.put(1, "name");
        columnMapping.put(2, "createdAt");
        columnMapping.put(3, "id");
        importer.setColumnMapping(columnMapping);
        
        MockMultipartFile file = new MockMultipartFile(
            "users.csv", "users.csv", 
            "text/csv",
            csvContent.getBytes()
        );
        
        List<TestUser> users = importer.importFromCsv(file);
        
        // 3. Verify results
        assertEquals(1, users.size());
        TestUser user = users.get(0);
        assertEquals(1L, user.getId());
        assertEquals("John Doe", user.getName());
        assertEquals("john@example.com", user.getEmail());
        assertEquals(LocalDate.of(2023, 1, 15), user.getCreatedAt());
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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithTypes {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Active", order = 2)
        private Boolean active;
        
        @ExcelColumn(name = "Score", order = 3)
        private Double score;
        
        @ExcelColumn(name = "Date", order = 4, format = "yyyy-MM-dd")
        private LocalDate date;
        
        @ExcelColumn(name = "DateTime", order = 5, format = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateTime;
        
        @ExcelColumn(name = "Type", order = 6)
        private UserType type;
    }
    
    enum UserType {
        ADMIN, USER, GUEST
    }
} 