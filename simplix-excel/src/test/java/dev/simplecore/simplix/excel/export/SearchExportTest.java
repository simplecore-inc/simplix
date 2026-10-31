/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.export;

import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An export holds every row the search matches, in both formats, with the search's own filters.
 */
class SearchExportTest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        @ExcelColumn(name = "Number", order = 0)
        private String number;
    }

    private static final int TOTAL = 1234;
    private static final int PAGE_SIZE = 100;

    private final List<Map<String, String>> asked = new ArrayList<>();

    /** A search over TOTAL rows that honours page and size and records what it was asked. */
    private Page<Line> search(Map<String, String> params) {
        asked.add(new LinkedHashMap<>(params));
        int page = Integer.parseInt(params.get("page"));
        int size = Integer.parseInt(params.get("size"));
        int from = Math.min(page * size, TOTAL);
        int to = Math.min(from + size, TOTAL);
        List<Line> rows = IntStream.range(from, to).mapToObj(i -> new Line("n" + i)).toList();
        return new PageImpl<>(rows, PageRequest.of(page, size), TOTAL);
    }

    private SearchExport<Line> export() {
        Function<Map<String, String>, Page<Line>> search = this::search;
        return SearchExport.of(Line.class, search).baseName("lines").pageSize(PAGE_SIZE);
    }

    private static Map<String, String> listParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("number.contains", "n");
        params.put("sort", "number.desc");
        params.put("page", "3");
        params.put("size", "10");
        return params;
    }

    @Test
    void csvHoldsEveryRowAcrossPages() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        export().write(listParams(), ExportFormat.CSV, response);

        String body = response.getContentAsString(StandardCharsets.UTF_8).replace("﻿", "");
        List<String> lines = body.lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).hasSize(TOTAL + 1);
        assertThat(lines.get(0)).contains("Number");
        assertThat(lines.get(TOTAL)).contains("n" + (TOTAL - 1));
        assertThat(response.getHeader("Content-Disposition")).contains("lines.csv");
        assertThat(response.getContentType()).startsWith("text/csv");
    }

    @Test
    void xlsxHoldsEveryRowAcrossPages() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        export().write(listParams(), ExportFormat.XLSX, response);

        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = book.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(TOTAL);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Number");
            assertThat(sheet.getRow(TOTAL).getCell(0).getStringCellValue()).isEqualTo("n" + (TOTAL - 1));
        }
        assertThat(response.getHeader("Content-Disposition")).contains("lines.xlsx");
        assertThat(response.getContentType()).contains("spreadsheetml");
    }

    @Test
    void everyPageCarriesTheListFiltersAndSortButNotItsPaging() throws Exception {
        export().write(listParams(), ExportFormat.CSV, new MockHttpServletResponse());

        int pages = (TOTAL + PAGE_SIZE - 1) / PAGE_SIZE;
        assertThat(asked).hasSize(pages);
        for (int i = 0; i < pages; i++) {
            Map<String, String> params = asked.get(i);
            assertThat(params).containsEntry("number.contains", "n")
                    .containsEntry("sort", "number.desc")
                    .containsEntry("page", String.valueOf(i))
                    .containsEntry("size", String.valueOf(PAGE_SIZE));
        }
    }
}
