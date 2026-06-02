# SimpliX eGov Module

전자정부 표준프레임워크(eGovFrame) 5.0.0 통합 모듈입니다. `EgovAbstractServiceImpl`이 사용하는 핵심 빈을 자동 등록하여 Spring Boot 애플리케이션에서 eGovFrame 서비스를 즉시 활용할 수 있게 합니다.

## Features

- ✔ **eGovFrame 5.0.0 통합** - `EgovAbstractServiceImpl` 즉시 사용 가능
- ✔ **TraceHandler 자동 등록** - `leaveaTrace()` 호출을 SLF4J 로그로 출력
- ✔ **LeaveaTrace 빈 자동 구성** - `@Resource`로 주입되는 필수 빈 제공
- ✔ **조건부 자동 활성화** - eGovFrame 클래스가 클래스패스에 있을 때만 활성화
- ✔ **커스터마이징 지원** - 사용자 `TraceHandler` 빈으로 손쉬운 교체
- ✔ **Log4j 충돌 회피** - 충돌 가능한 log4j 모듈 자동 제외

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-egov'

    // eGovFrame 라이브러리는 simplix-egov가 api 의존성으로 함께 제공
}
```

### 2. Configuration

```yaml
simplix:
  egov:
    enabled: true   # 기본값 true
```

### 3. Usage

eGovFrame 표준 서비스 클래스를 그대로 활용합니다.

```java
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserService extends EgovAbstractServiceImpl {

    public void registerUser(UserDto dto) {
        // 비즈니스 로직 수행
        leaveaTrace("UserService.registerUser invoked: " + dto.getId());
    }
}
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `simplix.egov.enabled` | `true` | 모듈 활성화 여부 |

## Architecture

```
simplix-egov/
+-- autoconfigure/
|   +-- SimpliXEgovAutoConfiguration   # Auto-configuration entry point
+-- config/
    +-- SimpliXEgovConfiguration       # Bean definitions (LeaveaTrace, TraceHandlerService)
    +-- SimpliXEgovProperties          # Configuration properties (simplix.egov.*)
    +-- SimpliXTraceHandler            # Default SLF4J-based TraceHandler
```

## Provided Beans

| Bean | Type | Description |
|------|------|-------------|
| `simplixTraceHandler` | `SimpliXTraceHandler` | SLF4J 기반 기본 `TraceHandler` |
| `traceHandlerService` | `DefaultTraceHandleManager` | 모든 클래스에 매칭(`*`)되는 핸들 관리자 |
| `leaveaTrace` | `LeaveaTrace` | `@Resource`로 주입되는 표준 빈 |

## Customizing TraceHandler

기본 `SimpliXTraceHandler`를 다른 구현체로 교체하려면 동일한 타입의 빈을 직접 등록합니다.

```java
@Configuration
public class CustomEgovConfig {

    @Bean
    public SimpliXTraceHandler customTraceHandler() {
        return new SimpliXTraceHandler() {
            @Override
            public void todo(Class<?> clazz, String message) {
                // Forward to monitoring system
            }
        };
    }
}
```

## Documentation

| Document | Description |
|----------|-------------|
| [Overview](ko/egov/overview.md) | 상세 아키텍처 및 동작 원리 |

## Requirements

- Java 17+
- Spring Boot 3.5+
- eGovFrame 5.0.0 (모듈에 포함)

## License

SimpleCORE License 1.0 (SCL-1.0)
