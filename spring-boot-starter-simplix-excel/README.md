# SimpliX Excel Module

The SimpliX Excel module provides various features for exporting data in Excel and CSV formats.
This module supports large data processing, template-based exports, and rich styling options.

## License Notice
Copyright (c) 2025 SimpleCORE
Licensed under the SimpleCORE License 1.0
Use allowed in own products. Redistribution or resale requires permission.

## Requirements
- Apache POI 5.x

## Key Features
- **Template-based Export**: Create Excel files using dynamic templates
- **DTO Field Annotations**: Easy configuration using `@ExcelColumn` annotation
- **Large Data Processing**: Memory-efficient processing with streaming mode support
- **Flexible Styling**: Rich style settings including fonts, colors, and formats
- **Type Conversion**: Comprehensive type conversion system with core converter integration
- **Auto Configuration**: Spring Boot auto-configuration support
- **Performance Optimized**: Cached formatters and styles for better performance
- **Excel Date Handling**: Accurate Excel date/time conversion with 1900 date system support
- **Import Support**: Excel and CSV file import with type conversion and validation

## Module Structure
```
excel/
├── annotation/     # Annotation definitions
├── api/           # Common interfaces
├── autoconfigure/ # Spring Boot auto configuration
├── convert/       # Type conversion system
├── exception/     # Exception classes
├── format/        # Formatter cache and utilities
├── impl/          # Interface implementations
├── properties/    # Configuration properties
├── style/         # Style management
├── template/      # Template processing
└── util/          # Utility classes
```

## Installation

### Gradle
```groovy
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix-excel:1.1.0'
}
```

### Maven
```xml
<dependency>
    <groupId>dev.simplecore</groupId>
    <artifactId>spring-boot-starter-simplix-excel</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Configuration Properties
```properties
# Template Settings
simplix.excel.template.path=templates/default-template.xlsx
simplix.excel.template.cache-enabled=true
simplix.excel.template.cache-size=100

# Export Settings
simplix.excel.export.default-sheet-name=Data
simplix.excel.export.streaming-threshold=100000
simplix.excel.export.batch-size=1000

# Import Settings
simplix.excel.import.skip-header=true
simplix.excel.import.sheet-index=0
simplix.excel.import.batch-size=1000

# Format Settings
simplix.excel.format.date=yyyy-MM-dd
simplix.excel.format.datetime=yyyy-MM-dd HH:mm:ss
simplix.excel.format.number=#,##0.###
simplix.excel.format.default-timezone=system

# Style Settings
simplix.excel.style.header.background=#F0F0F0
simplix.excel.style.header.font-size=11
simplix.excel.style.data.font-size=10
```

## Usage Examples

### 1. Basic DTO Configuration
```java
public class UserDto {
    @ExcelColumn(name = "ID", order = 1)
    private Long id;
    
    @ExcelColumn(name = "Name", order = 2, 
                fontName = "Arial", fontSize = 10, bold = true)
    private String name;
    
    @ExcelColumn(name = "Created At", order = 3, 
                format = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @ExcelColumn(name = "Status", order = 4,
                backgroundColor = "#E6F3FF")
    private UserStatus status;  // Enum type
}
```

### 2. Type Conversion Integration
```java
@Configuration
public class ExcelConfig {
    @PostConstruct
    public void configureConverters() {
        // Core converters are automatically integrated:
        // - BooleanConverter
        // - DateTimeConverter
        // - EnumConverter
        
        // Custom converters can be added if needed
        TypeConverter.register(CustomType.class, new CustomTypeConverter());
    }
}
```

### 3. Excel Import
```java
@Service
public class ExcelImportService {
    public List<UserDto> importUsers(MultipartFile file) {
        StandardExcelImporter<UserDto> importer = new StandardExcelImporter<>(UserDto.class);
        
        // Configure import options
        importer.setSkipHeader(true);
        importer.setSheetIndex(0);
        
        // Custom column mapping (optional)
        Map<Integer, String> columnMapping = new HashMap<>();
        columnMapping.put(0, "id");
        columnMapping.put(1, "name");
        columnMapping.put(2, "createdAt");
        columnMapping.put(3, "status");
        importer.setColumnMapping(columnMapping);
        
        // Import data
        return importer.importFromExcel(file);
    }
    
    public List<UserDto> importUsersFromCsv(MultipartFile file) {
        StandardExcelImporter<UserDto> importer = new StandardExcelImporter<>(UserDto.class);
        return importer.importFromCsv(file);
    }
}
```

### 4. Style Management
```java
@Service
public class ExcelService {
    public void customizeStyle(Workbook workbook, Object value, ExcelColumn column) {
        ExcelStyleManager styleManager = new ExcelStyleManager(workbook);
        
        // Create header style
        CellStyle headerStyle = styleManager.createHeaderStyle();
        
        // Create data style with column configuration
        CellStyle dataStyle = styleManager.createDataStyle(value, column);
    }
}
```

### 5. Using FormatterCache
```java
public class DateFormatter {
    public String formatDate(Date date, String pattern) {
        // Get cached formatter
        return FormatterCache.getLegacyDateFormatter(pattern)
                           .format(date);
    }
    
    public String formatDateTime(LocalDateTime dateTime, String pattern) {
        // Get cached formatter
        return FormatterCache.getDateTimeFormatter(pattern)
                           .format(dateTime);
    }
}
```

## @ExcelColumn Annotation Options

| Option          | Description                        | Default      |
|----------------|------------------------------------|--------------|
| name           | Column name                        | ""           |
| order          | Column order (ascending)           | 0            |
| width          | Column width (in characters)       | 15           |
| format         | Date/Number format pattern         | ""           |
| fontName       | Font name                          | "Arial"      |
| fontSize       | Font size                          | 10           |
| bold           | Bold text                          | false        |
| italic         | Italic text                        | false        |
| alignment      | Alignment (LEFT, CENTER, RIGHT)    | "LEFT"       |
| backgroundColor| Background color (hex)             | ""           |
| fontColor      | Font color (hex)                   | ""           |
| wrapText       | Allow text wrapping                | false        |
| ignore         | Exclude from processing            | false        |

## Performance Considerations

1. **Formatter Cache**
   - Date/Time formatters are cached in `FormatterCache`
   - Pattern-based caching improves formatting performance

2. **Style Management**
   - `ExcelStyleManager` caches cell styles
   - Reuses styles for similar configurations

3. **Color Handling**
   - Hex colors are converted to nearest POI IndexedColors
   - Custom RGB colors are supported via XSSFColor

4. **Large Dataset Processing**
   - Use streaming mode for large exports
   - Consider batch size configuration for memory management
   - Batch processing for imports to manage memory usage

5. **Date/Time Handling**
   - Excel 1900 date system support with leap year bug fix
   - Millisecond precision for time values
   - Efficient date/time conversion using core converters

## Error Handling

The module includes specialized exceptions:
- `ExcelExportException`: General export errors
- `ExcelFormatException`: Format/conversion errors
- `ExcelTemplateException`: Template processing errors
- `ExcelImportException`: Import processing errors

## Recent Changes

1. **Core Converter Integration**
   - Removed custom converters in favor of core module integration
   - Improved type conversion consistency across modules
   - Better support for complex data types

2. **Excel Date System**
   - Fixed Excel 1900 date system leap year bug
   - Improved millisecond precision for time values
   - Better handling of fractional days

3. **Annotation Updates**
   - Renamed `title` to `name` for consistency
   - Combined `dateFormat`/`numberFormat` into unified `format`
   - Added `ignore` property for field exclusion

4. **Performance Improvements**
   - Optimized large dataset processing
   - Enhanced formatter caching
   - Improved memory usage in streaming mode

5. **Import Features**
   - Added Excel and CSV import support
   - Flexible column mapping
   - Type conversion and validation
   - Batch processing for large files

## Contributing

Please note that this project is proprietary and contributions require permission from SimpleCORE.
For bug reports or feature requests, please contact the development team. 