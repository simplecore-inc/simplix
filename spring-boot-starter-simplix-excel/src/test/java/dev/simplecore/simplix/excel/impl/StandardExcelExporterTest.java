/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardExcelExporterTest {

    static {
        // Configure Apache POI to handle large files
        org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.001);
    }

    @Test
    void testBasicExport() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "Jane Smith", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("test.xlsx").sheetName("Users");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // 3.1 Check workbook and sheet
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Users", sheet.getSheetName());
            
            // 3.2 Check header row
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("ID", headerRow.getCell(0).getStringCellValue());
            assertEquals("Name", headerRow.getCell(1).getStringCellValue());
            assertEquals("Email", headerRow.getCell(2).getStringCellValue());
            assertEquals("Created Date", headerRow.getCell(3).getStringCellValue());
            
            // 3.3 Check data rows
            Row dataRow1 = sheet.getRow(1);
            assertNotNull(dataRow1);
            assertEquals(1.0, dataRow1.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("John Doe", dataRow1.getCell(1).getStringCellValue());
            assertEquals("user1@example.com", dataRow1.getCell(2).getStringCellValue());
            
            Row dataRow2 = sheet.getRow(2);
            assertNotNull(dataRow2);
            assertEquals(2.0, dataRow2.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("Jane Smith", dataRow2.getCell(1).getStringCellValue());
            assertEquals("user2@example.com", dataRow2.getCell(2).getStringCellValue());
        }
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
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("test.xlsx");
        exporter.export(users, response);
        
        // 4. Verify response
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                response.getContentType());
        assertEquals("attachment; filename=test.xlsx", 
                response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
    
    @Test
    void testEmptyCollection() throws IOException {
        // 1. Execute export with empty collection
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(null, outputStream);
        
        // 2. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // Check workbook and sheet
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            
            // Should have header row but no data rows
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            
            // No data rows should exist
            assertNull(sheet.getRow(1));
        }
    }
    
    @Test
    void testCustomStyles() throws IOException {
        // 1. Prepare test data
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "John Doe", "user1@example.com", LocalDate.of(2023, 1, 15))
        );
        
        // 2. Execute export with custom styles
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("test.xlsx");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            
            // Verify header exists
            assertNotNull(headerRow);
            assertNotNull(headerRow.getCell(0));
            
            // Verify data exists
            Row dataRow = sheet.getRow(1);
            assertNotNull(dataRow);
            assertNotNull(dataRow.getCell(0));
        }
    }
    
    @Test
    void testDataTypeConversion() throws IOException {
        // 1. Prepare test data with various types
        TestUserWithTypes user = new TestUserWithTypes(
            1L,
            true,
            123.45,
            LocalDate.of(2023, 1, 15),
            LocalDateTime.of(2023, 1, 15, 14, 30, 0),
            UserType.ADMIN
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUserWithTypes> exporter = new StandardExcelExporter<>(TestUserWithTypes.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user), outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            
            // Verify numeric values
            assertEquals(1.0, dataRow.getCell(0).getNumericCellValue(), 0.001);
            assertEquals(123.45, dataRow.getCell(2).getNumericCellValue(), 0.001);
            
            // Verify string values
            assertEquals("ADMIN", dataRow.getCell(5).getStringCellValue());
            
            // Verify date values
            assertTrue(DateUtil.isCellDateFormatted(dataRow.getCell(3)));
            assertTrue(DateUtil.isCellDateFormatted(dataRow.getCell(4)));
        }
    }
    
    @Test
    void testCustomFormatting() throws IOException {
        // 1. Prepare test data
        TestUserWithCustomFormat user = new TestUserWithCustomFormat(
            12345L,
            "John Doe",
            123456.789,
            LocalDate.of(2023, 1, 15)
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUserWithCustomFormat> exporter = new StandardExcelExporter<>(TestUserWithCustomFormat.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user), outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            
            // Verify numeric values are exported correctly
            Cell idCell = dataRow.getCell(0);
            Cell amountCell = dataRow.getCell(2);
            assertNotNull(idCell);
            assertNotNull(amountCell);
            assertEquals(12345.0, idCell.getNumericCellValue(), 0.001);
            assertEquals(123456.789, amountCell.getNumericCellValue(), 0.001);
            
            // Verify string value
            Cell nameCell = dataRow.getCell(1);
            assertNotNull(nameCell);
            assertEquals("John Doe", nameCell.getStringCellValue());
            
            // Verify date value exists
            Cell dateCell = dataRow.getCell(3);
            assertNotNull(dateCell);
            assertEquals(CellType.NUMERIC, dateCell.getCellType());
            
            // Verify date value
            double numericValue = dateCell.getNumericCellValue();
            Calendar cal = Calendar.getInstance();
            cal.setTime(DateUtil.getJavaDate(numericValue));
            assertEquals(2023, cal.get(Calendar.YEAR));
            assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
            assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        }
    }
    
    @Test
    void testExceptionHandling() {
        // Test null class
        assertThrows(NullPointerException.class, () -> {
            new StandardExcelExporter<>(null);
        });
        
        // Test export with null collection
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        assertDoesNotThrow(() -> {
            exporter.export(null, outputStream);
        });
        
        // Test export with null output stream
        List<TestUser> users = Arrays.asList(new TestUser(1L, "Test", "test@example.com", LocalDate.now()));
        assertThrows(NullPointerException.class, () -> {
            exporter.export(users, (OutputStream) null);
        });
    }
    
    @Test
    void testLargeDataSet() throws IOException {
        // 1. Prepare large test data
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            users.add(new TestUser(
                (long) i,
                "User " + i,
                "user" + i + "@example.com",
                LocalDate.now().minusDays(i)
            ));
        }
        
        // 2. Execute export with streaming mode
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("large_test.xlsx")
                .streaming(true)
                .windowSize(100);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        long startTime = System.currentTimeMillis();
        exporter.export(users, outputStream);
        long endTime = System.currentTimeMillis();
        
        // 3. Verify results
        assertTrue((endTime - startTime) < 5000); // Should complete within 5 seconds
        assertTrue(outputStream.size() > 0);
        
        // 4. Verify file content
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(10001, sheet.getPhysicalNumberOfRows()); // Including header row
            
            // Check some random rows
            Row row5000 = sheet.getRow(5000);
            assertEquals(4999.0, row5000.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("User 4999", row5000.getCell(1).getStringCellValue());
        }
    }
    
    @Test
    void testHeaderOrderAndVisibility() throws IOException {
        // 1. Prepare test data
        LocalDate testDate = LocalDate.of(2023, 1, 15);
        TestUserWithColumnOrder user = new TestUserWithColumnOrder(
            1L, "John Doe", "user1@example.com", "ACTIVE", testDate
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUserWithColumnOrder> exporter = new StandardExcelExporter<>(TestUserWithColumnOrder.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user), outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Verify header row
            Row headerRow = sheet.getRow(0);
            assertEquals("ID", headerRow.getCell(0).getStringCellValue());
            assertEquals("Name", headerRow.getCell(1).getStringCellValue());
            assertEquals("Email", headerRow.getCell(2).getStringCellValue());
            assertEquals("Created Date", headerRow.getCell(3).getStringCellValue());
            
            // Verify data row
            Row dataRow = sheet.getRow(1);
            assertEquals(1.0, dataRow.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("John Doe", dataRow.getCell(1).getStringCellValue());
            assertEquals("user1@example.com", dataRow.getCell(2).getStringCellValue());
            
            // Verify date cell
            Cell dateCell = dataRow.getCell(3);
            assertTrue(DateUtil.isCellDateFormatted(dateCell), "Cell should be formatted as date");
            
            // Convert Excel date to LocalDate
            Date cellDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
            LocalDate actualDate = cellDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
            
            assertEquals(testDate, actualDate, "Date values should match");
            
            // Verify ignored field is not present
            assertNull(dataRow.getCell(4), "Ignored field should not be present");
        }
    }
    
    @Test
    void testInternationalization() throws IOException {
        // 1. Prepare test data with various languages
        TestUserMultiLingual user = new TestUserMultiLingual(
            1L,
            "홍길동", // Korean name
            "张三",   // Chinese name
            "user1@example.com",
            "안녕하세요", // Korean description
            "你好"      // Chinese description
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUserMultiLingual> exporter = new StandardExcelExporter<>(TestUserMultiLingual.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user), outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            
            // Verify multi-language content
            assertEquals("홍길동", dataRow.getCell(1).getStringCellValue());
            assertEquals("张三", dataRow.getCell(2).getStringCellValue());
            assertEquals("안녕하세요", dataRow.getCell(4).getStringCellValue());
            assertEquals("你好", dataRow.getCell(5).getStringCellValue());
        }
    }
    
    @Test
    void testConditionalFormatting() throws IOException {
        // 1. Prepare test data (숫자 데이터 유효성만 테스트)
        TestUserWithScore user1 = new TestUserWithScore(1L, "John", 95.5);
        TestUserWithScore user2 = new TestUserWithScore(2L, "Jane", 75.0);
        TestUserWithScore user3 = new TestUserWithScore(3L, "Bob", 45.5);
        
        // 2. Execute export
        StandardExcelExporter<TestUserWithScore> exporter = new StandardExcelExporter<>(TestUserWithScore.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user1, user2, user3), outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Verify score values
            assertEquals(95.5, sheet.getRow(1).getCell(2).getNumericCellValue(), 0.001);
            assertEquals(75.0, sheet.getRow(2).getCell(2).getNumericCellValue(), 0.001);
            assertEquals(45.5, sheet.getRow(3).getCell(2).getNumericCellValue(), 0.001);
        }
    }
    
    @Test
    void testCellMerging() throws IOException {
        // 1. Prepare test data with duplicate values (셀 병합 없이 데이터 정확성만 테스트)
        List<TestUserWithDepartment> users = Arrays.asList(
            new TestUserWithDepartment(1L, "IT", "Developer", "John"),
            new TestUserWithDepartment(2L, "IT", "Developer", "Jane"),
            new TestUserWithDepartment(3L, "IT", "Manager", "Bob"),
            new TestUserWithDepartment(4L, "HR", "Manager", "Alice")
        );
        
        // 2. Execute export
        StandardExcelExporter<TestUserWithDepartment> exporter = new StandardExcelExporter<>(TestUserWithDepartment.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Verify all data values are correctly exported
            assertEquals("IT", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Developer", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("John", sheet.getRow(1).getCell(3).getStringCellValue());
            
            assertEquals("IT", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals("Developer", sheet.getRow(2).getCell(2).getStringCellValue());
            assertEquals("Jane", sheet.getRow(2).getCell(3).getStringCellValue());
            
            assertEquals("IT", sheet.getRow(3).getCell(1).getStringCellValue());
            assertEquals("Manager", sheet.getRow(3).getCell(2).getStringCellValue());
            assertEquals("Bob", sheet.getRow(3).getCell(3).getStringCellValue());
            
            assertEquals("HR", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("Manager", sheet.getRow(4).getCell(2).getStringCellValue());
            assertEquals("Alice", sheet.getRow(4).getCell(3).getStringCellValue());
        }
    }
    
    @Test
    void testValidation() {
        // Test with null class
        assertThrows(NullPointerException.class, () -> {
            new StandardExcelExporter<>(null);
        });

        // Test with null output stream
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        List<TestUser> users = Arrays.asList(new TestUser(1L, "Test", "test@example.com", LocalDate.now()));
        assertThrows(NullPointerException.class, () -> {
            exporter.export(users, (OutputStream) null);
        });
    }
    
    @Test
    void testStreamingPerformance() throws IOException {
        // 1. Prepare large test data
        int dataSize = 50000;
        List<TestUser> users = new ArrayList<>();
        for (int i = 0; i < dataSize; i++) {
            users.add(new TestUser(
                (long) i,
                "User " + i,
                "user" + i + "@example.com",
                LocalDate.now().minusDays(i % 365)
            ));
        }

        // 2. Test normal mode
        StandardExcelExporter<TestUser> normalExporter = new StandardExcelExporter<>(TestUser.class);
        normalExporter.filename("normal_test.xlsx").streaming(false);
        
        ByteArrayOutputStream normalOutputStream = new ByteArrayOutputStream();
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Request GC before memory measurement
        long normalStartMemory = runtime.totalMemory() - runtime.freeMemory();
        long normalStartTime = System.currentTimeMillis();
        
        normalExporter.export(users, normalOutputStream);
        
        long normalEndTime = System.currentTimeMillis();
        runtime.gc(); // Request GC before memory measurement
        long normalEndMemory = runtime.totalMemory() - runtime.freeMemory();
        long normalMemoryUsed = normalEndMemory - normalStartMemory;
        long normalTimeElapsed = normalEndTime - normalStartTime;

        // 3. Test streaming mode
        StandardExcelExporter<TestUser> streamingExporter = new StandardExcelExporter<>(TestUser.class);
        streamingExporter.filename("streaming_test.xlsx")
                        .streaming(true)
                        .windowSize(1000);
        
        ByteArrayOutputStream streamingOutputStream = new ByteArrayOutputStream();
        runtime.gc(); // Request GC before memory measurement
        long streamingStartMemory = runtime.totalMemory() - runtime.freeMemory();
        long streamingStartTime = System.currentTimeMillis();
        
        streamingExporter.export(users, streamingOutputStream);
        
        long streamingEndTime = System.currentTimeMillis();
        runtime.gc(); // Request GC before memory measurement
        long streamingEndMemory = runtime.totalMemory() - runtime.freeMemory();
        long streamingMemoryUsed = streamingEndMemory - streamingStartMemory;
        long streamingTimeElapsed = streamingEndTime - streamingStartTime;

        // 4. Verify results
        assertTrue(streamingMemoryUsed <= normalMemoryUsed * 1.2, 
            "Streaming mode should use similar or less memory than normal mode");
        assertTrue(streamingTimeElapsed <= normalTimeElapsed * 1.5, 
            "Streaming mode should not be significantly slower than normal mode");
        
        // Verify file contents for both modes
        verifyExcelContent(new ByteArrayInputStream(normalOutputStream.toByteArray()), dataSize);
        verifyExcelContent(new ByteArrayInputStream(streamingOutputStream.toByteArray()), dataSize);
    }

    private void verifyExcelContent(ByteArrayInputStream inputStream, int expectedRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(expectedRows + 1, sheet.getPhysicalNumberOfRows()); // Including header
            
            // Verify random rows
            int[] rowsToCheck = {1, expectedRows / 2, expectedRows};
            for (int rowNum : rowsToCheck) {
                if (rowNum < expectedRows) {
                    Row row = sheet.getRow(rowNum);
                    assertNotNull(row);
                    assertEquals((double)(rowNum - 1), row.getCell(0).getNumericCellValue(), 0.001);
                    assertEquals("User " + (rowNum - 1), row.getCell(1).getStringCellValue());
                }
            }
        }
    }

    @Test
    void testEdgeCases() throws IOException {
        // 1. Prepare test data with edge cases
        TestUserWithEdgeCases user = new TestUserWithEdgeCases(
            Long.MAX_VALUE, // Maximum long value
            "A".repeat(32767), // Maximum string length in Excel
            generateLongEmail(32767), // Long email with special characters
            LocalDate.of(9999, 12, 31), // Maximum date in Excel
            "!@#$%^&*()_+-=[]{}|;:'\",.<>?/~`", // Special characters
            null // Null value
        );

        // 2. Execute export
        StandardExcelExporter<TestUserWithEdgeCases> exporter = new StandardExcelExporter<>(TestUserWithEdgeCases.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(Arrays.asList(user), outputStream);

        // 3. Verify results
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);

            // Verify maximum long value
            Cell idCell = dataRow.getCell(0);
            assertEquals(CellType.NUMERIC, idCell.getCellType());
            assertEquals((double)Long.MAX_VALUE, idCell.getNumericCellValue(), 0.001);

            // Verify maximum length string is truncated or handled
            String longString = dataRow.getCell(1).getStringCellValue();
            assertTrue(longString.length() <= 32767);
            assertTrue(longString.startsWith("A"));

            // Verify long email with special characters
            String email = dataRow.getCell(2).getStringCellValue();
            assertNotNull(email);
            assertTrue(email.endsWith("@test.com"));

            // Verify maximum date
            Cell dateCell = dataRow.getCell(3);
            assertTrue(DateUtil.isCellDateFormatted(dateCell));
            Date cellDate = DateUtil.getJavaDate(dateCell.getNumericCellValue());
            Calendar cal = Calendar.getInstance();
            cal.setTime(cellDate);
            assertEquals(9999, cal.get(Calendar.YEAR));
            assertEquals(11, cal.get(Calendar.MONTH)); // December is 11
            assertEquals(31, cal.get(Calendar.DAY_OF_MONTH));

            // Verify special characters
            assertEquals("!@#$%^&*()_+-=[]{}|;:'\",.<>?/~`", 
                dataRow.getCell(4).getStringCellValue());

            // Verify null handling
            Cell nullCell = dataRow.getCell(5);
            assertEquals(CellType.BLANK, nullCell.getCellType());
        }
    }

    private String generateLongEmail(int length) {
        StringBuilder email = new StringBuilder();
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&'*+-/=?^_`{|}~.@";
        for (int i = 0; i < length - 10; i++) {
            email.append(chars.charAt(i % chars.length()));
        }
        return email.append("@test.com").toString();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        @ExcelColumn(name = "ID", order = 1, width = 10)
        private Long id;
        
        @ExcelColumn(name = "Name", order = 2, width = 15, bold = true)
        private String name;
        
        @ExcelColumn(name = "Email", order = 3, width = 25)
        private String email;
        
        @ExcelColumn(name = "Created Date", order = 4, width = 20, format = "yyyy-MM-dd")
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
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithCustomFormat {
        @ExcelColumn(name = "ID", order = 1, format = "#,###")
        private Long id;
        
        @ExcelColumn(name = "Name", order = 2)
        private String name;
        
        @ExcelColumn(name = "Amount", order = 3, format = "#,##0.00")
        private Double amount;
        
        @ExcelColumn(name = "Date", order = 4, format = "yyyy년 MM월 dd일")
        private LocalDate date;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithColumnOrder {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Name", order = 2)
        private String name;
        
        @ExcelColumn(name = "Email", order = 3)
        private String email;
        
        @ExcelColumn(name = "Status", ignore = true)
        private String status;
        
        @ExcelColumn(name = "Created Date", order = 4, format = "yyyy-MM-dd")
        private LocalDate createdAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserMultiLingual {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Korean Name", order = 2)
        private String koreanName;
        
        @ExcelColumn(name = "Chinese Name", order = 3)
        private String chineseName;
        
        @ExcelColumn(name = "Email", order = 4)
        private String email;
        
        @ExcelColumn(name = "Korean Description", order = 5)
        private String koreanDescription;
        
        @ExcelColumn(name = "Chinese Description", order = 6)
        private String chineseDescription;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithScore {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Name", order = 2)
        private String name;
        
        @ExcelColumn(name = "Score", order = 3)
        private Double score;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithDepartment {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Department", order = 2)
        private String department;
        
        @ExcelColumn(name = "Position", order = 3)
        private String position;
        
        @ExcelColumn(name = "Name", order = 4)
        private String name;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithInvalidType {
        @ExcelColumn(name = "Invalid", order = 1)
        private Object invalidType;  // Object type is not supported
    }
    
    @Data
    @NoArgsConstructor
    static class TestUserWithDuplicateOrder {
        @ExcelColumn(name = "First", order = 1)
        private String first;
        
        @ExcelColumn(name = "Second", order = 1)  // Same order as first
        private String second;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithEdgeCases {
        @ExcelColumn(name = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(name = "Very Long Name", order = 2)
        private String longName;
        
        @ExcelColumn(name = "Long Email", order = 3)
        private String longEmail;
        
        @ExcelColumn(name = "Max Date", order = 4, format = "yyyy-MM-dd")
        private LocalDate maxDate;
        
        @ExcelColumn(name = "Special Chars", order = 5)
        private String specialChars;
        
        @ExcelColumn(name = "Null Value", order = 6)
        private String nullValue;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUserWithoutAnnotation {
        private Long id;
        private String name;
    }
    
    enum UserType {
        ADMIN, USER, GUEST
    }
} 