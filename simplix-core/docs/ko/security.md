# Security Guide

## Overview

SimpliX Core는 웹 애플리케이션 보안을 위한 다층 방어 시스템을 제공합니다:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Input Layer                               │
│  ┌─────────────┐ ┌─────────────────┐ ┌────────────────────────┐ │
│  │ @SafeHtml   │ │SqlInjection     │ │ @ValidateWith          │ │
│  │ - XSS 검증  │ │Validator        │ │ - 커스텀 서비스 검증   │ │
│  └─────────────┘ └─────────────────┘ └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Sanitization Layer                          │
│  ┌──────────────┐ ┌──────────────┐                              │
│  │HtmlSanitizer │ │InputSanitizer│                              │
│  │- OWASP 기반  │ │- 일반 입력   │                              │
│  └──────────────┘ └──────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Storage Layer                              │
│  ┌──────────────┐ ┌────────────────────┐ ┌───────────────────┐  │
│  │HashingUtils  │ │HashingAttribute    │ │MaskingConverter   │  │
│  │- SHA-256/512 │ │Converter (PBKDF2)  │ │- JPA 자동 마스킹  │  │
│  └──────────────┘ └────────────────────┘ └───────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Output Layer                              │
│  ┌────────────────┐ ┌─────────────────┐ ┌────────────────────┐  │
│  │DataMaskingUtils│ │IpAddressMasking │ │LogMasker           │  │
│  │- 다양한 마스킹 │ │Utils - GDPR 준수│ │- 로그 민감정보 처리│  │
│  └────────────────┘ └─────────────────┘ └────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Input Validation

### @SafeHtml

XSS 공격을 방지하는 Bean Validation 어노테이션입니다.

```java
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeHtmlValidator.class)
public @interface SafeHtml {
    String message() default "HTML content contains potentially dangerous elements";
    boolean allowBasicFormatting() default false;
    boolean allowLinks() default false;
    int maxLength() default -1;
    String[] allowedTags() default {};
}
```

### 사용 예제

```java
public class ArticleDto {
    private String title;

    // 모든 HTML 태그 제거 (기본값)
    @SafeHtml
    private String summary;

    // 기본 서식만 허용
    @SafeHtml(allowBasicFormatting = true)
    private String description;

    // 서식 + 링크 허용
    @SafeHtml(allowBasicFormatting = true, allowLinks = true)
    private String content;

    // 커스텀 태그 + 길이 제한
    @SafeHtml(
        allowedTags = {"p", "br", "strong"},
        maxLength = 10000
    )
    private String body;
}
```

### 컨트롤러에서 사용

```java
@PostMapping("/articles")
public ResponseEntity<Article> createArticle(@Valid @RequestBody ArticleDto dto) {
    // @SafeHtml 검증 자동 수행
    return ResponseEntity.ok(articleService.create(dto));
}
```

---

### SqlInjectionValidator

SQL Injection 공격을 탐지하고 방지합니다.

```java
SqlInjectionValidator validator = new SqlInjectionValidator();

// 기본 검증
boolean isSafe = validator.isSafeInput("Hello World");        // true
boolean isUnsafe = validator.isSafeInput("'; DROP TABLE;--"); // false

// 이메일 검증
boolean safeEmail = validator.isSafeEmail("user@example.com");    // true
boolean unsafeEmail = validator.isSafeEmail("' OR '1'='1");       // false

// 전화번호 검증
boolean safePhone = validator.isSafePhoneNumber("+821012345678"); // true

// UUID 검증
boolean validUuid = validator.isValidUUID("550e8400-e29b-41d4-a716-446655440000"); // true

// 해시 검증 (Base64)
boolean validHash = validator.isSafeHash("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72..."); // true

// LIKE 쿼리 이스케이프
String escaped = validator.escapeLikePattern("10%"); // "10\\%"

// 정렬 필드 검증
List<String> allowed = List.of("name", "createdAt", "status");
boolean validSort = validator.isValidSortField("name", allowed); // true
```

### 탐지 패턴

| 카테고리 | 탐지 패턴 |
|----------|-----------|
| SQL 키워드 | ALTER, CREATE, DELETE, DROP, EXEC, INSERT, MERGE, SELECT, UPDATE, UNION |
| 주석 | --, #, /*, */, @@, @ |
| 특수 문자 | ', ; |
| Hex 인코딩 | 0x... |
| 시간 지연 공격 | WAITFOR, BENCHMARK, SLEEP, DELAY |
| 시스템 테이블 | INFORMATION_SCHEMA, SYSOBJECTS, SYSCOLUMNS |

---

### @ValidateWith

서비스 메서드를 통한 커스텀 검증입니다.

```java
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidateWithValidator.class)
public @interface ValidateWith {
    String message() default "Invalid value";
    String service();  // "serviceBeanName.methodName" 형식
}
```

### 사용 예제

**필드 검증:**
```java
public class UserDto {
    @ValidateWith(
        service = "userPositionService.validateId",
        message = "Invalid position ID"
    )
    private String positionId;
}

@Service
public class UserPositionService {
    public boolean validateId(String id) {
        return existsById(id);
    }
}
```

**DTO 전체 검증:**
```java
@ValidateWith(
    service = "userAccountService.validateCreateDto",
    message = "Invalid user account data"
)
public class UserAccountCreateDTO {
    private String username;
    private String departmentId;
}

@Service
public class UserAccountService {
    public boolean validateCreateDto(UserAccountCreateDTO dto) {
        // 복잡한 비즈니스 규칙 검증
        return validateBusinessRules(dto);
    }
}
```

---

## HTML Sanitization

### HtmlSanitizer

OWASP Java HTML Sanitizer를 기반으로 한 XSS 방지 유틸리티입니다.

### SanitizationPolicy

| 정책 | 설명 | 허용 태그 |
|------|------|-----------|
| `STRICT` | 모든 HTML 제거 | 없음 |
| `PLAIN_TEXT` | HTML 제거, 텍스트만 유지 | 없음 (텍스트만) |
| `BASIC_FORMATTING` | 기본 서식만 허용 | b, i, u, em, strong, p, br, span, div, h1-h6, ul, ol, li, blockquote, pre, code |
| `FORMATTING_WITH_LINKS` | 서식 + 링크 허용 | 위 + a (href, title) |
| `RICH_TEXT` | 리치 텍스트 전체 허용 | 위 + table, img 등 |

### 사용 예제

```java
// 기본 (STRICT - 모든 HTML 제거)
String clean = HtmlSanitizer.sanitize("<script>alert('xss')</script>Hello");
// "Hello"

// 정책 지정
String formatted = HtmlSanitizer.sanitizeWithPolicy(
    "<b>Bold</b><script>alert('xss')</script>",
    SanitizationPolicy.BASIC_FORMATTING
);
// "<b>Bold</b>"

// 커스텀 옵션
String custom = HtmlSanitizer.sanitize(
    html,
    true,   // allowBasicFormatting
    true,   // allowLinks
    new String[]{"mark", "sub", "sup"}  // customTags
);

// HTML 이스케이프 (태그를 텍스트로 표시)
String escaped = HtmlSanitizer.escapeHtml("<script>");
// "&lt;script&gt;"

// 위험 콘텐츠 탐지
boolean dangerous = HtmlSanitizer.containsDangerousContent(
    "<img src='x' onerror='alert(1)'>"
);
// true
```

### 인코딩 공격 탐지

HtmlSanitizer는 다양한 인코딩 공격을 탐지합니다:

```java
// URL 인코딩
"%3Cscript%3E"  // <script> 인코딩 → 탐지

// HTML 엔티티
"&#x3C;script&#x3E;"  // <script> 엔티티 → 탐지

// data: URI
"data:text/html,<script>..."  // 탐지

// 이벤트 핸들러 인코딩
"%6F%6E%65%72%72%6F%72"  // onerror 인코딩 → 탐지
```

---

## Hashing

### HashingUtils

검색용 해시 생성 유틸리티입니다.

```java
// SHA-256 해시 (기본)
String hash = HashingUtils.hash("user@example.com");
// "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols="

// SHA-512 해시
String hash512 = HashingUtils.hash("user@example.com", HashingUtils.SHA_512);

// 해시 유효성 검증
boolean valid256 = HashingUtils.isValidSha256Hash(hash);   // true
boolean valid512 = HashingUtils.isValidSha512Hash(hash512); // true
boolean validAny = HashingUtils.isValidHash(hash);          // true
```

### 사용 사례

| 사용 사례 | 도구 | 이유 |
|-----------|------|------|
| 이메일/전화번호 검색 인덱스 | HashingUtils | 빠름, 결정적, Salt 불필요 |
| 비밀번호 저장 | HashingAttributeConverter (PBKDF2) | 느림, Salt 적용, 안전 |
| 캐시 키 | HashingUtils | 빠름, 단순 |
| 민감한 개인정보 | HashingAttributeConverter | Salt로 추가 보안 |

### 이메일 검색 인덱스 예제

```java
@Entity
public class User {
    @Id
    private Long id;

    // 암호화된 이메일 (검색 불가)
    @Convert(converter = EncryptionConverter.class)
    private String email;

    // 검색용 해시 인덱스
    @Column(name = "email_hash")
    private String emailHash;

    @PrePersist
    @PreUpdate
    public void updateEmailHash() {
        this.emailHash = HashingUtils.hash(email.toLowerCase().trim());
    }
}

// 검색
public User findByEmail(String email) {
    String hash = HashingUtils.hash(email.toLowerCase().trim());
    return userRepository.findByEmailHash(hash);
}
```

---

## Data Masking

### DataMaskingUtils

민감 데이터 마스킹 유틸리티입니다.

### 지원 마스킹 타입

| 메서드 | 입력 | 출력 |
|--------|------|------|
| `maskEmail` | `user@example.com` | `us***@example.com` |
| `maskPhoneNumber` | `010-1234-5678` | `010-****-****` |
| `maskCreditCard` | `1234-5678-9012-3456` | `****-****-****-3456` |
| `maskRRN` | `901231-1234567` | `901231-*******` |
| `maskIpAddress` | `192.168.1.123` | `192.168.1.0` |
| `maskPaymentToken` | `pm_1234567890abcdef` | `pm_****cdef` |
| `maskGeneric` | 사용자 정의 | 앞/뒤 N자 유지 |
| `maskFull` | 모든 값 | `*******` |

### 사용 예제

```java
// 이메일
String maskedEmail = DataMaskingUtils.maskEmail("john.doe@example.com");
// "jo***@example.com"

// 전화번호
String maskedPhone = DataMaskingUtils.maskPhoneNumber("010-1234-5678");
// "010-****-****"

// 신용카드
String maskedCard = DataMaskingUtils.maskCreditCard("1234-5678-9012-3456");
// "****-****-****-3456"

// 주민등록번호
String maskedRRN = DataMaskingUtils.maskRRN("901231-1234567");
// "901231-*******"

// IP 주소 (GDPR 준수)
String maskedIp = DataMaskingUtils.maskIpAddress("192.168.1.123");
// "192.168.1.0"

// 커스텀 마스킹
String custom = DataMaskingUtils.maskGeneric("1234567890", 2, 3);
// "12*****890"

// 마스킹 여부 확인
boolean isMasked = DataMaskingUtils.isMasked("010-****-****");
// true
```

---

### IpAddressMaskingUtils

GDPR 준수를 위한 IP 주소 마스킹입니다.

```java
// IPv4 마스킹 (마지막 옥텟 제거)
String maskedIpv4 = IpAddressMaskingUtils.maskSubnetLevel("192.168.1.123");
// "192.168.1.0"

// IPv6 마스킹 (마지막 세그먼트 제거)
String maskedIpv6 = IpAddressMaskingUtils.maskSubnetLevel("2001:db8::7334");
// "2001:db8::0"

// 마스킹 여부 확인
boolean isMasked = IpAddressMaskingUtils.isMasked("192.168.1.0");
// true

// IP 형식 검증
boolean isValid = IpAddressMaskingUtils.isValidIpAddress("192.168.1.1");
// true
```

---

### LogMasker

로그 메시지에서 민감 정보를 자동 탐지하고 마스킹합니다.

```java
// 전체 마스킹
String masked = LogMasker.maskSensitiveData(
    "User email: john@example.com, phone: 010-1234-5678"
);
// "User email: jo***@example.com, phone: 010-****-****"

// 개별 마스킹
String maskedRrn = LogMasker.maskRRN("주민번호: 901231-1234567");
// "주민번호: 901231-*******"

String maskedCard = LogMasker.maskCreditCard("카드번호: 1234-5678-9012-3456");
// "카드번호: ****-****-****-3456"

String maskedPhone = LogMasker.maskPhoneNumber("전화: 010-1234-5678");
// "전화: 010-****-****"

String maskedEmail = LogMasker.maskEmail("이메일: user@example.com");
// "이메일: us***@example.com"

// 비밀번호 마스킹 (JSON, 쿼리 스트링)
String maskedPwd = LogMasker.maskPassword("password=secret123");
// "password=********"

// IP 마스킹
String maskedIp = LogMasker.maskIPAddress("IP: 192.168.1.100");
// "IP: 192.168.1.0"

// 민감 정보 탐지
boolean hasSensitive = LogMasker.containsSensitiveData("email: user@test.com");
// true

// 필드별 마스킹
String maskedField = LogMasker.maskFieldValue("email", "user@example.com");
// "us***@example.com"
```

### 탐지 패턴

| 타입 | 패턴 |
|------|------|
| 주민등록번호 | `YYMMDD-XXXXXXX` |
| 신용카드 | `XXXX-XXXX-XXXX-XXXX` |
| 전화번호 | `01X-XXXX-XXXX`, `+XX-XXX-XXXX` |
| 이메일 | `xxx@xxx.xxx` |
| 비밀번호 | `password=xxx`, `"pwd": "xxx"` |
| IP 주소 | `XXX.XXX.XXX.XXX` |

---

## JPA Entity Masking

### @MaskSensitive

엔티티 필드에 자동 마스킹을 적용합니다.

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskSensitive {
    MaskType type() default MaskType.FULL;
    int keepFirst() default 3;
    int keepLast() default 4;
    int minLength() default 0;
    String maskChar() default "*";
    boolean enabled() default true;
}
```

### MaskType

| 타입 | 설명 | 예시 |
|------|------|------|
| `FULL` | 전체 마스킹 | `********` |
| `PARTIAL` | 부분 마스킹 | `abc*****xyz` |
| `EMAIL` | 이메일 마스킹 | `u***@example.com` |
| `PHONE` | 전화번호 마스킹 | `010-****-****` |
| `CREDIT_CARD` | 카드 마스킹 | `****-****-****-3456` |
| `PAYMENT_TOKEN` | 토큰 마스킹 | `pm_****cdef` |
| `IP_ADDRESS` | IP 마스킹 | `192.168.*.*` |
| `JSON` | JSON 마스킹 | LogMasker 적용 |
| `NONE` | 마스킹 안함 | 조건부 비활성화 |

### 사용 예제

```java
@Entity
@EntityListeners(UniversalMaskingListener.class)
public class Payment {

    @Id
    private Long id;

    @MaskSensitive(type = MaskType.CREDIT_CARD)
    private String cardNumber;

    @MaskSensitive(type = MaskType.PAYMENT_TOKEN)
    private String paymentMethodId;

    @MaskSensitive(type = MaskType.PARTIAL, keepFirst = 4, keepLast = 4)
    private String accountNumber;

    @MaskSensitive(type = MaskType.EMAIL)
    private String billingEmail;

    @MaskSensitive(type = MaskType.IP_ADDRESS)
    private String clientIp;

    @MaskSensitive(type = MaskType.JSON)
    private String auditDetails;
}
```

### UniversalMaskingListener

`@EntityListeners(UniversalMaskingListener.class)`를 추가하면 `@PostLoad` 시점에서 자동으로 마스킹이 적용됩니다.

---

## 보안 체크리스트

### 입력 검증

- [ ] 모든 사용자 입력에 `@SafeHtml` 또는 `SqlInjectionValidator` 적용
- [ ] Bean Validation (`@Valid`) 사용
- [ ] 파일 업로드 시 MIME 타입 검증
- [ ] URL 파라미터 검증

### XSS 방지

- [ ] HTML 출력 시 `HtmlSanitizer.sanitize()` 사용
- [ ] JSON 응답 시 `HtmlSanitizer.escapeHtml()` 고려
- [ ] 적절한 `SanitizationPolicy` 선택

### SQL Injection 방지

- [ ] JPA/MyBatis 파라미터 바인딩 사용
- [ ] 동적 쿼리 시 `SqlInjectionValidator.isSafeInput()` 검증
- [ ] ORDER BY 필드명 화이트리스트 검증

### 민감 데이터 보호

- [ ] 비밀번호는 BCrypt 사용 (HashingUtils가 아닌)
- [ ] 검색용 인덱스는 `HashingUtils.hash()` 사용
- [ ] 로그 출력 전 `LogMasker.maskSensitiveData()` 적용
- [ ] API 응답에 민감 데이터 마스킹

### GDPR 준수

- [ ] IP 주소는 `IpAddressMaskingUtils.maskSubnetLevel()` 사용
- [ ] 감사 로그에 마스킹된 데이터 저장
- [ ] 데이터 삭제 요청 처리 절차 마련

---

## Related Documents

- [Overview (아키텍처 개요)](./overview.md) - 모듈 구조
- [Entity & Repository Guide (엔티티/리포지토리)](./entity-repository.md) - 베이스 엔티티, 복합 키
- [Tree Structure Guide (트리 구조)](./tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](./type-converters.md) - Boolean, Enum, DateTime 변환
- [Exception & API Guide (예외/API)](./exception-api.md) - 에러 코드, API 응답
- [Cache Guide (캐시)](./cache.md) - CacheManager, CacheProvider
