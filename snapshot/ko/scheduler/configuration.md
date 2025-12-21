# SimpliX Scheduler Configuration Reference

## Configuration Properties

### 전체 설정 구조

```yaml
simplix:
  scheduler:
    # 스케줄러 로깅 활성화
    enabled: true

    # AOP Aspect 활성화
    aspect-enabled: true

    # 저장 전략 모드
    mode: database

    # 로그 보관 기간 (일)
    retention-days: 90

    # 로그 정리 cron
    cleanup-cron: "0 0 3 * * ?"

    # stuck 실행 감지 임계값 (분)
    stuck-threshold-minutes: 30

    # 로깅 제외 스케줄러 목록
    excluded-schedulers: []

    # 분산 락 설정
    lock:
      lock-at-most: 60s
      lock-at-least: 1s
      max-retries: 3
      retry-delays-ms: [100, 200, 500]
```

---

## Property Reference

### 기본 설정

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.scheduler.enabled` | boolean | `true` | 스케줄러 로깅 기능 활성화 |
| `simplix.scheduler.aspect-enabled` | boolean | `true` | AOP Aspect 활성화. false로 설정하면 `@Scheduled` 메서드 인터셉트 비활성화 |
| `simplix.scheduler.mode` | String | `"database"` | 저장 전략. `database` 또는 `in-memory` |

### 로그 관리

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.scheduler.retention-days` | int | `90` | 실행 로그 보관 기간 (일) |
| `simplix.scheduler.cleanup-cron` | String | `"0 0 3 * * ?"` | 로그 정리 작업 cron 표현식 |
| `simplix.scheduler.stuck-threshold-minutes` | int | `30` | RUNNING 상태로 이 시간 이상 유지되면 stuck으로 감지 |

### 제외 설정

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.scheduler.excluded-schedulers` | List<String> | `[]` | 로깅에서 제외할 스케줄러 이름 패턴 목록 |

**제외 패턴 예시:**

```yaml
simplix:
  scheduler:
    excluded-schedulers:
      - CacheMetricsCollector      # 클래스명 prefix
      - HealthCheckScheduler       # 클래스명 prefix
      - internal-                  # @SchedulerName prefix
```

### 분산 락 설정

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `simplix.scheduler.lock.lock-at-most` | Duration | `60s` | 최대 락 유지 시간 |
| `simplix.scheduler.lock.lock-at-least` | Duration | `1s` | 최소 락 유지 시간 |
| `simplix.scheduler.lock.max-retries` | int | `3` | 락 획득 실패 시 재시도 횟수 |
| `simplix.scheduler.lock.retry-delays-ms` | long[] | `[100,200,500]` | 재시도 간격 (ms, 지수 백오프) |

---

## Strategy Configuration

### In-Memory Strategy

```yaml
simplix:
  scheduler:
    enabled: true
    mode: in-memory
```

**특징:**
- 외부 의존성 없음
- Provider 구현 불필요
- 애플리케이션 재시작 시 데이터 손실

### Database Strategy

```yaml
simplix:
  scheduler:
    enabled: true
    mode: database
```

**필수 조건:**
- `SchedulerRegistryProvider` 빈 등록
- `SchedulerExecutionLogProvider` 빈 등록

**선택 조건:**
- `LockProvider` 빈 등록 (분산 환경)

---

## Conditional Bean Registration

`SchedulerAutoConfiguration`에서 조건에 따라 빈이 등록됩니다:

| Bean | Condition |
|------|-----------|
| `schedulerLoggingService` | `simplix.scheduler.enabled=true` |
| `schedulerExecutionAspect` | `simplix.scheduler.enabled=true` AND `simplix.scheduler.aspect-enabled=true` |
| `inMemoryLoggingStrategy` | Always (fallback) |
| `databaseLoggingStrategy` | `SchedulerRegistryProvider` AND `SchedulerExecutionLogProvider` beans exist |

---

## Environment Variables

| Environment Variable | Property | Description |
|---------------------|----------|-------------|
| `SCHEDULER_ENABLED` | `simplix.scheduler.enabled` | 로깅 활성화 |
| `SCHEDULER_MODE` | `simplix.scheduler.mode` | 저장 전략 |
| `SCHEDULER_RETENTION_DAYS` | `simplix.scheduler.retention-days` | 보관 기간 |

**Example:**

```bash
export SCHEDULER_ENABLED=true
export SCHEDULER_MODE=database
export SCHEDULER_RETENTION_DAYS=30
```

```yaml
simplix:
  scheduler:
    enabled: ${SCHEDULER_ENABLED:true}
    mode: ${SCHEDULER_MODE:database}
    retention-days: ${SCHEDULER_RETENTION_DAYS:90}
```

---

## Profile-Based Configuration

### 개발 환경

```yaml
# application-dev.yml
simplix:
  scheduler:
    enabled: true
    mode: in-memory
    stuck-threshold-minutes: 5
```

### 운영 환경

```yaml
# application-prod.yml
simplix:
  scheduler:
    enabled: true
    mode: database
    retention-days: 90
    stuck-threshold-minutes: 30
    excluded-schedulers:
      - HealthCheck
      - Metrics
    lock:
      lock-at-most: 120s
      max-retries: 5
```

### 테스트 환경

```yaml
# application-test.yml
simplix:
  scheduler:
    enabled: false  # 테스트 시 비활성화
```

---

## ShedLock Configuration

분산 환경에서 ShedLock을 함께 사용하는 경우:

```yaml
simplix:
  scheduler:
    mode: database
    lock:
      lock-at-most: 60s    # SimpliX 레지스트리 생성 락
      lock-at-least: 1s
      max-retries: 3
```

**ShedLock 테이블:**

```sql
CREATE TABLE shedlock (
    name       VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP   NOT NULL,
    locked_at  TIMESTAMP   NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
```

**ShedLock 어노테이션:**

```java
@Scheduled(cron = "0 0 * * * ?")
@SchedulerLock(
    name = "hourly-job",
    lockAtMostFor = "PT59M",   // ShedLock: 스케줄러 중복 실행 방지
    lockAtLeastFor = "PT5M"
)
public void hourlyJob() {
    // ...
}
```

| 설정 | SimpliX scheduler.lock | ShedLock @SchedulerLock |
|------|------------------------|-------------------------|
| 용도 | 레지스트리 생성 락 | 스케줄러 실행 락 |
| 적용 시점 | 첫 실행 시 레지스트리 생성 | 매 실행 시 |
| 필수 여부 | 선택 | 분산 환경 필수 |

---

## Logging Configuration

```yaml
logging:
  level:
    dev.simplecore.simplix.scheduler: INFO  # 기본
    dev.simplecore.simplix.scheduler.aspect: DEBUG  # Aspect 상세
    dev.simplecore.simplix.scheduler.strategy: DEBUG  # 전략 동작
```

| Log Level | Content |
|-----------|---------|
| TRACE | 모든 실행 상세 정보 |
| DEBUG | 실행 시작/종료, 캐시 동작, 전략 선택 |
| INFO | 레지스트리 생성, 전략 초기화 |
| WARN | 로깅 실패, 락 획득 재시도 |
| ERROR | 치명적 오류 |

---

## Complete Example

```yaml
spring:
  application:
    name: scheduler-service

simplix:
  scheduler:
    enabled: true
    aspect-enabled: true
    mode: database
    retention-days: 90
    cleanup-cron: "0 0 3 * * ?"
    stuck-threshold-minutes: 30
    excluded-schedulers:
      - CacheMetricsCollector
      - HealthCheckScheduler
    lock:
      lock-at-most: 60s
      lock-at-least: 1s
      max-retries: 3
      retry-delays-ms: [100, 200, 500]

logging:
  level:
    dev.simplecore.simplix.scheduler: INFO
```

---

## Related Documents

- [Overview](ko/scheduler/overview.md) - 모듈 개요 및 아키텍처
- [Usage Guide](ko/scheduler/usage-guide.md) - 상세 사용법 가이드
