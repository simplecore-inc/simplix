# Exception & API Guide

## Error Code

### ErrorCode Enum

SimpliX 프레임워크의 표준화된 에러 코드입니다.

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 코드, 기본 메시지, HTTP 상태, 카테고리
    GEN_BAD_REQUEST("GEN_BAD_REQUEST", "Bad request", HttpStatus.BAD_REQUEST, ErrorCategory.GENERAL),
    // ...
}
```

### 에러 카테고리

| 접두사 | 카테고리 | HTTP Status | 설명 |
|--------|----------|-------------|------|
| `GEN_` | GENERAL | 400/404/500 | 일반 에러 |
| `AUTH_` | AUTHENTICATION | 401 | 인증 에러 |
| `AUTHZ_` | AUTHORIZATION | 403 | 권한 에러 |
| `VAL_` | VALIDATION | 400 | 검증 에러 |
| `SEARCH_` | SEARCH | 400 | 검색/쿼리 에러 |
| `BIZ_` | BUSINESS | 409/422 | 비즈니스 로직 에러 |
| `DB_` | DATABASE | 500 | 데이터베이스 에러 |
| `EXT_` | EXTERNAL | 502/503 | 외부 서비스 에러 |

### 전체 에러 코드 목록

#### General (GEN_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `GEN_INTERNAL_SERVER_ERROR` | Internal server error | 500 |
| `GEN_BAD_REQUEST` | Bad request | 400 |
| `GEN_NOT_FOUND` | Resource not found | 404 |
| `GEN_METHOD_NOT_ALLOWED` | Method not allowed | 405 |
| `GEN_CONFLICT` | Conflict | 409 |
| `GEN_SERVICE_UNAVAILABLE` | Service unavailable | 503 |
| `GEN_TIMEOUT` | Request timeout | 408 |

#### Authentication (AUTH_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `AUTH_AUTHENTICATION_REQUIRED` | Authentication required | 401 |
| `AUTH_INVALID_CREDENTIALS` | Invalid credentials | 401 |
| `AUTH_TOKEN_EXPIRED` | Token expired | 401 |
| `AUTH_TOKEN_INVALID` | Invalid token | 401 |
| `AUTH_SESSION_EXPIRED` | Session expired | 401 |

#### Authorization (AUTHZ_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `AUTHZ_INSUFFICIENT_PERMISSIONS` | Insufficient permissions | 403 |
| `AUTHZ_ACCESS_DENIED` | Access denied | 403 |
| `AUTHZ_RESOURCE_FORBIDDEN` | Resource access forbidden | 403 |

#### Validation (VAL_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `VAL_VALIDATION_FAILED` | Validation failed | 400 |
| `VAL_INVALID_PARAMETER` | Invalid parameter | 400 |
| `VAL_MISSING_PARAMETER` | Missing required parameter | 400 |
| `VAL_INVALID_FORMAT` | Invalid format | 400 |
| `VAL_CONSTRAINT_VIOLATION` | Constraint violation | 400 |

#### Search (SEARCH_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `SEARCH_INVALID_PARAMETER` | Invalid search parameter | 400 |
| `SEARCH_INVALID_SORT_FIELD` | Invalid sort field | 400 |
| `SEARCH_INVALID_FILTER_OPERATOR` | Invalid filter operator | 400 |
| `SEARCH_INVALID_QUERY_SYNTAX` | Invalid query syntax | 400 |

#### Business (BIZ_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `BIZ_BUSINESS_LOGIC_ERROR` | Business logic error | 422 |
| `BIZ_DUPLICATE_RESOURCE` | Duplicate resource | 409 |
| `BIZ_RESOURCE_LOCKED` | Resource is locked | 423 |
| `BIZ_INVALID_STATE` | Invalid state transition | 422 |
| `BIZ_QUOTA_EXCEEDED` | Quota exceeded | 429 |

#### Database (DB_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `DB_DATABASE_ERROR` | Database error | 500 |
| `DB_TRANSACTION_FAILED` | Transaction failed | 500 |
| `DB_CONNECTION_ERROR` | Database connection error | 500 |
| `DB_DEADLOCK_DETECTED` | Database deadlock detected | 409 |

#### External (EXT_)

| 코드 | 메시지 | HTTP Status |
|------|--------|-------------|
| `EXT_SERVICE_ERROR` | External service error | 502 |
| `EXT_SERVICE_UNAVAILABLE` | External service unavailable | 503 |
| `EXT_SERVICE_TIMEOUT` | External service timeout | 504 |

### 유틸리티 메서드

```java
// 코드 문자열로 ErrorCode 찾기
ErrorCode code = ErrorCode.fromCode("AUTH_TOKEN_EXPIRED");

// 카테고리 확인
boolean isAuth = code.isAuthenticationError();   // true
boolean isAuthz = code.isAuthorizationError();   // false

// HTTP 상태 유형 확인
boolean isClient = code.isClientError();  // 4xx
boolean isServer = code.isServerError();  // 5xx
```

---

## SimpliXGeneralException

표준 예외 클래스입니다.

### 클래스 구조

```java
@Getter
public class SimpliXGeneralException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus statusCode;
    private Object detail;
    private String path;
}
```

### 생성자

```java
// 기본 생성자
public SimpliXGeneralException(ErrorCode errorCode, String message, Object detail)

// Cause 포함
public SimpliXGeneralException(ErrorCode errorCode, String message, Throwable cause, Object detail)
```

### 사용 예제

```java
// 기본 사용
throw new SimpliXGeneralException(
    ErrorCode.GEN_NOT_FOUND,
    "User not found",
    Map.of("userId", userId)
);

// 인증 에러
throw new SimpliXGeneralException(
    ErrorCode.AUTH_TOKEN_EXPIRED,
    "JWT token has expired",
    null
);

// 비즈니스 에러
throw new SimpliXGeneralException(
    ErrorCode.BIZ_DUPLICATE_RESOURCE,
    "Email already registered",
    Map.of("email", email)
);

// 검증 에러 (상세 정보 포함)
Map<String, String> violations = Map.of(
    "username", "must not be blank",
    "email", "invalid email format"
);
throw new SimpliXGeneralException(
    ErrorCode.VAL_VALIDATION_FAILED,
    "Validation failed",
    violations
);

// 외부 서비스 에러 (cause 포함)
try {
    externalApiClient.call();
} catch (Exception e) {
    throw new SimpliXGeneralException(
        ErrorCode.EXT_SERVICE_ERROR,
        "Failed to call payment API",
        e,
        Map.of("service", "payment-api")
    );
}
```

### 서비스에서 사용

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new SimpliXGeneralException(
                ErrorCode.GEN_NOT_FOUND,
                "User not found with id: " + id,
                Map.of("userId", id)
            ));
    }

    public User createUser(UserCreateDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new SimpliXGeneralException(
                ErrorCode.BIZ_DUPLICATE_RESOURCE,
                "Email already exists",
                Map.of("email", dto.getEmail())
            );
        }
        return userRepository.save(dto.toEntity());
    }
}
```

---

## SimpliXApiResponse

표준 API 응답 래퍼입니다.

### ResponseType

| 타입 | 설명 | 사용 상황 |
|------|------|----------|
| `SUCCESS` | 성공 | 정상 처리 완료 |
| `FAILURE` | 실패 | 비즈니스 로직 실패 (에러 아님) |
| `ERROR` | 에러 | 시스템 에러, 예외 |

### 클래스 구조

```java
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpliXApiResponse<T> {
    private String type;          // SUCCESS, FAILURE, ERROR
    private String message;       // 응답 메시지
    private T body;               // 응답 데이터
    private OffsetDateTime timestamp;  // 응답 시간 (타임존 포함)
    private String errorCode;     // 에러 코드 (ERROR일 때만)
    private Object errorDetail;   // 에러 상세 (ERROR일 때만)
}
```

### 팩토리 메서드

```java
// 성공 응답
SimpliXApiResponse.success(body)
SimpliXApiResponse.success(body, message)

// 실패 응답 (비즈니스 로직 실패)
SimpliXApiResponse.failure(message)
SimpliXApiResponse.failure(data, message)

// 에러 응답 (시스템 에러)
SimpliXApiResponse.error(message)
SimpliXApiResponse.error(message, errorCode)
SimpliXApiResponse.error(message, errorCode, errorDetail)
```

### 사용 예제

**컨트롤러:**
```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<UserDto>> getUser(@PathVariable Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok(SimpliXApiResponse.success(UserDto.from(user)));
    }

    @PostMapping
    public ResponseEntity<SimpliXApiResponse<UserDto>> createUser(
            @Valid @RequestBody UserCreateDto dto) {
        User user = userService.createUser(dto);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(SimpliXApiResponse.success(UserDto.from(user), "User created"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SimpliXApiResponse<Void>> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        if (deleted) {
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "User deleted"));
        }
        return ResponseEntity.ok(SimpliXApiResponse.failure("User not found"));
    }
}
```

### JSON 응답 예시

**SUCCESS:**
```json
{
  "type": "SUCCESS",
  "body": {
    "id": 1,
    "username": "john",
    "email": "john@example.com"
  },
  "timestamp": "2024-12-15T10:30:00.000+09:00"
}
```

**FAILURE:**
```json
{
  "type": "FAILURE",
  "message": "User not found",
  "timestamp": "2024-12-15T10:30:00.000+09:00"
}
```

**ERROR:**
```json
{
  "type": "ERROR",
  "message": "Validation failed",
  "errorCode": "VAL_VALIDATION_FAILED",
  "errorDetail": {
    "username": "must not be blank",
    "email": "invalid email format"
  },
  "timestamp": "2024-12-15T10:30:00.000+09:00"
}
```

### 글로벌 예외 핸들러

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SimpliXGeneralException.class)
    public ResponseEntity<SimpliXApiResponse<Void>> handleSimplixException(
            SimpliXGeneralException ex) {
        return ResponseEntity
            .status(ex.getStatusCode())
            .body(SimpliXApiResponse.error(
                ex.getMessage(),
                ex.getErrorCode().getCode(),
                ex.getDetail()
            ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<SimpliXApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                FieldError::getDefaultMessage
            ));

        return ResponseEntity
            .badRequest()
            .body(SimpliXApiResponse.error(
                "Validation failed",
                ErrorCode.VAL_VALIDATION_FAILED.getCode(),
                errors
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SimpliXApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(SimpliXApiResponse.error(
                "Internal server error",
                ErrorCode.GEN_INTERNAL_SERVER_ERROR.getCode()
            ));
    }
}
```

---

## Related Documents

- [Overview (아키텍처 개요)](./overview.md) - 모듈 구조
- [Entity & Repository Guide (엔티티/리포지토리)](./entity-repository.md) - 베이스 엔티티, 복합 키
- [Tree Structure Guide (트리 구조)](./tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](./type-converters.md) - Boolean, Enum, DateTime 변환
- [Security Guide (보안)](./security.md) - XSS 방지, 해싱, 마스킹
- [Cache Guide (캐시)](./cache.md) - CacheManager, CacheProvider
