# SimpliX Excel Module Overview

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application                               │
│                             │                                    │
│     ExcelExporter / JxlsExporter / CsvExporter                  │
│                             │                                    │
└─────────────────────────────┼───────────────────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SimpliX Excel Module                          │
│                                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │ Standard    │  │ Jxls       │  │ Unified     │              │
│  │ Excel      │  │ Exporter   │  │ Csv        │              │
│  │ Exporter   │  │ Impl       │  │ Exporter   │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│         └────────────────┼────────────────┘                      │
│                          ▼                                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   AbstractExporter                         │  │
│  │  - Field extraction (@ExcelColumn)                        │  │
│  │  - HTTP response handling                                  │  │
│  │  - Batch processing                                        │  │
│  └───────────────────────────────────────────────────────────┘  │
│                          │                                       │
│         ┌────────────────┼────────────────┐                     │
│         ▼                ▼                ▼                     │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐                │
│  │ Excel      │  │ Formatter  │  │ Excel      │                │
│  │ Style     │  │ Cache      │  │ Converter  │                │
│  │ Manager   │  │            │  │            │                │
│  └────────────┘  └────────────┘  └────────────┘                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Core Interfaces

### ExcelExporter<T>

표준 Excel 내보내기 인터페이스:

```java
public interface ExcelExporter<T> {
    ExcelExporter<T> filename(String filename);
    ExcelExporter<T> sheetName(String sheetName);
    ExcelExporter<T> streaming();
    ExcelExporter<T> autoSizeColumns();

    void export(HttpServletResponse response, Collection<T> data);
    void export(OutputStream outputStream, Collection<T> data);
}
```

### JxlsExporter<T>

템플릿 기반 내보내기 인터페이스:

```java
public interface JxlsExporter<T> {
    JxlsExporter<T> template(String templatePath);
    JxlsExporter<T> enableFormulas();
    JxlsExporter<T> hideGridLines();
    JxlsExporter<T> parameter(String key, Object value);

    void export(HttpServletResponse response, Collection<T> data);
}
```

### CsvExporter<T>

CSV 내보내기 인터페이스:

```java
public interface CsvExporter<T> {
    CsvExporter<T> filename(String filename);
    CsvExporter<T> delimiter(char delimiter);
    CsvExporter<T> encoding(String encoding);
    CsvExporter<T> quoteStrings();
    CsvExporter<T> includeHeader(boolean include);

    void export(HttpServletResponse response, Collection<T> data);
}
```

---

## @ExcelColumn Annotation

필드를 Excel 컬럼에 매핑하는 어노테이션:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {
    // 기본 설정
    String name() default "";           // 컬럼 헤더명
    int order() default 0;              // 컬럼 순서
    int width() default 15;             // 컬럼 너비
    boolean ignore() default false;     // 내보내기 제외

    // 포맷
    String format() default "";         // 날짜/숫자 포맷 패턴

    // 폰트 스타일
    String fontName() default "Arial";
    int fontSize() default 10;
    boolean bold() default false;
    boolean italic() default false;

    // 색상
    IndexedColors backgroundColor() default IndexedColors.AUTOMATIC;
    IndexedColors fontColor() default IndexedColors.AUTOMATIC;

    // 레이아웃
    HorizontalAlignment alignment() default HorizontalAlignment.LEFT;
    boolean wrapText() default false;
}
```

### 옵션 상세

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `name` | String | "" | 컬럼 헤더에 표시될 이름 |
| `order` | int | 0 | 컬럼 순서 (오름차순) |
| `width` | int | 15 | 문자 단위 컬럼 너비 |
| `ignore` | boolean | false | true면 내보내기에서 제외 |
| `format` | String | "" | 날짜: "yyyy-MM-dd", 숫자: "#,##0" |
| `fontName` | String | "Arial" | 폰트 이름 |
| `fontSize` | int | 10 | 폰트 크기 |
| `bold` | boolean | false | 굵은 글꼴 |
| `italic` | boolean | false | 기울임 글꼴 |
| `backgroundColor` | IndexedColors | AUTOMATIC | 배경색 |
| `fontColor` | IndexedColors | AUTOMATIC | 글꼴색 |
| `alignment` | HorizontalAlignment | LEFT | 정렬 (LEFT, CENTER, RIGHT) |
| `wrapText` | boolean | false | 텍스트 줄바꿈 |

---

## Export Implementations

### StandardExcelExporter

3가지 내보내기 모드 지원:

#### 1. Standard Mode (기본)

모든 데이터를 메모리에 로드:

```java
ExcelExporter.of(User.class)
    .filename("users.xlsx")
    .export(response, users);
```

#### 2. Streaming Mode with DataProvider

페이지네이션 기반 배치 처리:

```java
ExcelExporter.of(User.class)
    .filename("users.xlsx")
    .streaming()
    .export(response, pageNumber -> userRepository.findAll(PageRequest.of(pageNumber, 1000)));
```

#### 3. Streaming Mode with Collection

가상 페이징으로 대용량 컬렉션 처리:

```java
ExcelExporter.of(User.class)
    .filename("users.xlsx")
    .streaming()
    .export(response, largeUserList);  // 내부적으로 StreamingCollection 사용
```

### JxlsExporterImpl

JXLS 마커 기반 템플릿 처리:

```java
JxlsExporter.of(User.class)
    .template("classpath:templates/user-report.xlsx")
    .parameter("reportDate", LocalDate.now())
    .parameter("department", "IT")
    .enableFormulas()
    .export(response, users);
```

### UnifiedCsvExporter

다양한 CSV 옵션 지원:

```java
CsvExporter.of(User.class)
    .filename("users.csv")
    .delimiter(';')           // 구분자
    .encoding("UTF-8")        // 인코딩
    .quoteStrings()           // 문자열 인용
    .includeHeader(true)      // 헤더 포함
    .export(response, users);
```

---

## Import Functionality

### StandardExcelImporter

Excel 및 CSV 파일 가져오기:

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// Excel 가져오기
List<User> users = importer.importFromExcel(excelFile);

// CSV 가져오기
List<User> users = importer.importFromCsv(csvFile);
```

### Import Options

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// 헤더 건너뛰기
importer.setSkipHeader(true);

// 시트 인덱스 (0부터 시작)
importer.setSheetIndex(0);

// 날짜 포맷
importer.setDateFormat("yyyy-MM-dd");
importer.setDateTimeFormat("yyyy-MM-dd HH:mm:ss");

// 컬럼 매핑 (인덱스 -> 필드명)
Map<Integer, String> mapping = Map.of(
    0, "id",
    1, "name",
    2, "email"
);
importer.setColumnMapping(mapping);
```

---

## Type Conversion

### 지원 타입

| 카테고리 | 타입 |
|----------|------|
| 기본형 | boolean, byte, short, int, long, float, double |
| 숫자 | BigDecimal, BigInteger |
| 문자열 | String |
| 날짜/시간 (Legacy) | Date, Calendar |
| 날짜/시간 (Java 8+) | LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime, Instant |
| 기간 | Year, YearMonth, MonthDay, Duration, Period |
| 열거형 | Enum, SimpliXLabeledEnum |
| 컬렉션 | List, Set, Collection (쉼표 구분 문자열로 변환) |
| 객체 | getId(), getName(), getTitle(), getCode() 메서드 자동 탐색 |

### Enum 처리

```java
// 기본 Enum - name() 사용
public enum Status {
    ACTIVE, INACTIVE, PENDING
}

// SimpliXLabeledEnum - getLabel() 사용
public enum Status implements SimpliXLabeledEnum {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    PENDING("대기");

    private final String label;

    @Override
    public String getLabel() {
        return label;
    }
}
```

---

## Style Management

### ExcelStyleManager

셀 스타일 생성 및 캐싱:

```java
ExcelStyleManager styleManager = new ExcelStyleManager(workbook);

// 헤더 스타일 (굵은 글꼴, 회색 배경, 테두리)
CellStyle headerStyle = styleManager.createHeaderStyle();

// 데이터 스타일 (@ExcelColumn 기반)
CellStyle dataStyle = styleManager.createDataStyle(value, excelColumn);
```

### Style Caching

동일 설정의 스타일은 캐싱되어 재사용:

```
스타일 키 = fontName + fontSize + bold + italic + bgColor + fontColor + alignment + wrapText + format
```

---

## Formatter System

### FormatterCache

패턴 기반 포맷터 캐싱:

```java
// 날짜 포맷터
DateTimeFormatter formatter = FormatterCache.getDateTimeFormatter("yyyy-MM-dd");

// 숫자 포맷터
DecimalFormat formatter = FormatterCache.getDecimalFormatter("#,##0.00");

// Legacy Date 포맷터
SimpleDateFormat formatter = FormatterCache.getLegacyDateFormatter("yyyy-MM-dd");
```

### 기본 포맷

| 타입 | 기본 포맷 |
|------|----------|
| Date | yyyy-MM-dd |
| Time | HH:mm:ss |
| DateTime | yyyy-MM-dd HH:mm:ss |
| Number | #,##0 |
| Decimal | #,##0.00 |
| Percentage | #,##0.00% |
| Currency | ¤#,##0.00 |

---

## Template Processing

### ExcelTemplateManager

템플릿 로딩 및 캐싱:

```java
// 템플릿 로드 (classpath 우선, filesystem 폴백)
byte[] template = ExcelTemplateManager.loadTemplate("templates/report.xlsx");

// 기본 템플릿 자동 생성
byte[] template = ExcelTemplateManager.getOrCreateTemplate("templates/default.xlsx", User.class);
```

### JXLS Markers

템플릿에서 사용하는 JXLS 마커:

```
${item.name}           - 단일 값
${item.createdAt}      - 날짜 값
jx:each(items, "item") - 반복 처리
jx:area(A1:D100)       - 영역 지정
```

---

## Configuration Reference

### 전체 설정

```yaml
simplix:
  excel:
    # 템플릿 설정
    template:
      path: templates/default-template.xlsx
      defaultSheetName: Data
      defaultColumnWidth: 15
      useJxlsMarkers: true
      applyHeaderStyle: true

    # 내보내기 설정
    export:
      pageSize: 1000              # 스트리밍 페이지 크기
      windowSize: 100             # SXSSF 윈도우 크기
      defaultSheetName: Data
      streamingEnabled: false
      hideGridLines: false
      enableFormulas: true

    # CSV 설정
    csv:
      delimiter: ","
      encoding: UTF-8
      quoteStrings: true
      includeHeaders: true
      lineSeparator: (system)

    # 포맷 설정
    format:
      dateFormat: yyyy-MM-dd
      timeFormat: HH:mm:ss
      dateTimeFormat: yyyy-MM-dd HH:mm:ss
      numberFormat: "#,##0"
      decimalFormat: "#,##0.00"
      percentageFormat: "#,##0.00%"
      currencyFormat: "¤#,##0.00"
      booleanTrueValue: Y
      booleanFalseValue: N

    # 캐시 설정
    cache:
      templateCacheEnabled: true
      fieldCacheEnabled: true
      columnCacheEnabled: true
```

---

## Performance Optimization

### 1. Streaming Mode

대용량 데이터는 스트리밍 모드 사용:

```java
ExcelExporter.of(User.class)
    .streaming()  // SXSSFWorkbook 사용
    .export(response, dataProvider);
```

### 2. Batch Processing

페이지 단위 처리로 메모리 관리:

```java
// pageSize: 1000 (한 번에 처리할 행 수)
// windowSize: 100 (메모리에 유지할 행 수)
```

### 3. Style Caching

동일 스타일 재사용:

```java
// 자동으로 캐싱됨 - 같은 설정의 CellStyle은 한 번만 생성
```

### 4. Formatter Caching

포맷터 재사용:

```java
// 패턴 기반 캐싱 - 동일 패턴은 캐시에서 반환
```

---

## Related Documents

- [Export Guide](./export-guide.md) - 내보내기 상세 가이드
- [Import Guide](./import-guide.md) - 가져오기 상세 가이드
