# SimpliX Scheduler Module

Spring `@Scheduled` 메서드의 실행을 자동으로 추적하고 로깅하는 AOP 기반 모니터링 모듈입니다.

## Features

- ✔ **AOP 자동 추적** - `@Scheduled` 메서드를 자동 인터셉트하여 실행 정보 기록
- ✔ **전략 패턴** - In-Memory / Database 저장 전략 자유 선택
- ✔ **ShedLock 통합** - 분산 환경 레지스트리 생성 시 락 보장
- ✔ **Provider 인터페이스** - 사용자 프로젝트의 엔티티/리포지토리 유연 연동
- ✔ **커스텀 네이밍** - `@SchedulerName`으로 스케줄러 이름 지정
- ✔ **Placeholder Resolution** - Spring `${...}` 표현식을 실제 값으로 치환 후 저장
- ✔ **Auto-Configuration** - Spring Boot 자동 구성

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-scheduler'

    // Optional: Database 전략 사용 시
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Optional: 분산 환경 (ShedLock)
    implementation 'net.javacrumbs.shedlock:shedlock-spring'
    implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template'
}
```

### 2. Configuration

```yaml
simplix:
  scheduler:
    enabled: true
    mode: database   # database 또는 in-memory
    retention-days: 90
```

### 3. Usage

```java
@Component
@RequiredArgsConstructor
public class ReportScheduler {

    private final ReportService reportService;

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerName("daily-report-generation")
    public void generateDailyReport() {
        reportService.generate();
        // 실행 시작/종료, 소요 시간, 성공/실패 결과가 자동 기록
    }
}
```

## Configuration Summary

| Property | Default | Description |
|----------|---------|-------------|
| `simplix.scheduler.enabled` | `true` | 모듈 활성화 |
| `simplix.scheduler.aspect-enabled` | `true` | AOP Aspect 활성화 |
| `simplix.scheduler.mode` | `database` | 저장 전략 (database/in-memory) |
| `simplix.scheduler.retention-days` | `90` | 실행 로그 보관 기간 (일) |
| `simplix.scheduler.cleanup-cron` | `0 0 3 * * ?` | 로그 정리 cron |
| `simplix.scheduler.stuck-threshold-minutes` | `30` | stuck 실행 감지 임계값 |
| `simplix.scheduler.excluded-schedulers` | `[]` | 로깅 제외 스케줄러 패턴 |
| `simplix.scheduler.lock.lock-at-most` | `60s` | 레지스트리 생성 락 최대 시간 |
| `simplix.scheduler.lock.lock-at-least` | `1s` | 최소 락 유지 시간 |
| `simplix.scheduler.lock.max-retries` | `3` | 락 재시도 횟수 |

## Architecture

```
simplix-scheduler/
+-- annotation/
|   +-- SchedulerName              # Custom scheduler name annotation
+-- aspect/
|   +-- SchedulerExecutionAspect   # AOP interceptor for @Scheduled
+-- config/
|   +-- SchedulerAutoConfiguration # Auto-configuration entry point
|   +-- SchedulerProperties        # Configuration properties
+-- core/
|   +-- SchedulerLoggingService            # Main logging service
|   +-- SchedulerLoggingStrategy           # Strategy interface
|   +-- SchedulerMetadata                  # Scheduler metadata
|   +-- SchedulerRegistryProvider          # Registry SPI
|   +-- SchedulerExecutionLogProvider      # Execution log SPI
+-- model/
|   +-- SchedulerExecutionContext  # Per-execution context
|   +-- SchedulerExecutionResult   # Success/failure result
|   +-- SchedulerRegistryEntry     # Registry DTO
|   +-- ExecutionStatus            # RUNNING/SUCCESS/FAILED/TIMEOUT
+-- service/
|   +-- DefaultSchedulerLoggingService  # Default service implementation
+-- strategy/
    +-- InMemoryLoggingStrategy    # In-memory strategy
    +-- DatabaseLoggingStrategy    # Database strategy (requires Provider beans)
```

## Storage Strategies

| 전략 | 용도 | 외부 의존성 | Provider 구현 |
|------|------|-------------|----------------|
| `in-memory` | 개발/테스트 | 없음 | 불필요 |
| `database` | 운영 환경 | JPA / DataSource | 필요 |

### In-Memory

```yaml
simplix:
  scheduler:
    mode: in-memory
```

ConcurrentHashMap 기반 저장. 애플리케이션 재시작 시 데이터 손실.

### Database

```yaml
simplix:
  scheduler:
    mode: database
```

`SchedulerRegistryProvider`와 `SchedulerExecutionLogProvider` 빈이 등록되어 있어야 활성화됩니다. 분산 환경에서는 ShedLock으로 레지스트리 중복 생성을 방지합니다.

## Required Implementations

### SchedulerRegistryProvider (Database 전략)

```java
public interface SchedulerRegistryProvider<T> {
    Optional<T> findBySchedulerName(String schedulerName);
    T save(SchedulerRegistryEntry entry);
    int updateLastExecution(String registryId, Instant executionAt, Long durationMs);
    SchedulerRegistryEntry toRegistryEntry(T entity);
    String getRegistryId(T entity);
}
```

### SchedulerExecutionLogProvider (Database 전략)

```java
public interface SchedulerExecutionLogProvider<T> {
    T createFromContext(SchedulerExecutionContext context);
    void applyResult(T entity, SchedulerExecutionResult result);
    T save(T entity);
}
```

상세한 구현 예시는 [Usage Guide](ko/scheduler/usage-guide.md)를 참고하세요.

## Documentation

| Document | Description |
|----------|-------------|
| [Overview](ko/scheduler/overview.md) | 모듈 개요 및 아키텍처 상세 |
| [Usage Guide](ko/scheduler/usage-guide.md) | 빠른 시작과 사용 패턴 |
| [Configuration Reference](ko/scheduler/configuration.md) | 설정 옵션 전체 목록 |

## Requirements

- Java 17+
- Spring Boot 3.5+
- Spring AOP
- (Optional) Spring Data JPA - Database 전략 사용 시
- (Optional) ShedLock - 분산 환경 락 통합 시

## License

SimpleCORE License 1.0 (SCL-1.0)
