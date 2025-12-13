# SimpliX Core Module

SimpliX 프레임워크의 핵심 모듈입니다. 다른 모든 SimpliX 모듈의 기반이 됩니다.

## Features

- ✔ **베이스 엔티티/리포지토리** - SimpliXBaseEntity, SimpliXBaseRepository
- ✔ **계층 구조(트리) 지원** - TreeEntity, SimpliXTreeService (40+ 메서드)
- ✔ **타입 변환 시스템** - Boolean, Enum, DateTime 변환기
- ✔ **XSS/SQL Injection 방지** - OWASP 기반 HtmlSanitizer, SqlInjectionValidator
- ✔ **민감 데이터 마스킹** - DataMaskingUtils, LogMasker, IpAddressMaskingUtils
- ✔ **표준화된 예외 처리** - ErrorCode (30+ 에러 코드), SimpliXGeneralException
- ✔ **표준화된 API 응답** - SimpliXApiResponse (SUCCESS/FAILURE/ERROR)
- ✔ **캐시 추상화** - SPI 기반 CacheManager

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-core:${version}'
}
```

### 2. Base Entity

```java
@Entity
@Table(name = "users")
public class User extends SimpliXBaseEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;

    @Override
    public Long getId() { return id; }

    @Override
    public void setId(Long id) { this.id = id; }
}
```

### 3. Base Repository

```java
public interface UserRepository extends SimpliXBaseRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
```

### 4. API Response

```java
@GetMapping("/{id}")
public ResponseEntity<SimpliXApiResponse<User>> getUser(@PathVariable Long id) {
    User user = userService.findById(id);
    return ResponseEntity.ok(SimpliXApiResponse.success(user));
}
```

### 5. Exception Handling

```java
throw new SimpliXGeneralException(
    ErrorCode.GEN_NOT_FOUND,
    "User not found",
    Map.of("userId", id)
);
```

## Module Architecture

```
simplix-core (base)
    │
    ├── spring-boot-starter-simplix
    ├── spring-boot-starter-simplix-auth
    ├── spring-boot-starter-simplix-event
    ├── spring-boot-starter-simplix-excel
    └── spring-boot-starter-simplix-mybatis
```

## Library Module

simplix-core는 Auto-Configuration이 없는 순수 라이브러리 모듈입니다:

- Spring Boot starter가 아닌 일반 라이브러리
- 의존성 추가만으로 클래스 사용 가능
- 다른 모듈에서 공통 기능 제공

## Documentation

- [Overview (아키텍처 상세)](docs/ko/overview.md)
- [Entity & Repository Guide (엔티티/리포지토리)](docs/ko/entity-repository.md)
- [Tree Structure Guide (트리 구조)](docs/ko/tree-structure.md)
- [Type Converters Guide (타입 변환)](docs/ko/type-converters.md)
- [Security Guide (보안)](docs/ko/security.md)
- [Exception & API Guide (예외/API)](docs/ko/exception-api.md)
- [Cache Guide (캐시)](docs/ko/cache.md)

## Requirements

- Java 17+
- Spring Boot 3.x
- Spring Data JPA

## License

SimpleCORE License 1.0 (SCL-1.0)
