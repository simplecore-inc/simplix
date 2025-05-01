package dev.simplecore.simplix.excel.impl;

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
        // 1. 테스트용 데이터 준비
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "홍길동", "user1@example.com", LocalDate.of(2023, 1, 15)),
            new TestUser(2L, "김철수", "user2@example.com", LocalDate.of(2023, 2, 20))
        );
        
        // 2. 내보내기 실행
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .delimiter(",")
                .quoteStrings(true)
                .includeHeader(true);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. 결과 검증
        String csvContent = outputStream.toString("UTF-8");
        assertNotNull(csvContent);
        
        // 헤더 행이 있는지 확인
        assertTrue(csvContent.contains("ID,이름,이메일,가입일"));
        
        // 데이터 행이 있는지 확인
        assertTrue(csvContent.contains("1,\"홍길동\",\"user1@example.com\",2023-01-15"));
        assertTrue(csvContent.contains("2,\"김철수\",\"user2@example.com\",2023-02-20"));
    }
    
    @Test
    void testCsvExportWithCustomDelimiter() throws IOException {
        // 1. 테스트용 데이터 준비
        List<TestUser> users = Arrays.asList(
            new TestUser(1L, "홍길동", "user1@example.com", LocalDate.of(2023, 1, 15))
        );
        
        // 2. 내보내기 실행 (세미콜론 구분자 사용)
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .delimiter(";")
                .quoteStrings(true)
                .includeHeader(true);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        exporter.export(users, outputStream);
        
        // 3. 결과 검증
        String csvContent = outputStream.toString("UTF-8");
        
        // 세미콜론으로 구분된 헤더와 데이터 확인
        assertTrue(csvContent.contains("ID;이름;이메일;가입일"));
        assertTrue(csvContent.contains("1;\"홍길동\";\"user1@example.com\";2023-01-15"));
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
        UnifiedCsvExporter<TestUser> exporter = new UnifiedCsvExporter<>(TestUser.class);
        exporter.filename("test.csv")
                .encoding("UTF-8");
        exporter.export(users, response);
        
        // 4. 응답 확인
        assertEquals("text/csv", response.getContentType());
        assertEquals("attachment; filename=test.csv", 
                response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TestUser {
        @ExcelColumn(title = "ID", order = 1)
        private Long id;
        
        @ExcelColumn(title = "이름", order = 2)
        private String name;
        
        @ExcelColumn(title = "이메일", order = 3)
        private String email;
        
        @ExcelColumn(title = "가입일", order = 4, dateFormat = "yyyy-MM-dd")
        private LocalDate createdAt;
    }
} 