# SimpliX Starter Overview

## Introduction

`spring-boot-starter-simplix`는 SimpliX 프레임워크의 메인 Spring Boot Starter입니다. "Umbrella Starter" 패턴을 사용하여 모든 SimpliX 모듈을 하나의 의존성으로 통합하고, Spring Boot 애플리케이션에 필요한 자동 구성을 제공합니다.

## Architecture

### Umbrella Starter Pattern

```
spring-boot-starter-simplix (umbrella)
    |
    +-- simplix-core ----------- Base utilities, exceptions, API response
    |
    +-- simplix-auth ----------- JWT/JWE tokens, Spring Security integration
    |
    +-- simplix-cache ---------- Distributed caching, Redis/Caffeine support
    |
    +-- simplix-encryption ----- Data encryption, JPA converters
    |
    +-- simplix-event ---------- NATS-based event system
    |
    +-- simplix-excel ---------- Excel/CSV import/export
    |
    +-- simplix-file ----------- File storage (local, S3, GCS)
    |
    +-- simplix-email ---------- Email sending, template support
    |
    +-- simplix-hibernate ------ Hibernate L2 cache integration
    |
    +-- simplix-mybatis -------- MyBatis integration, type handlers
```

### Auto-Configuration Flow

SimpliX의 Auto-Configuration은 Spring Boot 표준을 따르며, 특정 순서로 실행됩니다:

```
1. SimpliXAutoConfiguration (Order: 0)
   +-- Main config, component scan

2. SimpliXMessageSourceAutoConfiguration (before: MessageSourceAutoConfiguration)
   +-- i18n message source integration

3. SimpliXValidatorAutoConfiguration (after: MessageSourceAutoConfiguration)
   +-- Bean Validation message setup

4. SimpliXDateTimeAutoConfiguration
   +-- Timezone management, JVM default timezone setup

5. SimpliXJpaAutoConfiguration (after: HibernateJpaAutoConfiguration)
   +-- JPA DateTime converter registration

6. SimpliXModelMapperAutoConfiguration
   +-- ModelMapper timezone-aware setup

7. SimpliXWebAutoConfiguration (after: WebMvcAutoConfiguration)
   +-- Web exception handling, Swagger schema enhancement

8. SimpliXSwaggerAutoConfiguration
   +-- OpenAPI/Swagger config, Scalar UI

9. SimpliXThymeleafAutoConfiguration (before: ErrorMvcAutoConfiguration)
   +-- Thymeleaf template resolver

10. SimpliXI18nAutoConfiguration
    +-- I18n translation configuration, locale fallback setup
```

## Package Structure

```
dev.simplecore.simplix
├── springboot
│   ├── autoconfigure/           # Auto-Configuration 클래스
│   │   ├── SimpliXAutoConfiguration.java
│   │   ├── SimpliXDateTimeAutoConfiguration.java
│   │   ├── SimpliXI18nAutoConfiguration.java
│   │   ├── SimpliXJpaAutoConfiguration.java
│   │   ├── SimpliXMessageSourceAutoConfiguration.java
│   │   ├── SimpliXModelMapperAutoConfiguration.java
│   │   ├── SimpliXSwaggerAutoConfiguration.java
│   │   ├── SimpliXThymeleafAutoConfiguration.java
│   │   ├── SimpliXValidatorAutoConfiguration.java
│   │   └── SimpliXWebAutoConfiguration.java
│   │
│   ├── converter/               # JPA AttributeConverter
│   │   ├── SimpliXLocalDateTimeConverter.java
│   │   └── SimpliXOffsetDateTimeConverter.java
│   │
│   ├── properties/              # Configuration Properties
│   │   └── SimpliXProperties.java
│   │
│   └── application/             # 애플리케이션 유틸리티
│       └── ApplicationInfoRunner.java
│
└── web
    ├── advice/                  # 예외 처리
    │   ├── SimpliXExceptionHandler.java
    │   ├── SimpliXResponseBodyAdvice.java
    │   └── ValidationFieldError.java
    │
    ├── config/                  # 웹 설정
    │   ├── SimpliXWebConfig.java
    │   ├── SwaggerSchemaEnhancer.java
    │   └── EnumSchemaExtractor.java
    │
    ├── controller/              # 기본 컨트롤러
    │   ├── SimpliXBaseController.java
    │   └── SimpliXStandardApi.java
    │
    ├── service/                 # 기본 서비스
    │   ├── SimpliXBaseService.java
    │   └── SimpliXService.java
    │
    └── exception/               # 에러 처리
        └── SimpliXErrorController.java
```

## Key Components

### SimpliXProperties

모든 SimpliX 설정을 관리하는 중앙 Properties 클래스:

```java
@ConfigurationProperties(prefix = "simplix")
public class SimpliXProperties {
    private CoreProperties core = new CoreProperties();
    private ExceptionHandlerProperties exceptionHandler = new ExceptionHandlerProperties();
    private DateTimeProperties dateTime = new DateTimeProperties();
}
```

### SimpliXTimezoneService

타임존 관련 모든 작업을 처리하는 중앙 서비스:

```java
@Bean
public SimpliXTimezoneService timezoneService() {
    return new SimpliXTimezoneService(
        resolveApplicationZoneId(),
        properties.getDateTime().isUseUtcForDatabase(),
        properties.getDateTime().isNormalizeTimezone()
    );
}
```

### SimpliXBaseService

CRUD 작업을 위한 기본 서비스 추상 클래스:

- `findById(ID id)` - ID로 엔티티 조회
- `findById(ID id, Class<P> projection)` - ID로 조회 후 프로젝션 매핑
- `findAll(Pageable pageable)` - 페이징된 전체 조회
- `findAllWithSearch(SearchCondition)` - 동적 검색 조건 조회
- `save(E entity)` - 엔티티 저장
- `deleteById(ID id)` - ID로 삭제

### SimpliXExceptionHandler

전역 예외 처리기:

- SimpliXGeneralException 처리
- 유효성 검사 예외 (MethodArgumentNotValidException)
- Spring Security 예외 (AccessDeniedException, AuthenticationException)
- 일반 예외 래핑 및 로깅
- Trace ID 헤더 추가

## Conditional Registration

각 Auto-Configuration은 조건부로 등록됩니다:

| Configuration | Condition | Default |
|---------------|-----------|---------|
| SimpliXAutoConfiguration | Always | Enabled |
| SimpliXMessageSourceAutoConfiguration | `simplix.message-source.enabled` | true |
| SimpliXI18nAutoConfiguration | Always | Enabled |
| SimpliXDateTimeAutoConfiguration | `simplix.core.enabled` | true |
| SimpliXJpaAutoConfiguration | `@ConditionalOnClass(EntityManagerFactory)` | Auto |
| SimpliXModelMapperAutoConfiguration | `@ConditionalOnClass(ModelMapper)` | Auto |
| SimpliXSwaggerAutoConfiguration | `@ConditionalOnClass(OpenAPI)` | Auto |
| SimpliXThymeleafAutoConfiguration | `@ConditionalOnClass(SpringTemplateEngine)` | Auto |
| SimpliXWebAutoConfiguration | `@ConditionalOnWebApplication` | Auto |

## Related Documentation

- [Configuration Guide](configuration.md) - 상세 설정 가이드
- [DateTime Guide](datetime.md) - 타임존/날짜시간 관리
- [Exception Handler Guide](exception-handler.md) - 예외 처리 커스터마이징
- [Service & Controller Guide](service-controller.md) - 서비스/컨트롤러 확장
- [Swagger Guide](swagger.md) - API 문서화 설정