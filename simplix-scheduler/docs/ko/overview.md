# SimpliX Scheduler Module Overview

## Overview

SimpliX Scheduler 모듈은 Spring `@Scheduled` 메서드의 실행을 자동으로 추적하고 로깅하는 AOP 기반 모니터링 시스템입니다. 스케줄러 실행 이력, 성공/실패 상태, 실행 시간 등을 자동으로 기록합니다.

## Features

- **AOP 기반 자동 로깅**: `@Scheduled` 메서드를 자동으로 인터셉트하여 실행 정보 기록
- **Strategy Pattern**: database/in-memory 저장 전략 지원
- **ShedLock 통합**: 분산 환경에서 스케줄러 레지스트리 생성 시 락 지원
- **Provider Interface**: 사용자 프로젝트에서 엔티티/리포지토리를 유연하게 구현
- **Auto-Configuration**: Spring Boot 자동 설정
- **Custom Naming**: `@SchedulerName` 어노테이션으로 스케줄러 이름 지정
- **Placeholder Resolution**: Spring `${...}` placeholder를 실제 값으로 치환하여 저장

---

## Architecture

```
+---------------------------------------------------------------------+
|                        Application                                  |
|                             |                                       |
|              @Scheduled + @SchedulerName                            |
|                             |                                       |
+-----------------------------+---------------------------------------+
                              |
                              v
+---------------------------------------------------------------------+
|                  SimpliX Scheduler Module                           |
|                                                                     |
|  +--------------------+    +--------------------------+             |
|  | SchedulerExecution |--->| SchedulerLoggingService  |             |
|  | Aspect (AOP)       |    | (Main Entry Point)       |             |
|  +--------------------+    +------------+-------------+             |
|                                         |                           |
|                    +--------------------+--------------------+      |
|                    |                                         |      |
|                    v                                         v      |
|           +-----------------+                  +------------------+ |
|           | InMemory        |                  | Database         | |
|           | LoggingStrategy |                  | LoggingStrategy  | |
|           +-----------------+                  +--------+---------+ |
|                                                         |           |
+---------------------------------------------------------------------+
                                                          |
                              +---------------------------+
                              |
              +---------------+---------------+
              |                               |
              v                               v
+------------------------+    +---------------------------+
| SchedulerRegistry      |    | SchedulerExecutionLog     |
| Provider               |    | Provider                  |
| (User Implementation)  |    | (User Implementation)     |
+------------------------+    +---------------------------+
              |                               |
              v                               v
+--------------------------------------------------------------------+
|                     User's Database                                |
|  +------------------------+    +---------------------------+       |
|  | scheduler_job_registry |    | scheduler_execution_log   |       |
|  +------------------------+    +---------------------------+       |
+--------------------------------------------------------------------+
```

---

## Core Components

### SchedulerExecutionAspect

`@Scheduled` 메서드를 자동으로 인터셉트하는 AOP Aspect입니다:

```java
@Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
public Object logSchedulerExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    // 1. 스케줄러 메타데이터 추출
    // 2. 레지스트리 엔트리 확인/생성
    // 3. 실행 시작 기록
    // 4. 메서드 실행
    // 5. 성공/실패 결과 기록
}
```

**주요 기능:**
- 자동 레지스트리 엔트리 생성 (첫 실행 시)
- 실행 시간 추적 및 상태 로깅
- 서비스명 구분 (api-server vs scheduler-server)
- 오류 발생 시에도 스케줄러 실행 계속 (graceful error handling)

### SchedulerLoggingService

스케줄러 로깅의 주 진입점 인터페이스입니다:

```java
public interface SchedulerLoggingService {
    // 레지스트리 엔트리 확인/생성
    SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata);

    // 실행 컨텍스트 생성
    SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName
    );

    // 실행 결과 저장
    void saveExecutionResult(
        SchedulerExecutionContext context,
        SchedulerExecutionResult result
    );

    // 로깅 활성화 여부
    boolean isEnabled();

    // 스케줄러 제외 여부 확인
    boolean isExcluded(String schedulerName);
}
```

### SchedulerLoggingStrategy

저장소 구현을 위한 전략 인터페이스입니다:

```java
public interface SchedulerLoggingStrategy {
    String getName();
    boolean supports(String mode);

    SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata);
    SchedulerExecutionContext createExecutionContext(
        SchedulerRegistryEntry registry,
        String serviceName,
        String serverHost
    );
    void saveExecutionResult(
        SchedulerExecutionContext context,
        SchedulerExecutionResult result
    );

    default void initialize() {}
    default void shutdown() {}
    default void clearCache() {}
}
```

### InMemoryLoggingStrategy

인메모리 저장 전략입니다:

- **용도**: 개발/테스트 환경
- **특징**:
  - 외부 의존성 없음
  - 애플리케이션 재시작 시 데이터 손실
  - ConcurrentHashMap 기반

### DatabaseLoggingStrategy

데이터베이스 저장 전략입니다:

- **용도**: 운영 환경
- **특징**:
  - 영속적인 실행 이력 저장
  - ShedLock 통합으로 분산 환경 지원
  - 레지스트리 캐싱으로 DB 쿼리 최소화

---

## Model Classes

### SchedulerMetadata

스케줄러 메서드의 메타데이터를 담는 불변 객체입니다:

```java
@Value
@Builder
public class SchedulerMetadata {
    String schedulerName;      // 고유 스케줄러 이름
    String className;          // 클래스 전체 경로
    String methodName;         // 메서드 이름
    String cronExpression;     // cron 표현식 또는 fixedDelay/Rate 정보
    String shedlockName;       // ShedLock 이름 (분산 스케줄러)
    SchedulerType schedulerType;  // LOCAL 또는 DISTRIBUTED
}
```

### SchedulerRegistryEntry

스케줄러 레지스트리 정보를 담는 DTO입니다:

```java
@Value
@Builder
@With
public class SchedulerRegistryEntry {
    String registryId;         // 레지스트리 ID
    String schedulerName;      // 스케줄러 이름
    String className;          // 클래스명
    String methodName;         // 메서드명
    SchedulerType schedulerType;
    String shedlockName;
    String cronExpression;
    String displayName;        // UI 표시용 이름
    Boolean enabled;           // 활성화 여부
    Instant lastExecutionAt;   // 마지막 실행 시간
    Long lastDurationMs;       // 마지막 실행 소요 시간
}
```

### SchedulerExecutionContext

단일 스케줄러 실행의 컨텍스트입니다:

```java
@Value
@Builder
@With
public class SchedulerExecutionContext {
    String registryId;
    String schedulerName;
    String shedlockName;
    Instant startTime;
    ExecutionStatus status;
    String serviceName;
    String serverHost;
}
```

### SchedulerExecutionResult

스케줄러 실행 결과입니다:

```java
@Value
@Builder
public class SchedulerExecutionResult {
    ExecutionStatus status;
    Instant endTime;
    Long durationMs;
    String errorMessage;
    Integer itemsProcessed;

    // 팩토리 메서드
    public static SchedulerExecutionResult success(long durationMs);
    public static SchedulerExecutionResult success(long durationMs, int itemsProcessed);
    public static SchedulerExecutionResult failure(long durationMs, String errorMessage);
    public static SchedulerExecutionResult timeout(long durationMs);
}
```

### ExecutionStatus

실행 상태 열거형입니다:

```java
public enum ExecutionStatus {
    RUNNING,   // 실행 중
    SUCCESS,   // 성공
    FAILED,    // 실패
    TIMEOUT    // 타임아웃
}
```

---

## Provider Interfaces

Database 전략 사용 시 사용자 프로젝트에서 구현해야 하는 인터페이스입니다.

### SchedulerRegistryProvider

스케줄러 레지스트리 엔티티 연동을 위한 Provider입니다:

```java
public interface SchedulerRegistryProvider<T> {
    Optional<T> findBySchedulerName(String schedulerName);
    T save(SchedulerRegistryEntry entry);
    int updateLastExecution(String registryId, Instant executionAt, Long durationMs);
    SchedulerRegistryEntry toRegistryEntry(T entity);
    String getRegistryId(T entity);
}
```

### SchedulerExecutionLogProvider

실행 로그 엔티티 연동을 위한 Provider입니다:

```java
public interface SchedulerExecutionLogProvider<T> {
    T createFromContext(SchedulerExecutionContext context);
    void applyResult(T entity, SchedulerExecutionResult result);
    T save(T entity);
}
```

---

## Auto-Configuration

### SchedulerAutoConfiguration

Spring Boot 자동 구성 클래스입니다:

```java
@AutoConfiguration
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(name = "simplix.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerAutoConfiguration {
    // Bean definitions...
}
```

**조건부 빈 생성:**

| Bean | 조건 |
|------|------|
| `schedulerLoggingService` | 기본 |
| `schedulerExecutionAspect` | `aspect-enabled=true` (기본) |
| `inMemoryLoggingStrategy` | 기본 (fallback) |
| `databaseLoggingStrategy` | RegistryProvider + LogProvider 빈 존재 시 |

---

## Configuration Properties

### 전체 설정 구조

```yaml
simplix:
  scheduler:
    # 스케줄러 로깅 활성화 (기본: true)
    enabled: true

    # AOP Aspect 활성화 (기본: true)
    aspect-enabled: true

    # 로깅 모드: database, in-memory
    mode: database

    # 실행 로그 보관 기간 (일)
    retention-days: 90

    # 로그 정리 cron 표현식
    cleanup-cron: "0 0 3 * * ?"

    # stuck 실행 감지 임계값 (분)
    stuck-threshold-minutes: 30

    # 로깅 제외 스케줄러 이름 패턴
    excluded-schedulers:
      - CacheMetricsCollector
      - HealthCheckScheduler

    # 분산 락 설정
    lock:
      lock-at-most: 60s
      lock-at-least: 1s
      max-retries: 3
      retry-delays-ms: [100, 200, 500]
```

### SchedulerProperties 클래스

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `enabled` | boolean | true | 스케줄러 로깅 활성화 |
| `aspectEnabled` | boolean | true | AOP Aspect 활성화 |
| `mode` | String | "database" | 저장 전략 (database/in-memory) |
| `retentionDays` | int | 90 | 로그 보관 기간 |
| `cleanupCron` | String | "0 0 3 * * ?" | 정리 작업 cron |
| `stuckThresholdMinutes` | int | 30 | stuck 감지 임계값 |
| `excludedSchedulers` | List<String> | [] | 제외할 스케줄러 패턴 |
| `lock.lockAtMost` | Duration | 60s | 최대 락 유지 시간 |
| `lock.lockAtLeast` | Duration | 1s | 최소 락 유지 시간 |
| `lock.maxRetries` | int | 3 | 락 획득 재시도 횟수 |
| `lock.retryDelaysMs` | long[] | [100,200,500] | 재시도 지연 시간 |

---

## Strategy Selection Guide

| 환경 | 권장 전략 | 이유 |
|------|----------|------|
| 개발/테스트 | in-memory | 외부 의존성 없음 |
| 단일 인스턴스 운영 | database | 영속적 이력 저장 |
| 분산 환경 | database + ShedLock | 락 기반 중복 방지 |

---

## Dependencies

```gradle
dependencies {
    api project(':simplix-core')

    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-aop'

    // Optional: ShedLock (분산 환경)
    compileOnly 'net.javacrumbs.shedlock:shedlock-spring'
    compileOnly 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template'

    // Optional: JPA (database 전략)
    compileOnly 'org.springframework.boot:spring-boot-starter-data-jpa'
}
```

---

## Related Documents

- [Usage Guide](./usage-guide.md) - 상세 사용법 가이드
- [Configuration Reference](./configuration.md) - 설정 옵션 상세
