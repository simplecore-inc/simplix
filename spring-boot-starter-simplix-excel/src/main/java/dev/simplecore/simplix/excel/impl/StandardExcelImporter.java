package dev.simplecore.simplix.excel.impl;

import dev.simplecore.simplix.core.convert.bool.BooleanConverter;
import dev.simplecore.simplix.core.convert.datetime.DateTimeConverter;
import dev.simplecore.simplix.core.convert.enumeration.EnumConverter;
import dev.simplecore.simplix.excel.annotation.ExcelColumn;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class StandardExcelImporter<T> {
    private final Class<T> dataClass;
    private final List<Field> importFields;
    private final Map<Integer, Field> columnMapping;
    private final Map<Class<?>, Object> converters;
    
    @Getter @Setter
    private boolean skipHeader = true;
    
    @Getter @Setter
    private int sheetIndex = 0;
    
    @Getter @Setter
    private String dateFormat = "yyyy-MM-dd";
    
    @Getter @Setter
    private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

    public StandardExcelImporter(Class<T> dataClass) {
        this.dataClass = dataClass;
        this.columnMapping = new HashMap<>();
        this.converters = initConverters();
        
        // Extract fields with @ExcelColumn annotation and sort by order
        this.importFields = Arrays.stream(dataClass.getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
            .filter(field -> !field.getAnnotation(ExcelColumn.class).ignore())
            .sorted(Comparator.comparingInt(field -> 
                field.getAnnotation(ExcelColumn.class).order()))
            .collect(Collectors.toList());
        
        // Initialize default column mapping
        for (int i = 0; i < importFields.size(); i++) {
            columnMapping.put(i, importFields.get(i));
        }
    }

    private Map<Class<?>, Object> initConverters() {
        Map<Class<?>, Object> converters = new HashMap<>();
        converters.put(Boolean.class, BooleanConverter.getDefault());
        converters.put(LocalDate.class, DateTimeConverter.getDefault());
        converters.put(LocalDateTime.class, DateTimeConverter.getDefault());
        converters.put(Enum.class, EnumConverter.getDefault());
        return converters;
    }

    public List<T> importFromExcel(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            return processSheet(sheet);
        }
    }

    public List<T> importFromCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVParser parser = CSVFormat.DEFAULT.parse(reader);
            return processCsv(parser);
        }
    }

    public void setColumnMapping(Map<Integer, String> mapping) {
        columnMapping.clear();
        mapping.forEach((column, fieldName) -> {
            Field field = findFieldByName(fieldName);
            if (field != null) {
                columnMapping.put(column, field);
            }
        });
    }

    private Field findFieldByName(String fieldName) {
        return importFields.stream()
            .filter(field -> field.getName().equals(fieldName))
            .findFirst()
            .orElse(null);
    }

    private List<T> processSheet(Sheet sheet) {
        List<T> results = new ArrayList<>();
        int startRow = skipHeader ? 1 : 0;

        for (int rowNum = startRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            T instance = createInstance(row);
            if (instance != null) {
                results.add(instance);
            }
        }

        return results;
    }

    private List<T> processCsv(CSVParser parser) {
        List<T> results = new ArrayList<>();
        Iterator<CSVRecord> iterator = parser.iterator();

        if (skipHeader && iterator.hasNext()) {
            iterator.next(); // Skip header row
        }

        while (iterator.hasNext()) {
            CSVRecord record = iterator.next();
            T instance = createInstanceFromCsv(record);
            if (instance != null) {
                results.add(instance);
            }
        }

        return results;
    }

    private T createInstance(Row row) {
        try {
            T instance = dataClass.getDeclaredConstructor().newInstance();

            for (Map.Entry<Integer, Field> entry : columnMapping.entrySet()) {
                int columnIndex = entry.getKey();
                Field field = entry.getValue();
                Cell cell = row.getCell(columnIndex);
                
                if (cell != null) {
                    Object value = convertCellValue(cell, field.getType());
                    if (value != null) {
                        field.setAccessible(true);
                        field.set(instance, value);
                    }
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance from Excel row", e);
        }
    }

    private T createInstanceFromCsv(CSVRecord record) {
        try {
            T instance = dataClass.getDeclaredConstructor().newInstance();

            for (Map.Entry<Integer, Field> entry : columnMapping.entrySet()) {
                int columnIndex = entry.getKey();
                Field field = entry.getValue();
                
                if (columnIndex < record.size()) {
                    String value = record.get(columnIndex);
                    if (StringUtils.hasText(value)) {
                        Object convertedValue = convertValue(value, field.getType());
                        if (convertedValue != null) {
                            field.setAccessible(true);
                            field.set(instance, convertedValue);
                        }
                    }
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance from CSV record", e);
        }
    }

    private Object convertCellValue(Cell cell, Class<?> targetType) {
        switch (cell.getCellType()) {
            case STRING:
                return convertValue(cell.getStringCellValue(), targetType);
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return convertValue(cell.getLocalDateTimeCellValue(), targetType);
                }
                return convertValue(cell.getNumericCellValue(), targetType);
            case BOOLEAN:
                return convertValue(cell.getBooleanCellValue(), targetType);
            case BLANK:
                return null;
            default:
                return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        // Handle numeric types
        if (value instanceof Number) {
            Number number = (Number) value;
            if (targetType == Long.class || targetType == long.class) return number.longValue();
            if (targetType == Integer.class || targetType == int.class) return number.intValue();
            if (targetType == Double.class || targetType == double.class) return number.doubleValue();
            if (targetType == Float.class || targetType == float.class) return number.floatValue();
            if (targetType == BigDecimal.class) return BigDecimal.valueOf(number.doubleValue());
            
            // Handle Excel date values (days since 1900-01-01)
            if ((targetType == LocalDate.class || targetType == LocalDateTime.class) 
                && number.doubleValue() > 0) {
                try {
                    // Excel's date system has a bug where it thinks 1900 is a leap year
                    // We need to subtract 1 from dates after February 28, 1900
                    double excelDate = number.doubleValue();
                    if (excelDate > 60) { // March 1, 1900 in Excel is actually day 61
                        excelDate -= 1;
                    }
                    
                    // Convert Excel date to LocalDateTime
                    int wholeDays = (int) excelDate;
                    double fractionalDay = excelDate - wholeDays;
                    
                    LocalDateTime dateTime = LocalDateTime.of(1900, 1, 1, 0, 0)
                        .plusDays(wholeDays - 1); // Subtract 1 because Excel dates start from 1
                    
                    if (fractionalDay > 0) {
                        // Convert fractional day to milliseconds and round to nearest second
                        long millisInDay = Math.round(fractionalDay * 24 * 60 * 60 * 1000);
                        dateTime = dateTime
                            .plusHours(millisInDay / (60 * 60 * 1000))
                            .plusMinutes((millisInDay % (60 * 60 * 1000)) / (60 * 1000))
                            .plusSeconds((millisInDay % (60 * 1000)) / 1000);
                    }
                    
                    return targetType == LocalDate.class ? dateTime.toLocalDate() : dateTime;
                } catch (Exception e) {
                    log.warn("Failed to convert Excel date value: {}", number, e);
                    return null;
                }
            }
        }

        // Handle string values
        String stringValue = value.toString().trim();
        
        // Handle numeric strings for numeric types
        try {
            if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(stringValue);
            }
            if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(stringValue);
            }
            if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(stringValue);
            }
            if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(stringValue);
            }
            if (targetType == BigDecimal.class) {
                return new BigDecimal(stringValue);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric value: " + stringValue, e);
        }

        // Handle date/time types
        if (targetType == LocalDate.class && value instanceof LocalDateTime) {
            return ((LocalDateTime) value).toLocalDate();
        }

        // Use converters for complex types
        if (targetType == Boolean.class || targetType == boolean.class) {
            return ((BooleanConverter) converters.get(Boolean.class)).fromString(stringValue);
        }
        if (targetType == LocalDate.class) {
            return ((DateTimeConverter) converters.get(LocalDate.class))
                .fromString(stringValue, LocalDate.class);
        }
        if (targetType == LocalDateTime.class) {
            return ((DateTimeConverter) converters.get(LocalDateTime.class))
                .fromString(stringValue, LocalDateTime.class);
        }
        if (targetType.isEnum()) {
            return ((EnumConverter) converters.get(Enum.class))
                .fromString(stringValue, (Class<? extends Enum>) targetType);
        }

        return stringValue;
    }
} 