/*
 * Copyright (c) 2025 SimpleCORE
 * Licensed under the SimpleCORE License 1.0 (see LICENSE)
 * Use allowed in own products. Redistribution or resale requires permission.
 */
package dev.simplecore.simplix.excel.export;

import dev.simplecore.simplix.excel.impl.exporter.StandardExcelExporter;
import dev.simplecore.simplix.excel.impl.exporter.UnifiedCsvExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes every row a list search matches, page by page, so the file holds what the list shows
 * under the same filters and order rather than only the page on screen.
 *
 * <p>The caller hands over its own paged search over request parameters (the one its list
 * endpoint answers with). The {@code page} and {@code size} the request carried are dropped and
 * replaced by the export's own paging, and the export's own {@code format} parameter is dropped;
 * every other parameter, sort included, reaches each page.
 *
 * @param <T> the export row type, whose {@code @ExcelColumn} fields give the columns
 */
public final class SearchExport<T> {

    /** Rows read per query, below the usual search page size ceiling. */
    public static final int DEFAULT_PAGE_SIZE = 500;

    private static final String PAGE = "page";
    private static final String SIZE = "size";

    /** The request parameter an export endpoint names its {@link ExportFormat} with. */
    public static final String FORMAT_PARAM = "format";

    private final Class<T> rowType;
    private final Function<Map<String, String>, Page<T>> search;
    private String baseName = "export";
    private int pageSize = DEFAULT_PAGE_SIZE;

    private SearchExport(Class<T> rowType, Function<Map<String, String>, Page<T>> search) {
        this.rowType = rowType;
        this.search = search;
    }

    /**
     * Starts an export over a paged search.
     *
     * @param rowType the export row type
     * @param search the list's search over request parameters, already mapped to export rows
     */
    public static <T> SearchExport<T> of(Class<T> rowType, Function<Map<String, String>, Page<T>> search) {
        return new SearchExport<>(rowType, search);
    }

    /** The file name without extension; the format adds its own. */
    public SearchExport<T> baseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    /** Rows read per query. */
    public SearchExport<T> pageSize(int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }
        this.pageSize = pageSize;
        return this;
    }

    /**
     * Writes the file to the response with its download headers.
     *
     * @param params the list's search parameters; {@code page}, {@code size} and {@code format} are ignored
     * @param format the file kind
     * @param response where the file is written
     * @throws IOException when the response cannot be written
     */
    public void write(Map<String, String> params, ExportFormat format, HttpServletResponse response)
            throws IOException {
        Function<PageRequest, Page<T>> provider = pager(params);
        switch (format) {
            case CSV -> csv(provider).export(List.of(), response);
            case XLSX -> xlsx(provider).export(List.of(), response);
        }
    }

    /**
     * Writes the file body alone, for a caller that sets its own headers or stores the file.
     *
     * @param params the list's search parameters; {@code page}, {@code size} and {@code format} are ignored
     * @param format the file kind
     * @param out where the body is written
     * @throws IOException when the stream cannot be written
     */
    public void write(Map<String, String> params, ExportFormat format, OutputStream out) throws IOException {
        Function<PageRequest, Page<T>> provider = pager(params);
        switch (format) {
            case CSV -> csv(provider).export(List.of(), out);
            case XLSX -> xlsx(provider).export(List.of(), out);
        }
    }

    /** The file name the response carries for a format. */
    public String filename(ExportFormat format) {
        return baseName + "." + format.parameter();
    }

    private Function<PageRequest, Page<T>> pager(Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>(params == null ? Map.of() : params);
        filters.remove(PAGE);
        filters.remove(SIZE);
        filters.remove(FORMAT_PARAM);
        return page -> {
            Map<String, String> paged = new LinkedHashMap<>(filters);
            paged.put(PAGE, String.valueOf(page.getPageNumber()));
            paged.put(SIZE, String.valueOf(page.getPageSize()));
            return search.apply(paged);
        };
    }

    private UnifiedCsvExporter<T> csv(Function<PageRequest, Page<T>> provider) {
        return new UnifiedCsvExporter<>(rowType)
                .filename(filename(ExportFormat.CSV))
                .encoding(UnifiedCsvExporter.Encoding.UTF8_BOM)
                .batchSize(pageSize)
                .streaming(true)
                .dataProvider(provider);
    }

    private StandardExcelExporter<T> xlsx(Function<PageRequest, Page<T>> provider) {
        return new StandardExcelExporter<>(rowType)
                .filename(filename(ExportFormat.XLSX))
                .pageSize(pageSize)
                .streaming(true)
                .dataProvider(provider);
    }
}
