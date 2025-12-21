# SimpliX Excel Import Guide

Excel/CSV 가져오기 상세 사용법 가이드입니다.

## Table of Contents

- [Basic Import](#basic-import)
- [CSV Import](#csv-import)
- [Import Options](#import-options)
- [Column Mapping](#column-mapping)
- [Type Conversion](#type-conversion)
- [Validation](#validation)
- [Large Data Import](#large-data-import)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Basic Import

### Excel Import

기본적인 Excel 파일 가져오기:

```java
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final Validator validator;

    @PostMapping("/users/import")
    public ResponseEntity<ImportResult> importUsers(@RequestParam MultipartFile file) {
        StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

        List<User> users = importer.importFromExcel(file);

        userRepository.saveAll(users);

        return ResponseEntity.ok(new ImportResult(users.size()));
    }
}
```

### Import Result DTO

```java
public class ImportResult {
    private final int totalCount;
    private final int successCount;
    private final List<ImportError> errors;

    // Constructor, getters
}

public class ImportError {
    private final int rowNumber;
    private final String fieldName;
    private final String message;

    // Constructor, getters
}
```

---

## CSV Import

### Basic CSV Import

```java
@PostMapping("/users/import/csv")
public ResponseEntity<ImportResult> importUsersCsv(@RequestParam MultipartFile file) {
    StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

    List<User> users = importer.importFromCsv(file);

    userRepository.saveAll(users);

    return ResponseEntity.ok(new ImportResult(users.size()));
}
```

### CSV with Custom Delimiter

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// 세미콜론 구분자
importer.setDelimiter(';');

// 탭 구분자 (TSV)
importer.setDelimiter('\t');

List<User> users = importer.importFromCsv(file);
```

### CSV Encoding

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// 인코딩 설정 (기본: UTF-8)
importer.setEncoding("UTF-8");

// EUC-KR (레거시 한글 파일)
importer.setEncoding("EUC-KR");

List<User> users = importer.importFromCsv(file);
```

---

## Import Options

### Skip Header

첫 번째 행(헤더) 건너뛰기:

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

importer.setSkipHeader(true);  // 첫 번째 행 건너뛰기

List<User> users = importer.importFromExcel(file);
```

### Sheet Selection

특정 시트 선택 (0-indexed):

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// 두 번째 시트 선택
importer.setSheetIndex(1);

List<User> users = importer.importFromExcel(file);
```

### Date Format

날짜 포맷 설정:

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// 날짜 포맷
importer.setDateFormat("yyyy/MM/dd");

// 날짜시간 포맷
importer.setDateTimeFormat("yyyy/MM/dd HH:mm:ss");

List<User> users = importer.importFromExcel(file);
```

### Combined Options

```java
@PostMapping("/users/import")
public ResponseEntity<ImportResult> importUsers(@RequestParam MultipartFile file) {
    StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

    // 옵션 설정
    importer.setSkipHeader(true);
    importer.setSheetIndex(0);
    importer.setDateFormat("yyyy-MM-dd");
    importer.setDateTimeFormat("yyyy-MM-dd HH:mm:ss");

    List<User> users = importer.importFromExcel(file);

    return ResponseEntity.ok(new ImportResult(users.size()));
}
```

---

## Column Mapping

### Index-Based Mapping

컬럼 인덱스를 필드에 매핑:

```java
StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);

// Excel 컬럼 순서가 DTO와 다른 경우
Map<Integer, String> mapping = new HashMap<>();
mapping.put(0, "name");      // A열 -> name
mapping.put(1, "email");     // B열 -> email
mapping.put(2, "id");        // C열 -> id
mapping.put(4, "createdAt"); // E열 -> createdAt (D열 건너뛰기)

importer.setColumnMapping(mapping);

List<User> users = importer.importFromExcel(file);
```

### Mapping Example

Excel 파일 구조:
```
| A (Name) | B (Email) | C (ID) | D (Skip) | E (Created) |
|----------|-----------|--------|----------|-------------|
| John     | j@t.com   | 1      | ...      | 2024-01-15  |
```

DTO:
```java
public class User {
    private Long id;        // C열에서 매핑
    private String name;    // A열에서 매핑
    private String email;   // B열에서 매핑
    private LocalDate createdAt;  // E열에서 매핑
}
```

### @ExcelColumn Based Mapping

@ExcelColumn의 order 속성으로 매핑:

```java
public class UserImportDto {
    @ExcelColumn(order = 3)  // C열 (0-indexed: 2)
    private Long id;

    @ExcelColumn(order = 1)  // A열 (0-indexed: 0)
    private String name;

    @ExcelColumn(order = 2)  // B열 (0-indexed: 1)
    private String email;

    @ExcelColumn(order = 5, format = "yyyy-MM-dd")  // E열
    private LocalDate createdAt;
}
```

---

## Type Conversion

### Supported Types

자동 타입 변환 지원:

| 카테고리 | 타입 |
|----------|------|
| 기본형 | boolean, byte, short, int, long, float, double |
| 숫자 | BigDecimal, BigInteger |
| 문자열 | String |
| 날짜/시간 (Legacy) | Date, Calendar |
| 날짜/시간 (Java 8+) | LocalDate, LocalDateTime, LocalTime, OffsetDateTime, ZonedDateTime, Instant |
| 기간 | Year, YearMonth, MonthDay, Duration, Period |
| 열거형 | Enum, SimpliXLabeledEnum |
| 컬렉션 | List, Set, Collection (쉼표 구분 문자열에서 변환) |

### Enum Conversion

```java
// 기본 Enum - name() 사용
public enum Status {
    ACTIVE, INACTIVE, PENDING
}
// Excel 값: "ACTIVE", "INACTIVE", "PENDING"

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
// Excel 값: "활성", "비활성", "대기"
```

### Boolean Conversion

```java
// 지원되는 Boolean 값
// true: "true", "yes", "y", "1", "on"
// false: "false", "no", "n", "0", "off"
```

### Collection Conversion

```java
public class Product {
    // 쉼표로 구분된 문자열에서 List로 변환
    // Excel 값: "tag1, tag2, tag3"
    private List<String> tags;

    // Excel 값: "cat1, cat2"
    private Set<String> categories;
}
```

---

## Validation

### Bean Validation

```java
@PostMapping("/users/import")
public ResponseEntity<?> importUsers(@RequestParam MultipartFile file) {
    StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);
    importer.setSkipHeader(true);

    List<User> users = importer.importFromExcel(file);

    // 유효성 검사
    List<ImportError> errors = new ArrayList<>();
    for (int i = 0; i < users.size(); i++) {
        User user = users.get(i);
        Set<ConstraintViolation<User>> violations = validator.validate(user);

        if (!violations.isEmpty()) {
            int rowNumber = i + 2;  // +2: 헤더 행 + 0-indexed
            for (ConstraintViolation<User> v : violations) {
                errors.add(new ImportError(
                    rowNumber,
                    v.getPropertyPath().toString(),
                    v.getMessage()
                ));
            }
        }
    }

    if (!errors.isEmpty()) {
        return ResponseEntity.badRequest().body(errors);
    }

    userRepository.saveAll(users);
    return ResponseEntity.ok(new ImportResult(users.size()));
}
```

### DTO with Validation Annotations

```java
public class UserImportDto {

    @NotNull(message = "ID is required")
    @ExcelColumn(order = 1)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    @ExcelColumn(order = 2)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @ExcelColumn(order = 3)
    private String email;

    @Past(message = "Created date must be in the past")
    @ExcelColumn(order = 4, format = "yyyy-MM-dd")
    private LocalDate createdAt;
}
```

### Custom Validation

```java
@PostMapping("/users/import")
public ResponseEntity<?> importUsers(@RequestParam MultipartFile file) {
    StandardExcelImporter<User> importer = new StandardExcelImporter<>(User.class);
    List<User> users = importer.importFromExcel(file);

    List<ImportError> errors = new ArrayList<>();

    for (int i = 0; i < users.size(); i++) {
        User user = users.get(i);
        int rowNumber = i + 2;

        // 커스텀 유효성 검사
        if (userRepository.existsByEmail(user.getEmail())) {
            errors.add(new ImportError(rowNumber, "email", "Email already exists"));
        }

        if (user.getCreatedAt().isAfter(LocalDate.now())) {
            errors.add(new ImportError(rowNumber, "createdAt", "Future date not allowed"));
        }
    }

    if (!errors.isEmpty()) {
        return ResponseEntity.badRequest().body(errors);
    }

    userRepository.saveAll(users);
    return ResponseEntity.ok(new ImportResult(users.size()));
}
```

---

## Large Data Import

### Batch Import

대용량 파일을 배치 단위로 처리:

```java
@PostMapping("/data/import")
public ResponseEntity<?> importLargeFile(@RequestParam MultipartFile file) {
    StandardExcelImporter<Data> importer = new StandardExcelImporter<>(Data.class);

    AtomicInteger processed = new AtomicInteger(0);

    // 배치 단위로 처리
    importer.importFromExcel(file, batch -> {
        dataRepository.saveAll(batch);
        processed.addAndGet(batch.size());
        log.info("Processed {} records", processed.get());
    }, 1000);  // 1000건씩 배치 처리

    return ResponseEntity.ok(Map.of("processed", processed.get()));
}
```

### Streaming Import

메모리 효율적인 가져오기:

```java
@PostMapping("/logs/import")
public ResponseEntity<?> importLogs(@RequestParam MultipartFile file) {
    StandardExcelImporter<LogEntry> importer = new StandardExcelImporter<>(LogEntry.class);

    AtomicInteger successCount = new AtomicInteger(0);
    List<ImportError> errors = new ArrayList<>();

    // 스트리밍 방식으로 한 행씩 처리
    importer.importFromExcelStreaming(file, (rowIndex, entry) -> {
        try {
            logRepository.save(entry);
            successCount.incrementAndGet();
        } catch (Exception e) {
            errors.add(new ImportError(rowIndex, null, e.getMessage()));
        }
    });

    return ResponseEntity.ok(Map.of(
        "success", successCount.get(),
        "errors", errors
    ));
}
```

### Memory Configuration

```yaml
simplix:
  excel:
    import:
      batchSize: 1000      # 배치 크기
      maxRows: 1000000     # 최대 처리 행 수
```

---

## Best Practices

### 1. Import DTO 분리

엔티티와 별도의 Import DTO 사용:

```java
// Good: Import 전용 DTO
public class UserImportDto {
    @ExcelColumn(order = 1)
    @NotBlank
    private String name;

    @ExcelColumn(order = 2)
    @Email
    private String email;

    // 변환 메서드
    public User toEntity() {
        User user = new User();
        user.setName(this.name);
        user.setEmail(this.email);
        return user;
    }
}

// Bad: 엔티티 직접 사용
@Entity
public class User {
    @Id
    @GeneratedValue
    private Long id;  // 가져오기 시 ID 충돌 가능

    private String password;  // 민감 필드 노출
}
```

### 2. 트랜잭션 관리

대용량 가져오기 시 트랜잭션 분리:

```java
@Service
@RequiredArgsConstructor
public class UserImportService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBatch(List<User> users) {
        userRepository.saveAll(users);
    }
}
```

### 3. 에러 처리 전략

가져오기 실패 시 처리 전략:

```java
// 전체 롤백 (기본)
@Transactional
public ImportResult importUsers(MultipartFile file) {
    List<User> users = importer.importFromExcel(file);
    // 하나라도 실패하면 전체 롤백
    userRepository.saveAll(users);
    return new ImportResult(users.size());
}

// 부분 성공 허용
public ImportResult importUsersPartial(MultipartFile file) {
    List<User> users = importer.importFromExcel(file);
    int success = 0;
    List<ImportError> errors = new ArrayList<>();

    for (int i = 0; i < users.size(); i++) {
        try {
            userRepository.save(users.get(i));
            success++;
        } catch (Exception e) {
            errors.add(new ImportError(i + 2, null, e.getMessage()));
        }
    }

    return new ImportResult(success, errors);
}
```

### 4. 파일 검증

가져오기 전 파일 검증:

```java
@PostMapping("/users/import")
public ResponseEntity<?> importUsers(@RequestParam MultipartFile file) {
    // 파일 검증
    if (file.isEmpty()) {
        return ResponseEntity.badRequest().body("File is empty");
    }

    String filename = file.getOriginalFilename();
    if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
        return ResponseEntity.badRequest().body("Invalid file type");
    }

    if (file.getSize() > 10 * 1024 * 1024) {  // 10MB 제한
        return ResponseEntity.badRequest().body("File too large");
    }

    // 가져오기 수행
    // ...
}
```

### 5. 중복 처리

중복 데이터 처리 전략:

```java
@PostMapping("/users/import")
public ResponseEntity<?> importUsers(@RequestParam MultipartFile file) {
    List<User> users = importer.importFromExcel(file);

    int created = 0;
    int updated = 0;

    for (User user : users) {
        Optional<User> existing = userRepository.findByEmail(user.getEmail());

        if (existing.isPresent()) {
            // 업데이트
            User entity = existing.get();
            entity.setName(user.getName());
            userRepository.save(entity);
            updated++;
        } else {
            // 신규 생성
            userRepository.save(user);
            created++;
        }
    }

    return ResponseEntity.ok(Map.of("created", created, "updated", updated));
}
```

---

## Troubleshooting

### Import 타입 변환 실패

**증상**: 가져오기 시 타입 변환 오류

**해결**:
```java
// 날짜 포맷 명시
importer.setDateFormat("yyyy/MM/dd");
importer.setDateTimeFormat("yyyy/MM/dd HH:mm:ss");
```

### 한글 깨짐 (CSV)

**증상**: CSV 가져오기 시 한글 깨짐

**해결**:
```java
// 인코딩 명시
importer.setEncoding("EUC-KR");  // 레거시 파일
importer.setEncoding("UTF-8");   // UTF-8 파일
```

### 컬럼 매핑 오류

**증상**: 데이터가 잘못된 필드에 매핑됨

**해결**:
```java
// 명시적 컬럼 매핑
Map<Integer, String> mapping = new HashMap<>();
mapping.put(0, "fieldA");
mapping.put(1, "fieldB");
importer.setColumnMapping(mapping);
```

### OutOfMemoryError

**증상**: 대용량 파일 가져오기 시 메모리 부족

**해결**:
```java
// 배치 처리 사용
importer.importFromExcel(file, batch -> {
    repository.saveAll(batch);
}, 500);  // 배치 크기 줄이기
```

### 빈 행 처리

**증상**: 빈 행이 null 객체로 생성됨

**해결**:
```java
List<User> users = importer.importFromExcel(file);

// null 및 빈 객체 필터링
users = users.stream()
    .filter(Objects::nonNull)
    .filter(u -> u.getName() != null || u.getEmail() != null)
    .collect(Collectors.toList());
```

### 숫자 형식 오류

**증상**: Excel의 숫자가 문자열로 저장된 경우 변환 실패

**해결**:
```java
// DTO에서 String으로 받아서 변환
public class ImportDto {
    private String amountStr;  // Excel에서 받음

    public BigDecimal getAmount() {
        if (amountStr == null || amountStr.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(amountStr.replaceAll(",", ""));
    }
}
```

### 날짜 인식 실패

**증상**: Excel 날짜가 숫자(시리얼 값)로 읽힘

**해결**:
```java
// Excel 날짜 시리얼 값 변환
public LocalDate convertExcelDate(double serialDate) {
    return LocalDate.of(1899, 12, 30).plusDays((long) serialDate);
}
```

---

## Related Documents

- [Overview](ko/excel/overview.md) - 모듈 개요
- [Export Guide](ko/excel/export-guide.md) - 내보내기 가이드
