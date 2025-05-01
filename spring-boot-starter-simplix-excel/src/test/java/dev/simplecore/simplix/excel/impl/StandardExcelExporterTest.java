package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class StandardExcelExporterTest {

    @Test
    void testBasicExport() throws IOException {
        // 1. 테스트용 데이터 준비
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "홍길동", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "김철수", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        // 2. 내보내기 실행
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("test.xlsx").sheetName("Users");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. 결과 검증
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // 3.1 워크북과 시트 확인
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Users", sheet.getSheetName());
            
            // 3.2 헤더 행 확인
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("ID", headerRow.getCell(0).getStringCellValue());
            assertEquals("이름", headerRow.getCell(1).getStringCellValue());
            assertEquals("이메일", headerRow.getCell(2).getStringCellValue());
            assertEquals("가입일", headerRow.getCell(3).getStringCellValue());
            
            // 3.3 데이터 행 확인
            Row dataRow1 = sheet.getRow(1);
            assertNotNull(dataRow1);
            assertEquals(1.0, dataRow1.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("홍길동", dataRow1.getCell(1).getStringCellValue());
            assertEquals("user1@example.com", dataRow1.getCell(2).getStringCellValue());
            
            Row dataRow2 = sheet.getRow(2);
            assertNotNull(dataRow2);
            assertEquals(2.0, dataRow2.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("김철수", dataRow2.getCell(1).getStringCellValue());
            assertEquals("user2@example.com", dataRow2.getCell(2).getStringCellValue());
        }
    }
    
    @Test
    void testHttpResponseExport() throws IOException {
        // 1. 테스트용 데이터 준비
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "홍길동", "user1@example.com", LocalDate.of(2023, 1, 15))
        );
        
        // 2. Mock HttpServletResponse 준비
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // 3. 내보내기 실행
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        exporter.filename("test.xlsx");
        exporter.export(users, response);
        
        // 4. 응답 확인
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 
                response.getContentType());
        assertEquals("attachment; filename=test.xlsx", 
                response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
    
    @Test
    void testEmptyCollection() throws IOException {
        // 1. 내보내기 실행 (빈 컬렉션)
        StandardExcelExporter<TestUser> exporter = new StandardExcelExporter<>(TestUser.class);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(null, outputStream);
        
        // 2. 결과 검증
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // 워크북과 시트 확인
            assertNotNull(workbook);
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            
            // 헤더 행만 있고 데이터 행은 없어야 함
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            
            // 데이터 행이 없어야 함
            assertNull(sheet.getRow(1));
        }
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        @ExcelColumn(title = "ID", order = 1, width = 10)
        private Long id;
        
        @ExcelColumn(title = "이름", order = 2, width = 15, bold = true)
        private String name;
        
        @ExcelColumn(title = "이메일", order = 3, width = 25)
        private String email;
        
        @ExcelColumn(title = "가입일", order = 4, width = 20, dateFormat = "yyyy-MM-dd")
        private LocalDate createdAt;
    }
} 