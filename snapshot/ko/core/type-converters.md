# Type Converters Guide

## Overview

SimpliX Core는 세 가지 타입 변환 시스템을 제공합니다:

| 변환기 | 용도 | 지원 타입 |
|--------|------|-----------|
| **BooleanConverter** | Boolean ↔ String | true/false, 1/0, yes/no, y/n, on/off |
| **EnumConverter** | Enum ↔ String/Map | 모든 Enum 타입 |
| **DateTimeConverter** | Temporal ↔ String | LocalDateTime, LocalDate, LocalTime, ZonedDateTime, OffsetDateTime, Instant |

모든 변환기는 **인터페이스 + 기본 구현체** 패턴을 사용합니다:

```java
// 인터페이스의 정적 팩토리 메서드
BooleanConverter converter = BooleanConverter.getDefault();
EnumConverter enumConverter = EnumConverter.getDefault();
DateTimeConverter dateConverter = DateTimeConverter.getDefault();
```

---

## BooleanConverter

### 인터페이스

```java
public interface BooleanConverter {
    Boolean fromString(String value);
    String toString(Boolean value);

    static BooleanConverter getDefault() {
        return new StandardBooleanConverter();
    }
}
```

### 지원 값

| 입력 | 결과 |
|------|------|
| `"true"`, `"1"`, `"yes"`, `"y"`, `"on"` | `true` |
| `"false"`, `"0"`, `"no"`, `"n"`, `"off"` | `false` |
| `null`, `""` | `null` |
| 기타 | `IllegalArgumentException` |

**대소문자 무시** - `"TRUE"`, `"True"`, `"true"` 모두 동일하게 처리됩니다.

### 사용 예제

```java
BooleanConverter converter = BooleanConverter.getDefault();

// String → Boolean
Boolean result1 = converter.fromString("yes");    // true
Boolean result2 = converter.fromString("0");      // false
Boolean result3 = converter.fromString("ON");     // true (대소문자 무시)
Boolean result4 = converter.fromString(null);     // null

// Boolean → String
String str1 = converter.toString(true);   // "true"
String str2 = converter.toString(false);  // "false"
String str3 = converter.toString(null);   // null
```

---

## EnumConverter

### 인터페이스

```java
public interface EnumConverter {
    <T extends Enum<?>> T fromString(String value, Class<T> enumType);
    String toString(Enum<?> value);
    Map<String, Object> toMap(Enum<?> value);

    static EnumConverter getDefault() {
        return new StandardEnumConverter();
    }
}
```

### 특징

1. **대소문자 무시 매칭** - `"active"`, `"ACTIVE"`, `"Active"` 모두 `Status.ACTIVE`로 변환
2. **toMap() 지원** - Enum의 모든 필드를 Map으로 변환

### 사용 예제

```java
// Enum 정의
public enum Status {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    PENDING("대기중");

    private final String label;

    Status(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

```java
EnumConverter converter = EnumConverter.getDefault();

// String → Enum
Status status1 = converter.fromString("ACTIVE", Status.class);   // Status.ACTIVE
Status status2 = converter.fromString("active", Status.class);   // Status.ACTIVE (대소문자 무시)
Status status3 = converter.fromString("pending", Status.class);  // Status.PENDING

// Enum → String
String name = converter.toString(Status.ACTIVE);  // "ACTIVE"

// Enum → Map
Map<String, Object> map = converter.toMap(Status.ACTIVE);
// {
//   "type": "Status",
//   "value": "ACTIVE",
//   "label": "활성"
// }
```

### toMap() 동작

`toMap()`은 Enum의 모든 getter 메서드를 리플렉션으로 추출합니다:

```java
// 복잡한 Enum
public enum Priority {
    HIGH(1, "높음", "#ff0000"),
    MEDIUM(2, "중간", "#ffff00"),
    LOW(3, "낮음", "#00ff00");

    private final int order;
    private final String label;
    private final String color;

    // constructor, getters...
}
```

```java
Map<String, Object> map = converter.toMap(Priority.HIGH);
// {
//   "type": "Priority",
//   "value": "HIGH",
//   "order": 1,
//   "label": "높음",
//   "color": "#ff0000"
// }
```

---

## DateTimeConverter

### 인터페이스

```java
public interface DateTimeConverter {
    <T extends Temporal> T fromString(String value, Class<T> targetType);
    String toString(Temporal value);

    static DateTimeConverter getDefault() {
        return new StandardDateTimeConverter();
    }

    static DateTimeConverter of(ZoneId zoneId) {
        return new StandardDateTimeConverter(zoneId);
    }
}
```

### 지원 타입

| 타입 | 설명 |
|------|------|
| `LocalDateTime` | 날짜 + 시간 (타임존 없음) |
| `LocalDate` | 날짜만 |
| `LocalTime` | 시간만 |
| `ZonedDateTime` | 날짜 + 시간 + 타임존 |
| `OffsetDateTime` | 날짜 + 시간 + UTC 오프셋 |
| `Instant` | UTC 시점 |

### 지원 포맷

**LocalDateTime:**
```
yyyy-MM-dd'T'HH:mm:ss.SSSXXX   (ISO-8601 with timezone)
yyyy-MM-dd'T'HH:mm:ss.SSS      (ISO-8601 without timezone)
yyyy-MM-dd'T'HH:mm:ss          (ISO-8601 without millis)
yyyy-MM-dd HH:mm:ss.SSS        (Common datetime with millis)
yyyy-MM-dd HH:mm:ss            (Common datetime)
yyyy-MM-dd HH:mm               (Short datetime)
yyyy.MM.dd HH:mm:ss            (Dot format)
yyyy/MM/dd HH:mm:ss            (Slash format)
yyyyMMddHHmmss                 (Compact format)
```

**LocalDate:**
```
yyyy-MM-dd                     (ISO date)
yyyy/MM/dd                     (Slash format)
yyyyMMdd                       (Compact format)
yyyy.MM.dd                     (Dot format)
dd-MM-yyyy                     (European format)
MM-dd-yyyy                     (US format)
```

**LocalTime:**
```
HH:mm:ss.SSS                   (Full time with millis)
HH:mm:ss                       (Full time)
HH:mm                          (Short time)
hh:mm:ss a                     (12-hour format)
hh:mm a                        (12-hour format short)
HHmmss                         (Compact format)
```

### 사용 예제

```java
DateTimeConverter converter = DateTimeConverter.getDefault();

// String → LocalDateTime (다양한 포맷 자동 감지)
LocalDateTime dt1 = converter.fromString("2024-12-15T10:30:00", LocalDateTime.class);
LocalDateTime dt2 = converter.fromString("2024-12-15 10:30:00", LocalDateTime.class);
LocalDateTime dt3 = converter.fromString("2024.12.15 10:30:00", LocalDateTime.class);
LocalDateTime dt4 = converter.fromString("20241215103000", LocalDateTime.class);

// String → LocalDate
LocalDate date1 = converter.fromString("2024-12-15", LocalDate.class);
LocalDate date2 = converter.fromString("2024/12/15", LocalDate.class);
LocalDate date3 = converter.fromString("20241215", LocalDate.class);

// String → LocalTime
LocalTime time1 = converter.fromString("10:30:00", LocalTime.class);
LocalTime time2 = converter.fromString("10:30", LocalTime.class);
LocalTime time3 = converter.fromString("10:30:00 AM", LocalTime.class);

// Temporal → String (ISO-8601 형식)
String str = converter.toString(LocalDateTime.now());
// "2024-12-15T10:30:00.000+09:00"
```

### 타임존 설정

```java
// 특정 타임존으로 변환기 생성
DateTimeConverter converter = DateTimeConverter.of(ZoneId.of("Asia/Seoul"));

// UTC로 변환기 생성
DateTimeConverter utcConverter = DateTimeConverter.of(ZoneId.of("UTC"));
```

### 타입 간 변환

변환기는 자동으로 타입 간 변환을 수행합니다:

```java
DateTimeConverter converter = DateTimeConverter.getDefault();

// ZonedDateTime 문자열 → LocalDateTime으로 변환
LocalDateTime ldt = converter.fromString(
    "2024-12-15T10:30:00+09:00[Asia/Seoul]",
    LocalDateTime.class
);

// LocalDateTime → Instant로 변환 (기본 타임존 적용)
Instant instant = converter.fromString(
    "2024-12-15T10:30:00",
    Instant.class
);
```

---

## SimpliXLabeledEnum

라벨이 있는 Enum을 위한 인터페이스입니다.

```java
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public interface SimpliXLabeledEnum {
    String name();      // Enum.name() 상속
    String getLabel();  // 라벨 반환
}
```

### 구현 예제

```java
public enum UserStatus implements SimpliXLabeledEnum {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    SUSPENDED("정지"),
    DELETED("삭제됨");

    private final String label;

    UserStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
```

### JSON 직렬화

`@JsonFormat(shape = JsonFormat.Shape.OBJECT)`로 인해 객체로 직렬화됩니다:

```json
{
  "status": {
    "name": "ACTIVE",
    "label": "활성"
  }
}
```

---

## Jackson Serializers

SimpliX Core는 Jackson과 통합된 직렬화기를 제공합니다.

### SimpliXBooleanSerializer / Deserializer

Boolean을 문자열로 직렬화합니다.

```java
// 직렬화
Boolean value = true;
// → "true"

// 역직렬화
"yes" → true
"0"   → false
```

**등록:**
```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Boolean.class, new SimpliXBooleanSerializer());
        module.addDeserializer(Boolean.class, new SimpliXBooleanDeserializer());
        mapper.registerModule(module);
        return mapper;
    }
}
```

### SimpliXEnumSerializer

Enum을 Map 형태의 객체로 직렬화합니다.

```java
// 직렬화
Status.ACTIVE
// → {"type": "Status", "value": "ACTIVE", "label": "활성"}
```

**등록:**
```java
module.addSerializer(Enum.class, new SimpliXEnumSerializer());
```

### SimpliXDateTimeSerializer / Deserializer

Temporal 타입을 ISO-8601 형식으로 직렬화합니다.

```java
// 직렬화 (타임존 포함)
LocalDateTime.of(2024, 12, 15, 10, 30, 0)
// → "2024-12-15T10:30:00.000+09:00"

// 역직렬화 (다양한 포맷 지원)
"2024-12-15T10:30:00" → LocalDateTime
"2024-12-15" → LocalDate
"10:30:00" → LocalTime
```

**등록:**
```java
SimpliXDateTimeSerializer serializer = new SimpliXDateTimeSerializer(ZoneId.of("Asia/Seoul"));
SimpliXDateTimeDeserializer deserializer = new SimpliXDateTimeDeserializer(ZoneId.of("Asia/Seoul"));

module.addSerializer(Temporal.class, serializer);
module.addDeserializer(LocalDateTime.class, deserializer);
module.addDeserializer(LocalDate.class, deserializer);
module.addDeserializer(LocalTime.class, deserializer);
```

---

## 커스텀 변환기 구현

### Boolean 변환기

```java
public class KoreanBooleanConverter implements BooleanConverter {
    @Override
    public Boolean fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.toLowerCase().trim();
        return switch (normalized) {
            case "예", "네", "y", "yes", "true", "1" -> true;
            case "아니오", "아니요", "n", "no", "false", "0" -> false;
            default -> throw new IllegalArgumentException("Invalid value: " + value);
        };
    }

    @Override
    public String toString(Boolean value) {
        return value == null ? null : (value ? "예" : "아니오");
    }
}
```

### DateTime 변환기

```java
public class CustomDateTimeConverter implements DateTimeConverter {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분");

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Temporal> T fromString(String value, Class<T> targetType) {
        if (value == null) return null;
        LocalDateTime ldt = LocalDateTime.parse(value, formatter);
        return (T) ldt;  // 간소화된 예제
    }

    @Override
    public String toString(Temporal value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(formatter);
        }
        return value.toString();
    }
}
```

---

## 통합 사용 예제

### DTO with Converters

```java
@Getter
@Setter
public class UserDto {
    private Long id;
    private String username;

    @JsonSerialize(using = SimpliXBooleanSerializer.class)
    @JsonDeserialize(using = SimpliXBooleanDeserializer.class)
    private Boolean active;

    private UserStatus status;  // SimpliXLabeledEnum 구현

    @JsonSerialize(using = SimpliXDateTimeSerializer.class)
    @JsonDeserialize(using = SimpliXDateTimeDeserializer.class)
    private LocalDateTime createdAt;
}
```

### JSON 결과

```json
{
  "id": 1,
  "username": "john",
  "active": "true",
  "status": {
    "name": "ACTIVE",
    "label": "활성"
  },
  "createdAt": "2024-12-15T10:30:00.000+09:00"
}
```

---

## Related Documents

- [Overview (아키텍처 개요)](ko/core/overview.md) - 모듈 구조
- [Entity & Repository Guide (엔티티/리포지토리)](ko/core/entity-repository.md) - 베이스 엔티티, 복합 키
- [Tree Structure Guide (트리 구조)](ko/core/tree-structure.md) - TreeEntity, SimpliXTreeService
- [Security Guide (보안)](ko/core/security.md) - XSS 방지, 해싱, 마스킹
- [Exception & API Guide (예외/API)](ko/core/exception-api.md) - 에러 코드, API 응답
- [Cache Guide (캐시)](ko/core/cache.md) - CacheManager, CacheProvider
