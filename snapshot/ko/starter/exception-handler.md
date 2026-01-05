# Exception Handler Guide

## Overview

SimpliX는 전역 예외 처리기 `SimpliXExceptionHandler`를 제공합니다. 이 핸들러는 다양한 예외를 표준화된 API 응답으로 변환하고, Trace ID를 통한 추적을 지원합니다.

## Auto-Configuration

예외 핸들러는 `SimpliXWebAutoConfiguration`에서 자동으로 등록됩니다:

```yaml
simplix:
  exception-handler:
    enabled: true  # 기본값: true
```

## Default Exception Handling

### Supported Exceptions

| Exception | HTTP Status | Error Code | Description |
|-----------|-------------|------------|-------------|
| `SimpliXGeneralException` | 가변 | 커스텀 | SimpliX 커스텀 예외 |
| `MethodArgumentNotValidException` | 400 | VAL_VALIDATION_FAILED | Bean Validation 실패 |
| `AccessDeniedException` | 403 | AUTHZ_INSUFFICIENT_PERMISSIONS | 권한 없음 |
| `BadCredentialsException` | 401 | AUTH_INVALID_CREDENTIALS | 잘못된 인증 정보 |
| `AuthenticationException` | 401 | AUTH_AUTHENTICATION_REQUIRED | 인증 필요 |
| `AsyncRequestTimeoutException` | 408 | GEN_TIMEOUT | 요청 타임아웃 |
| `NoResourceFoundException` | 404 | GEN_NOT_FOUND | 리소스 없음 |
| `Exception` | 500 | GEN_INTERNAL_SERVER_ERROR | 일반 예외 |

### Response Format

기본 응답 형식 (`SimpliXApiResponse`):

```json
{
  "success": false,
  "message": "Validation failed",
  "code": "VAL_VALIDATION_FAILED",
  "data": [
    {
      "field": "email",
      "message": "must be a valid email",
      "rejectedValue": "invalid-email",
      "code": "Email"
    }
  ],
  "timestamp": "2024-01-15T14:30:00+09:00"
}
```

## Custom Response Type

커스텀 응답 타입을 사용하려면 `SimpliXExceptionHandler`를 확장합니다.

### 1. Custom Response Class

```java
public class CustomApiResponse {
    private int code;
    private String message;
    private String detail;
    private OffsetDateTime timestamp;
    private String traceId;

    // constructors, getters, setters...
}
```

### 2. Custom Response Factory

```java
public class CustomResponseFactory
        implements SimpliXExceptionHandler.ResponseFactory<CustomApiResponse> {

    @Override
    public CustomApiResponse createErrorResponse(
            HttpStatus statusCode,
            String errorType,
            String message,
            Object detail,
            String path) {

        CustomApiResponse response = new CustomApiResponse();
        response.setCode(statusCode.value());
        response.setMessage(message);
        response.setDetail(detail != null ? detail.toString() : null);
        response.setTimestamp(OffsetDateTime.now());
        response.setTraceId(MDC.get("traceId"));
        return response;
    }
}
```

### 3. Custom Exception Handler

```java
@RestControllerAdvice
public class CustomExceptionHandler extends SimpliXExceptionHandler<CustomApiResponse> {

    public CustomExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
        super(messageSource, new CustomResponseFactory());
    }

    // 추가 예외 핸들러 정의 가능
    @ExceptionHandler(MyBusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CustomApiResponse handleMyBusinessException(
            MyBusinessException ex,
            HttpServletRequest request) {

        return responseFactory.createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "BUSINESS_ERROR",
            ex.getMessage(),
            ex.getDetails(),
            request.getRequestURI()
        );
    }
}
```

### 4. Disable Default Handler

커스텀 핸들러를 사용할 때 기본 핸들러 비활성화:

```yaml
simplix:
  exception-handler:
    enabled: false
```

또는 빈으로 등록하여 자동 대체:

```java
@Configuration
public class WebConfig {

    @Bean
    public CustomExceptionHandler customExceptionHandler(
            MessageSource messageSource,
            ObjectMapper objectMapper) {
        return new CustomExceptionHandler(messageSource, objectMapper);
    }
}
```

## Trace ID Support

SimpliX 예외 핸들러는 MDC의 `traceId`를 응답 헤더에 추가합니다.

### Request Header

```
X-Request-Id: abc123
```

### Response Header

```
X-Trace-Id: abc123
```

### Log Output

```
2024-01-15 14:30:00 ERROR [traceId=abc123] SimpliXGeneralException -
    ErrorCode: VAL_VALIDATION_FAILED, Path: /api/users
```

### Custom Trace ID Filter

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        MDC.put("traceId", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

## Validation Error Details

Bean Validation 실패시 상세 필드 에러 정보를 제공합니다.

### Validation Response

```json
{
  "success": false,
  "message": "Validation failed",
  "code": "VAL_VALIDATION_FAILED",
  "data": [
    {
      "field": "username",
      "message": "must not be blank",
      "rejectedValue": null,
      "code": "NotBlank"
    },
    {
      "field": "email",
      "message": "must be a valid email address",
      "rejectedValue": "invalid",
      "code": "Email"
    },
    {
      "field": "age",
      "message": "must be greater than or equal to 18",
      "rejectedValue": 15,
      "code": "Min"
    }
  ]
}
```

### Message Placeholder Substitution

SimpliX는 유효성 검사 메시지의 플레이스홀더를 자동으로 치환합니다:

```java
// DTO
public class UserDto {
    @Size(min = 2, max = 50, message = "Username must be between {min} and {max} characters")
    private String username;

    @Min(value = 18, message = "Age must be at least {value}")
    private int age;
}
```

결과:
- `"Username must be between 2 and 50 characters"`
- `"Age must be at least 18"`

### ValidationArgumentProcessor

검증 메시지의 인수를 처리하는 유틸리티 클래스입니다.

#### 기본 기능

| 어노테이션 | 처리 방식 |
|-----------|----------|
| `@Size` | min, max 순서 정렬 |
| `@Length` | min, max 순서 정렬 |
| `@Min` | value 추출 |
| `@Max` | value 추출 |
| `@Range` | 숫자 인수 처리 |

#### 커스텀 프로세서 등록

특정 메시지 패턴에 대한 커스텀 인수 처리:

```java
// 애플리케이션 시작 시 등록
ValidationArgumentProcessor.registerProcessor("custom.message.key", args -> {
    // args 배열을 원하는 형태로 변환
    return new Object[] { args[1], args[0] };  // 순서 변경
});
```

#### 제약조건 속성 추출

```java
// Constraint 어노테이션에서 속성 추출
Object[] args = ValidationArgumentProcessor.extractConstraintAttributes(
    sizeAnnotation,
    "min", "max"
);
// args = [2, 50]
```

#### 인수 재정렬

min/max 인수가 잘못된 순서일 때 자동 정렬:

```java
// min=50, max=2 형태의 인수가 들어오면
// min=2, max=50 으로 자동 정렬
```

## i18n Error Messages

에러 메시지는 MessageSource를 통해 국제화됩니다.

### messages.properties

```properties
# English (default)
error.val.validation.failed=Validation failed
error.auth.authentication.required=Authentication required
error.authz.insufficient.permissions=Access denied
error.gen.not.found=Resource not found
error.gen.internal.server.error=Internal server error
```

### messages_ko.properties

```properties
# Korean
error.val.validation.failed=유효성 검사 실패
error.auth.authentication.required=인증이 필요합니다
error.authz.insufficient.permissions=접근 권한이 없습니다
error.gen.not.found=리소스를 찾을 수 없습니다
error.gen.internal.server.error=서버 내부 오류
```

## Debug Mode

개발 환경에서는 상세 에러 정보가 포함됩니다.

### Enable Debug Mode

```yaml
spring:
  profiles:
    active: dev  # 또는 local, debug
```

### Debug Response

```json
{
  "success": false,
  "message": "Internal server error",
  "code": "GEN_INTERNAL_SERVER_ERROR",
  "data": "java.lang.NullPointerException: Cannot invoke method on null",
  "timestamp": "2024-01-15T14:30:00+09:00"
}
```

운영 환경에서는:

```json
{
  "success": false,
  "message": "Internal server error",
  "code": "GEN_INTERNAL_SERVER_ERROR",
  "data": "An error occurred while processing your request",
  "timestamp": "2024-01-15T14:30:00+09:00"
}
```

## Best Practices

### 1. Use SimpliXGeneralException

비즈니스 예외는 `SimpliXGeneralException`을 사용하세요:

```java
throw new SimpliXGeneralException(
    ErrorCode.GEN_NOT_FOUND,
    "User not found",
    Map.of("userId", id)
);
```

### 2. Custom Error Codes

프로젝트별 에러 코드는 ErrorCode enum을 확장하세요:

```java
// simplix-core의 ErrorCode 사용
throw new SimpliXGeneralException(
    ErrorCode.VAL_INVALID_PARAMETER,
    "Invalid parameter",
    null,
    "Parameter 'name' cannot be empty"
);
```

### 3. Log Appropriately

- 4xx 에러: WARN 레벨
- 5xx 에러: ERROR 레벨
- 404 에러: DEBUG 레벨 (일반적인 상황)

### 4. Include Trace ID

모든 에러 응답에 Trace ID를 포함하여 디버깅을 용이하게 하세요.