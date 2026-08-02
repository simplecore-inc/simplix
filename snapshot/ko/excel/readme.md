# SimpliX Excel Module

Apache POI 기반의 Excel/CSV 내보내기 및 가져오기 모듈입니다.

## Features

- **Excel Export** - 표준 모드 및 스트리밍 모드 지원
- **Template Export** - JXLS 기반 템플릿 처리
- **CSV Export** - 다양한 인코딩 및 구분자 지원, 셀 이스케이프와 수식 차단
- **Excel/CSV Import** - 자동 타입 변환
- **다국어 라벨** - 헤더·열거형·논리값을 화면과 같은 말로 기록
- **Cell Styling** - 폰트, 색상, 정렬, 테두리 설정
- **Performance** - 포맷터/스타일 캐싱, 가상 페이징

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-excel:${version}'
}
```

### 2. Entity with @ExcelColumn

```java
public class User {
    @ExcelColumn(name = "ID", order = 1, width = 10)
    private Long id;

    @ExcelColumn(name = "Name", order = 2, width = 20, bold = true)
    private String name;

    @ExcelColumn(name = "Email", order = 3, width = 30)
    private String email;

    @ExcelColumn(name = "Created", order = 4, format = "yyyy-MM-dd")
    private LocalDate createdAt;
}
```

### 3. Export Excel

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/users/export")
    public void exportUsers(HttpServletResponse response) {
        List<User> users = userRepository.findAll();

        ExcelExporter.of(User.class)
            .filename("users.xlsx")
            .sheetName("Users")
            .export(response, users);
    }
}
```

### 4. Export CSV

```java
@GetMapping("/users/export/csv")
public void exportUsersCsv(HttpServletResponse response) {
    List<User> users = userRepository.findAll();

    CsvExporter.of(User.class)
        .filename("users.csv")
        .delimiter(',')
        .encoding("UTF-8")
        .export(response, users);
}
```

## Export Modes

| Mode | Class | Use Case |
|------|-------|----------|
| Standard | `ExcelExporter` | 소규모 데이터 (메모리 내 처리) |
| Streaming | `ExcelExporter.streaming()` | 대용량 데이터 (배치 처리) |
| Template | `JxlsExporter` | 복잡한 레이아웃, 수식 필요 |
| CSV | `CsvExporter` | 단순 데이터, 범용 호환 |

## @ExcelColumn Options

| Option | Type | Description |
|--------|------|-------------|
| `name` | String | 컬럼 헤더명, `{key}` 형태면 메시지 키로 해석 |
| `order` | int | 컬럼 순서 |
| `width` | int | 컬럼 너비 |
| `format` | String | 날짜/숫자 포맷 |
| `bold` | boolean | 굵은 글꼴 |
| `backgroundColor` | IndexedColors | 배경색 |
| `alignment` | HorizontalAlignment | 정렬 |

## 다국어 라벨

헤더·열거형·논리값은 Excel과 CSV 모두 화면과 같은 말로 기록됩니다.

```java
@ExcelColumn(name = "{entities.Contract.customer}", order = 1)
private String customer;

@ExcelColumn(name = "{entities.Contract.status}", order = 2)
private ContractStatus status;   // enums.ContractStatus.ACTIVE 키를 찾음
```

해석 순서는 등록된 `ExcelLabelResolver` → 애플리케이션 `MessageSource` → `@ExcelColumn`에 적힌 문자열입니다. 번역이 없어도 파일은 만들어지며, 키가 그대로 남습니다. 자세한 내용은 [내보내기 가이드](ko/excel/export-guide.md)에 있습니다.

## CSV 안전 처리

| 상황 | 처리 |
|------|------|
| 값에 구분자·따옴표·줄바꿈이 있음 | 따옴표로 감싸고 내부 따옴표를 중복 |
| 값이 `=` `+` `-` `@`로 시작 | 앞에 작은따옴표를 붙여 수식 실행을 막음 (`sanitizeFormulas`, 기본 켜짐) |

## Configuration

```yaml
simplix:
  excel:
    export:
      pageSize: 1000
      windowSize: 100
      streamingEnabled: false

    format:
      dateFormat: yyyy-MM-dd
      dateTimeFormat: yyyy-MM-dd HH:mm:ss
      numberFormat: "#,##0"
      booleanTrueValue: Y
      booleanFalseValue: N

    csv:
      delimiter: ","
      encoding: UTF-8
      includeHeaders: true
```

## Documentation

- [Overview (상세 문서)](ko/excel/overview.md)
- [Export Guide (내보내기 가이드)](ko/excel/export-guide.md)
- [Import Guide (가져오기 가이드)](ko/excel/import-guide.md)

## License

SimpleCORE License 1.0 (SCL-1.0)
