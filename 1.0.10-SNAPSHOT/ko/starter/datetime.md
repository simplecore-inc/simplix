# DateTime & Timezone Guide

## Overview

SimpliX는 타임존을 중앙에서 관리하여 일관된 날짜/시간 처리를 제공합니다. 특히 글로벌 애플리케이션에서 발생하는 타임존 문제를 해결합니다.

## Timezone Resolution Priority

SimpliX는 다음 순서로 애플리케이션 타임존을 결정합니다:

```
1. simplix.date-time.default-timezone  (최우선)
2. spring.jackson.time-zone
3. user.timezone 시스템 속성
4. 시스템 기본 타임존              (최후순위)
```

## Configuration

```yaml
simplix:
  date-time:
    # 애플리케이션 기본 타임존
    default-timezone: Asia/Seoul

    # DB 저장시 UTC 변환 여부
    use-utc-for-database: true

    # LocalDateTime 타임존 정규화
    normalize-timezone: true
```

### use-utc-for-database

`true` (권장):
- 모든 OffsetDateTime이 DB 저장 전 UTC로 변환됩니다
- DB에서 읽을 때 애플리케이션 타임존으로 변환됩니다
- 글로벌 서비스에서 일관성 보장

`false`:
- 타임존 정보가 그대로 저장됩니다
- 단일 타임존 서비스에서 사용

### normalize-timezone

`true`:
- LocalDateTime이 애플리케이션 타임존으로 가정됩니다
- ModelMapper에서 LocalDateTime -> OffsetDateTime 변환시 적용

`false`:
- LocalDateTime이 그대로 유지됩니다
- 타임존 정보 없이 저장

## SimpliXTimezoneService

타임존 관련 모든 작업을 위한 중앙 서비스입니다.

### Injection

```java
@Service
public class MyService {

    private final SimpliXTimezoneService timezoneService;

    public MyService(SimpliXTimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }
}
```

### Methods

```java
// 애플리케이션 ZoneId 조회
ZoneId zoneId = timezoneService.getApplicationZoneId();

// 애플리케이션 ZoneOffset 조회
ZoneOffset offset = timezoneService.getApplicationZoneOffset();

// 설정 확인
boolean useUtc = timezoneService.isUseUtcForDatabase();
boolean normalize = timezoneService.isNormalizeTimezone();

// LocalDateTime -> OffsetDateTime (애플리케이션 타임존)
LocalDateTime local = LocalDateTime.now();
OffsetDateTime app = timezoneService.normalizeToApplicationTimezone(local);

// DB 저장용 UTC 변환
OffsetDateTime utc = timezoneService.normalizeForDatabase(app);

// DB에서 읽은 값을 애플리케이션 타임존으로
OffsetDateTime fromDb = timezoneService.normalizeFromDatabase(utc);
```

## JPA AttributeConverter

SimpliX는 JPA 엔티티의 날짜/시간 필드를 자동으로 변환합니다.

### SimpliXOffsetDateTimeConverter

OffsetDateTime을 DB 저장 전 UTC로 변환하고, 조회시 애플리케이션 타임존으로 변환합니다.

```java
@Entity
public class Event {

    @Convert(converter = SimpliXOffsetDateTimeConverter.class)
    private OffsetDateTime startTime;

    @Convert(converter = SimpliXOffsetDateTimeConverter.class)
    private OffsetDateTime endTime;
}
```

### SimpliXLocalDateTimeConverter

LocalDateTime을 OffsetDateTime(UTC)으로 변환하여 저장합니다.

```java
@Entity
public class Task {

    @Convert(converter = SimpliXLocalDateTimeConverter.class)
    private LocalDateTime dueDate;
}
```

### Auto-Apply (Global)

모든 OffsetDateTime 필드에 자동 적용하려면:

```java
// orm.xml 또는 package-info.java에서 설정
@org.hibernate.annotations.ConverterRegistration(
    converter = SimpliXOffsetDateTimeConverter.class,
    autoApply = true
)
```

## ModelMapper Integration

SimpliXModelMapperAutoConfiguration은 타임존 인식 변환기를 자동 등록합니다.

### Automatic Conversion

```java
// DTO
public class EventDto {
    private LocalDateTime startTime;  // 또는 OffsetDateTime
}

// Entity
public class Event {
    private OffsetDateTime startTime;
}

// 매핑시 자동 타임존 변환
EventDto dto = modelMapper.map(event, EventDto.class);
```

### Registered Converters

1. **LocalDateTime -> OffsetDateTime**: 애플리케이션 타임존 적용
2. **OffsetDateTime -> LocalDateTime**: 애플리케이션 타임존으로 변환 후 추출
3. **OffsetDateTime -> OffsetDateTime**: 애플리케이션 타임존으로 정규화

## Jackson Integration

SimpliX는 Jackson과 통합하여 JSON 직렬화/역직렬화시 타임존을 처리합니다.

```yaml
spring:
  jackson:
    time-zone: Asia/Seoul  # simplix.date-time.default-timezone이 없을 때 사용
    serialization:
      write-dates-as-timestamps: false  # ISO 8601 포맷 사용
```

### API Response

```json
{
  "createdAt": "2024-01-15T14:30:00+09:00",
  "updatedAt": "2024-01-15T15:45:00+09:00"
}
```

## Database Configuration

### PostgreSQL

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.time_zone: UTC
```

```sql
-- timestamptz 컬럼 사용 권장
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    start_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### MySQL

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.time_zone: UTC
```

```sql
-- DATETIME(3) 또는 TIMESTAMP 사용
CREATE TABLE events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    start_time DATETIME(3) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Best Practices

### 1. UTC for Storage

항상 DB에는 UTC로 저장하세요:

```yaml
simplix:
  date-time:
    use-utc-for-database: true

spring:
  jpa:
    properties:
      hibernate:
        jdbc.time_zone: UTC
```

### 2. OffsetDateTime for APIs

API에서는 OffsetDateTime을 사용하여 타임존 정보를 포함하세요:

```java
public class EventResponse {
    private OffsetDateTime startTime;  // ISO 8601 with offset
}
```

### 3. LocalDateTime for UI

사용자 인터페이스용 값은 LocalDateTime으로 변환:

```java
LocalDateTime displayTime = event.getStartTime()
    .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
    .toLocalDateTime();
```

### 4. Consistent Timezone

서비스 전체에서 동일한 타임존 설정 사용:

```yaml
# 모든 환경에서 동일하게 설정
simplix:
  date-time:
    default-timezone: Asia/Seoul  # 또는 UTC
```

## Troubleshooting

### 시간이 9시간 차이나는 경우

원인: 타임존 불일치
해결:
```yaml
simplix:
  date-time:
    default-timezone: Asia/Seoul
    use-utc-for-database: true
```

### DB 저장 후 시간이 변경되는 경우

원인: JPA/JDBC 타임존 불일치
해결:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.time_zone: UTC
```

### ModelMapper 변환 후 시간이 다른 경우

원인: 타임존 정규화 비활성화
해결:
```yaml
simplix:
  date-time:
    normalize-timezone: true
```