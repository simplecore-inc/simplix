# SimpliX Scheduler Usage Guide

## Quick Start

### 1. 의존성 추가

```gradle
implementation 'dev.simplecore:simplix-scheduler:${version}'

// Database 전략 사용 시
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

// 분산 환경 (ShedLock 통합)
implementation 'net.javacrumbs.shedlock:shedlock-spring'
implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template'
```

### 2. 설정 추가

```yaml
simplix:
  scheduler:
    enabled: true
    mode: database  # 또는 in-memory
```

### 3. 스케줄러 작성

```java
@Component
public class MyScheduler {

    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyCleanup() {
        // 자동으로 실행 로그가 기록됩니다
    }
}
```

---

## @SchedulerName Annotation

기본적으로 스케줄러 이름은 `ClassName_methodName` 형식으로 자동 생성됩니다.
커스텀 이름을 지정하려면 `@SchedulerName` 어노테이션을 사용합니다:

```java
@Scheduled(cron = "0 0 3 * * ?")
@SchedulerName("audit-data-purge")
public void purgeOldAuditData() {
    // 스케줄러 이름: "audit-data-purge"
}
```

**권장 네이밍 형식:** kebab-case (예: `user-sync`, `cache-cleanup`, `report-generation`)

---

## In-Memory Strategy

개발/테스트 환경에서 외부 의존성 없이 사용할 수 있습니다.

### 설정

```yaml
simplix:
  scheduler:
    enabled: true
    mode: in-memory
```

### 특징

- 외부 데이터베이스 불필요
- 애플리케이션 재시작 시 데이터 손실
- Provider 구현 불필요

---

## Database Strategy

운영 환경에서 영속적인 실행 이력 저장이 필요한 경우 사용합니다.

### 1. 테이블 생성

```sql
-- 스케줄러 레지스트리 테이블
CREATE TABLE scheduler_job_registry (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    scheduler_name      VARCHAR(255) NOT NULL UNIQUE,
    class_name          VARCHAR(500) NOT NULL,
    method_name         VARCHAR(255) NOT NULL,
    scheduler_type      VARCHAR(50) NOT NULL,  -- LOCAL, DISTRIBUTED
    shedlock_name       VARCHAR(255),
    cron_expression     VARCHAR(255),
    display_name        VARCHAR(255),
    enabled             BOOLEAN DEFAULT TRUE,
    last_execution_at   TIMESTAMP,
    last_duration_ms    BIGINT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 실행 로그 테이블
CREATE TABLE scheduler_execution_log (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT,
    scheduler_job_registry_id BIGINT NOT NULL,
    scheduler_name          VARCHAR(255) NOT NULL,
    shedlock_name           VARCHAR(255),
    execution_start_at      TIMESTAMP NOT NULL,
    execution_end_at        TIMESTAMP,
    duration_ms             BIGINT,
    status                  VARCHAR(50) NOT NULL,  -- RUNNING, SUCCESS, FAILED, TIMEOUT
    error_message           TEXT,
    items_processed         INT,
    service_name            VARCHAR(255),
    server_host             VARCHAR(255),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (scheduler_job_registry_id) REFERENCES scheduler_job_registry(id)
);

-- 인덱스
CREATE INDEX idx_exec_log_registry_id ON scheduler_execution_log(scheduler_job_registry_id);
CREATE INDEX idx_exec_log_start_at ON scheduler_execution_log(execution_start_at);
CREATE INDEX idx_exec_log_status ON scheduler_execution_log(status);
```

### 2. 엔티티 구현

```java
@Entity
@Table(name = "scheduler_job_registry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerJobRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String schedulerName;

    @Column(nullable = false, length = 500)
    private String className;

    @Column(nullable = false)
    private String methodName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SchedulerType schedulerType;

    private String shedlockName;
    private String cronExpression;
    private String displayName;

    @Column(nullable = false)
    private Boolean enabled = true;

    private Instant lastExecutionAt;
    private Long lastDurationMs;

    public static SchedulerJobRegistry from(SchedulerRegistryEntry entry) {
        SchedulerJobRegistry entity = new SchedulerJobRegistry();
        entity.schedulerName = entry.getSchedulerName();
        entity.className = entry.getClassName();
        entity.methodName = entry.getMethodName();
        entity.schedulerType = SchedulerType.valueOf(entry.getSchedulerType().name());
        entity.shedlockName = entry.getShedlockName();
        entity.cronExpression = entry.getCronExpression();
        entity.displayName = entry.getDisplayName();
        entity.enabled = entry.getEnabled() != null ? entry.getEnabled() : true;
        return entity;
    }

    public enum SchedulerType {
        LOCAL, DISTRIBUTED
    }
}
```

```java
@Entity
@Table(name = "scheduler_execution_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long schedulerJobRegistryId;

    @Column(nullable = false)
    private String schedulerName;

    private String shedlockName;

    @Column(nullable = false)
    private Instant executionStartAt;

    private Instant executionEndAt;
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Integer itemsProcessed;
    private String serviceName;
    private String serverHost;

    public static SchedulerExecutionLog from(SchedulerExecutionContext context) {
        SchedulerExecutionLog log = new SchedulerExecutionLog();
        log.schedulerJobRegistryId = Long.parseLong(context.getRegistryId());
        log.schedulerName = context.getSchedulerName();
        log.shedlockName = context.getShedlockName();
        log.executionStartAt = context.getStartTime();
        log.status = context.getStatus();
        log.serviceName = context.getServiceName();
        log.serverHost = context.getServerHost();
        return log;
    }

    public void markSuccess(long durationMs) {
        this.executionEndAt = Instant.now();
        this.durationMs = durationMs;
        this.status = ExecutionStatus.SUCCESS;
    }

    public void markFailed(long durationMs, String errorMessage) {
        this.executionEndAt = Instant.now();
        this.durationMs = durationMs;
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public enum ExecutionStatus {
        RUNNING, SUCCESS, FAILED, TIMEOUT
    }
}
```

### 3. Repository 구현

```java
public interface SchedulerJobRegistryRepository extends JpaRepository<SchedulerJobRegistry, Long> {

    Optional<SchedulerJobRegistry> findBySchedulerName(String schedulerName);

    @Modifying
    @Query("UPDATE SchedulerJobRegistry r SET r.lastExecutionAt = :executionAt, r.lastDurationMs = :durationMs WHERE r.id = :id")
    int updateLastExecution(
        @Param("id") Long id,
        @Param("executionAt") Instant executionAt,
        @Param("durationMs") Long durationMs
    );
}

public interface SchedulerExecutionLogRepository extends JpaRepository<SchedulerExecutionLog, Long> {
}
```

### 4. Provider 구현

```java
@Component
@RequiredArgsConstructor
public class SchedulerJobRegistryProviderImpl
    implements SchedulerRegistryProvider<SchedulerJobRegistry> {

    private final SchedulerJobRegistryRepository repository;

    @Override
    public Optional<SchedulerJobRegistry> findBySchedulerName(String schedulerName) {
        return repository.findBySchedulerName(schedulerName);
    }

    @Override
    @Transactional
    public SchedulerJobRegistry save(SchedulerRegistryEntry entry) {
        SchedulerJobRegistry entity = SchedulerJobRegistry.from(entry);
        return repository.save(entity);
    }

    @Override
    @Transactional
    public int updateLastExecution(String registryId, Instant executionAt, Long durationMs) {
        return repository.updateLastExecution(
            Long.parseLong(registryId),
            executionAt,
            durationMs
        );
    }

    @Override
    public SchedulerRegistryEntry toRegistryEntry(SchedulerJobRegistry entity) {
        return SchedulerRegistryEntry.builder()
            .registryId(String.valueOf(entity.getId()))
            .schedulerName(entity.getSchedulerName())
            .className(entity.getClassName())
            .methodName(entity.getMethodName())
            .schedulerType(SchedulerMetadata.SchedulerType.valueOf(entity.getSchedulerType().name()))
            .shedlockName(entity.getShedlockName())
            .cronExpression(entity.getCronExpression())
            .displayName(entity.getDisplayName())
            .enabled(entity.getEnabled())
            .lastExecutionAt(entity.getLastExecutionAt())
            .lastDurationMs(entity.getLastDurationMs())
            .build();
    }

    @Override
    public String getRegistryId(SchedulerJobRegistry entity) {
        return String.valueOf(entity.getId());
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class SchedulerExecutionLogProviderImpl
    implements SchedulerExecutionLogProvider<SchedulerExecutionLog> {

    private final SchedulerExecutionLogRepository repository;

    @Override
    public SchedulerExecutionLog createFromContext(SchedulerExecutionContext context) {
        return SchedulerExecutionLog.from(context);
    }

    @Override
    public void applyResult(SchedulerExecutionLog entity, SchedulerExecutionResult result) {
        if (result.isSuccess()) {
            entity.markSuccess(result.getDurationMs());
        } else if (result.isFailed()) {
            entity.markFailed(result.getDurationMs(), result.getErrorMessage());
        } else if (result.isTimeout()) {
            entity.markFailed(result.getDurationMs(), "Execution timeout");
        }
    }

    @Override
    @Transactional
    public SchedulerExecutionLog save(SchedulerExecutionLog entity) {
        return repository.save(entity);
    }
}
```

---

## ShedLock Integration

분산 환경에서 스케줄러 중복 실행 방지 및 레지스트리 생성 락을 위해 ShedLock을 통합합니다.

### 1. 의존성 추가

```gradle
implementation 'net.javacrumbs.shedlock:shedlock-spring'
implementation 'net.javacrumbs.shedlock:shedlock-provider-jdbc-template'
```

### 2. ShedLock 설정

```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        );
    }
}
```

### 3. 분산 스케줄러 작성

```java
@Component
public class DistributedScheduler {

    @Scheduled(cron = "0 0 * * * ?")
    @SchedulerLock(name = "hourly-sync", lockAtMostFor = "PT59M", lockAtLeastFor = "PT5M")
    @SchedulerName("hourly-data-sync")
    public void hourlySync() {
        // ShedLock으로 분산 락 적용
        // SimpliX Scheduler는 자동으로 DISTRIBUTED 타입으로 인식
    }
}
```

---

## Excluding Schedulers

특정 스케줄러를 로깅에서 제외할 수 있습니다:

```yaml
simplix:
  scheduler:
    excluded-schedulers:
      - CacheMetrics      # CacheMetrics로 시작하는 모든 스케줄러
      - HealthCheck       # HealthCheck로 시작하는 모든 스케줄러
      - internal-         # @SchedulerName("internal-xxx") 형태
```

제외 패턴은 **prefix 매칭**으로 동작합니다.

---

## Logging Levels

```yaml
logging:
  level:
    dev.simplecore.simplix.scheduler: DEBUG
```

| 레벨 | 내용 |
|------|------|
| TRACE | 모든 스케줄러 실행 상세 정보 |
| DEBUG | 스케줄러 실행 시작/종료, 캐시 동작 |
| INFO | 레지스트리 생성, 전략 초기화 |
| WARN | 로깅 초기화 실패, 락 획득 재시도 |
| ERROR | 실행 실패 |

---

## Best Practices

### 1. 스케줄러 이름 규칙

```java
// Good - 명확하고 일관된 kebab-case
@SchedulerName("user-activity-sync")
@SchedulerName("daily-report-generation")
@SchedulerName("cache-cleanup")

// Bad - 불명확하거나 일관성 없음
@SchedulerName("sync")
@SchedulerName("dailyReportGeneration")
@SchedulerName("CACHE_CLEANUP")
```

### 2. 실행 시간이 긴 스케줄러

```java
@Scheduled(cron = "0 0 2 * * ?")
@SchedulerLock(name = "heavy-batch", lockAtMostFor = "PT4H", lockAtLeastFor = "PT1H")
@SchedulerName("heavy-batch-job")
public void heavyBatchJob() {
    // stuck-threshold-minutes보다 오래 걸릴 수 있는 작업
}
```

`stuck-threshold-minutes` 설정을 적절히 조정하세요:

```yaml
simplix:
  scheduler:
    stuck-threshold-minutes: 240  # 4시간
```

### 3. 서비스 구분

여러 서비스에서 동일한 스케줄러가 실행되는 경우, `spring.application.name`으로 구분됩니다:

```yaml
spring:
  application:
    name: scheduler-service  # 또는 api-service
```

### 4. 로그 보관 정책

```yaml
simplix:
  scheduler:
    retention-days: 90        # 90일 보관
    cleanup-cron: "0 0 3 * * ?"  # 매일 새벽 3시 정리
```

---

## Troubleshooting

### 스케줄러가 로깅되지 않음

1. `simplix.scheduler.enabled=true` 확인
2. `simplix.scheduler.aspect-enabled=true` 확인
3. 해당 스케줄러가 `excluded-schedulers`에 포함되어 있는지 확인
4. AOP 프록시가 제대로 적용되었는지 확인 (self-invocation 주의)

### Database 전략이 동작하지 않음

1. `SchedulerRegistryProvider` 구현체가 빈으로 등록되어 있는지 확인
2. `SchedulerExecutionLogProvider` 구현체가 빈으로 등록되어 있는지 확인
3. `simplix.scheduler.mode=database` 설정 확인

### 분산 환경에서 중복 레지스트리 생성

1. ShedLock 의존성이 추가되어 있는지 확인
2. `LockProvider` 빈이 등록되어 있는지 확인
3. ShedLock 테이블이 생성되어 있는지 확인

---

## Related Documents

- [Overview](ko/scheduler/overview.md) - 모듈 개요 및 아키텍처
- [Configuration Reference](ko/scheduler/configuration.md) - 설정 옵션 상세
