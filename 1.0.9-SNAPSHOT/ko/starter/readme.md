# Spring Boot Starter SimpliX

SimpliX Framework의 메인 Spring Boot Starter입니다. 모든 SimpliX 모듈을 하나의 의존성으로 통합하고, Spring Boot 애플리케이션에 자동 구성을 제공합니다.

## Features

- ✔ **Umbrella Starter** - 모든 SimpliX 모듈 (auth, cache, encryption, event, excel, file, email, hibernate, mybatis) 통합
- ✔ **Auto-Configuration** - Spring Boot 자동 구성 (MessageSource, JPA, DateTime, ModelMapper, Swagger, Thymeleaf 등)
- ✔ **Timezone Management** - 애플리케이션 타임존 자동 관리 및 UTC 데이터베이스 저장
- ✔ **Exception Handling** - 전역 예외 처리 및 표준화된 API 응답
- ✔ **Base Service/Controller** - CRUD 작업을 위한 기본 서비스/컨트롤러 추상 클래스
- ✔ **Swagger/OpenAPI** - API 문서 자동 생성 (Swagger UI + Scalar UI)
- ✔ **i18n Support** - 라이브러리와 애플리케이션 메시지 소스 통합
- ✔ **ModelMapper Integration** - 타임존 인식 DTO 매핑

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:spring-boot-starter-simplix:${version}'
}
```

이 하나의 의존성으로 다음 모듈들이 모두 포함됩니다:
- simplix-core (기본 유틸리티)
- simplix-auth (인증/인가)
- simplix-cache (캐싱)
- simplix-encryption (암호화)
- simplix-event (이벤트/NATS)
- simplix-excel (Excel/CSV)
- simplix-file (파일 스토리지)
- simplix-email (이메일)
- simplix-hibernate (Hibernate L2 캐시)
- simplix-mybatis (MyBatis 통합)

### 2. Minimal Configuration

```yaml
# application.yml
simplix:
  core:
    enabled: true  # SimpliX 활성화 (기본값: true)
```

### 3. Base Service 사용

```java
@Service
public class UserService extends SimpliXBaseService<User, Long> {

    public UserService(UserRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }

    // 기본 CRUD는 이미 구현됨
    // findById, findAll, save, delete 등
}
```

### 4. Base Controller 사용

```java
@RestController
@RequestMapping("/api/users")
public class UserController extends SimpliXBaseController<User, Long> {

    public UserController(UserService service) {
        super(service);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<User>> getUser(@PathVariable Long id) {
        return service.findById(id)
            .map(user -> ResponseEntity.ok(SimpliXApiResponse.success(user)))
            .orElseThrow(() -> new SimpliXGeneralException(ErrorCode.GEN_NOT_FOUND));
    }
}
```

## Auto-Configuration Overview

| Configuration | Description | Condition |
|---------------|-------------|-----------|
| `SimpliXAutoConfiguration` | 메인 자동 구성, 컴포넌트 스캔 | Always |
| `SimpliXMessageSourceAutoConfiguration` | i18n 메시지 소스 통합 | `simplix.message-source.enabled=true` |
| `SimpliXDateTimeAutoConfiguration` | 타임존 관리 | `simplix.core.enabled=true` |
| `SimpliXJpaAutoConfiguration` | JPA DateTime 컨버터 | JPA 존재시 |
| `SimpliXModelMapperAutoConfiguration` | ModelMapper 설정 | ModelMapper 존재시 |
| `SimpliXSwaggerAutoConfiguration` | Swagger/OpenAPI 설정 | SpringDoc 존재시 |
| `SimpliXThymeleafAutoConfiguration` | Thymeleaf 템플릿 | Thymeleaf 존재시 |
| `SimpliXWebAutoConfiguration` | 웹 예외 처리 | 웹 애플리케이션시 |
| `SimpliXValidatorAutoConfiguration` | Bean Validation | MessageSource 이후 |
| `SimpliXSecurityAutoConfiguration` | Spring Security | Security 존재시 |
| `SimpliXI18nAutoConfiguration` | I18n 번역 설정 | Always |

## Configuration Properties

```yaml
simplix:
  core:
    enabled: true                    # SimpliX 전체 활성화

  date-time:
    default-timezone: Asia/Seoul     # 애플리케이션 기본 타임존
    use-utc-for-database: true       # DB 저장시 UTC 변환
    normalize-timezone: true         # 타임존 없는 값 정규화

  message-source:
    enabled: true                    # 메시지 소스 통합 활성화

  i18n:
    default-locale: en               # 기본 로케일 (기본값: en)
    supported-locales:               # 지원 로케일 목록
      - en
      - ko
      - ja

  exception-handler:
    enabled: true                    # 전역 예외 핸들러 활성화

  swagger:
    i18n-enabled: true               # Swagger 스키마 i18n
    customizers:
      enum-extractor:
        enabled: true                # Enum 스키마 추출
      nested-object-extractor:
        enabled: true                # 중첩 객체 스키마 추출

# Spring Boot 표준 설정과 통합
spring:
  jackson:
    time-zone: Asia/Seoul            # Jackson 타임존 (우선순위 2)

  messages:
    basename: messages,messages/validation,messages/errors
    encoding: UTF-8

  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false

springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
```

## Exception Handling

SimpliX는 전역 예외 처리를 자동으로 설정합니다:

```java
// 기본 예외 처리기가 자동 등록됩니다
// 커스텀 응답 타입이 필요한 경우:

@RestControllerAdvice
public class CustomExceptionHandler extends SimpliXExceptionHandler<CustomApiResponse> {

    public CustomExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        super(messageSource, new CustomResponseFactory());
    }
}
```

### 지원되는 예외 타입

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `SimpliXGeneralException` | 가변 | 커스텀 |
| `MethodArgumentNotValidException` | 400 | VAL_VALIDATION_FAILED |
| `AccessDeniedException` | 403 | AUTHZ_INSUFFICIENT_PERMISSIONS |
| `BadCredentialsException` | 401 | AUTH_INVALID_CREDENTIALS |
| `AuthenticationException` | 401 | AUTH_AUTHENTICATION_REQUIRED |
| `NoResourceFoundException` | 404 | GEN_NOT_FOUND |

## Timezone Management

SimpliX는 타임존을 중앙에서 관리합니다:

```java
// 타임존 서비스 주입
@Autowired
private SimpliXTimezoneService timezoneService;

// LocalDateTime -> OffsetDateTime (애플리케이션 타임존)
OffsetDateTime odt = timezoneService.normalizeToApplicationTimezone(localDateTime);

// DB 저장용 UTC 변환
OffsetDateTime utc = timezoneService.normalizeForDatabase(odt);

// DB에서 읽어온 값을 애플리케이션 타임존으로
OffsetDateTime app = timezoneService.normalizeFromDatabase(utc);
```

### 타임존 우선순위

1. `simplix.date-time.default-timezone`
2. `spring.jackson.time-zone`
3. `user.timezone` 시스템 속성
4. 시스템 기본 타임존

## Swagger/OpenAPI Endpoints

SimpliX는 API 문서화를 위한 엔드포인트를 자동으로 설정합니다:

- `/v3/api-docs` - OpenAPI JSON specification
- `/swagger-ui.html` - Swagger UI
- `/scalar` - Scalar UI (현대적 대안)

## Module Architecture

```
spring-boot-starter-simplix (umbrella)
    │
    ├── simplix-core (기반 유틸리티)
    ├── simplix-auth (인증/JWT/JWE)
    ├── simplix-cache (캐싱)
    ├── simplix-encryption (암호화)
    ├── simplix-event (NATS 이벤트)
    ├── simplix-excel (Excel/CSV)
    ├── simplix-file (파일 스토리지)
    ├── simplix-email (이메일)
    ├── simplix-hibernate (L2 캐시)
    └── simplix-mybatis (MyBatis)
```

## Documentation

- [Application Setup Guide (애플리케이션 설정)](ko/starter/application-setup.md)
- [Overview (아키텍처 상세)](ko/starter/overview.md)
- [Configuration Guide (YAML 설정)](ko/starter/configuration.md)
- [DateTime Guide (타임존/날짜시간)](ko/starter/datetime.md)
- [Exception Handler Guide (예외 처리)](ko/starter/exception-handler.md)
- [Service & Controller Guide (서비스/컨트롤러)](ko/starter/service-controller.md)
- [Swagger Guide (API 문서)](ko/starter/swagger.md)

## Requirements

- Java 17+
- Spring Boot 3.5.x
- Spring Data JPA

## License

SimpleCORE License 1.0 (SCL-1.0)