# Monitoring Guide

SimpliX Hibernate Cache 모듈의 모니터링 및 관리 가이드입니다.

## EvictionMetrics

캐시 무효화 관련 메트릭을 수집합니다.

### Micrometer 메트릭

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `cache.eviction.local` | Counter | 로컬 캐시 무효화 횟수 |
| `cache.eviction.distributed` | Counter | 분산 캐시 무효화 횟수 |
| `cache.eviction.success` | Counter | 성공한 캐시 무효화 횟수 |
| `cache.eviction.failure` | Counter | 실패한 캐시 무효화 횟수 |
| `cache.eviction.latency` | Timer | 캐시 무효화 지연 시간 |

### 메트릭 사용

```java
@Service
@RequiredArgsConstructor
public class CacheMonitoringService {
    private final EvictionMetrics metrics;

    public void logMetrics() {
        Map<String, Object> data = metrics.metrics();
        log.info("Cache eviction metrics: {}", data);
    }
}
```

### 메트릭 응답 예시

```json
{
  "summary": {
    "localEvictions": 1234,
    "distributedEvictions": 567,
    "broadcasts": 890,
    "successes": 1789,
    "failures": 12,
    "successRate": 99.33,
    "failureRate": 0.67
  },
  "byEntity": {
    "com.example.domain.User": {
      "count": 450,
      "lastEviction": "2024-12-15T10:30:00Z"
    },
    "com.example.domain.Order": {
      "count": 784,
      "lastEviction": "2024-12-15T10:29:55Z"
    }
  },
  "recentActivity": {
    "evictionsLastMinute": 15,
    "currentlyActive": true
  }
}
```

---

## Actuator 엔드포인트

### /actuator/cache-eviction

캐시 무효화 메트릭 전용 엔드포인트입니다.

**설정:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: cache-eviction, cache-admin, health
```

**GET /actuator/cache-eviction**
```bash
curl http://localhost:8080/actuator/cache-eviction
```

**응답:**
```json
{
  "summary": {
    "localEvictions": 1234,
    "distributedEvictions": 567,
    "broadcasts": 890,
    "successes": 1789,
    "failures": 12,
    "successRate": 99.33,
    "failureRate": 0.67
  },
  "byEntity": { ... },
  "recentActivity": { ... }
}
```

### /actuator/cache-admin

캐시 관리 전용 엔드포인트입니다.

**GET /actuator/cache-admin - 상태 조회**
```bash
curl http://localhost:8080/actuator/cache-admin
```

**응답:**
```json
{
  "provider": "Provider: LOCAL, Connected: true, Sent: 0, Received: 0",
  "metrics": { ... },
  "cluster": {
    "activeNodes": 3,
    "nodes": { ... },
    "heartbeatsSent": 120,
    "heartbeatsReceived": 240,
    "syncHealth": "HEALTHY"
  },
  "batch": {
    "batchMode": false,
    "batchDepth": 0,
    "pendingEvictions": 0,
    "batchThreshold": 10,
    "maxDelayMs": 100,
    "shutdown": false
  },
  "retry": {
    "retryQueueSize": 0,
    "deadLetterQueueSize": 0,
    "maxRetryQueueSize": 5000,
    "maxDlqSize": 1000,
    "maxRetryAttempts": 3,
    "retryDelayMs": 1000
  },
  "regions": ["users", "orders", "products"]
}
```

**DELETE /actuator/cache-admin - 캐시 제거**
```bash
# 특정 엔티티 제거
curl -X DELETE "http://localhost:8080/actuator/cache-admin?entityClass=com.example.domain.User&entityId=123"

# 엔티티 타입 전체 제거
curl -X DELETE "http://localhost:8080/actuator/cache-admin?entityClass=com.example.domain.User"
```

**POST /actuator/cache-admin - 전체 캐시 제거**
```bash
# action=evict-all 파라미터 필수 (실수 방지)
curl -X POST "http://localhost:8080/actuator/cache-admin?action=evict-all"
```

> 주의: `action=evict-all` 파라미터를 통해 전체 캐시 제거 의도를 명시적으로 확인합니다.

**POST /actuator/cache-admin - DLQ 재처리**
```bash
# 파라미터 없이 POST 호출 시 DLQ 재처리
curl -X POST http://localhost:8080/actuator/cache-admin
```

**POST /actuator/cache-admin - 배치 모드 전환**
```bash
# enable 파라미터로 배치 모드 제어
curl -X POST "http://localhost:8080/actuator/cache-admin?enable=true"
curl -X POST "http://localhost:8080/actuator/cache-admin?enable=false"
```

---

## ClusterSyncMonitor

분산 환경에서 노드 간 동기화 상태를 모니터링합니다.

### HealthIndicator

Spring Boot Actuator의 `/actuator/health`에 통합됩니다.

**GET /actuator/health**
```json
{
  "status": "UP",
  "components": {
    "clusterSyncMonitor": {
      "status": "UP",
      "details": {
        "syncStatus": "HEALTHY",
        "activeNodes": 3,
        "provider": "LOCAL",
        "providerConnected": true
      }
    }
  }
}
```

### 동기화 상태

| 상태 | 설명 |
|------|------|
| `STANDALONE` | 단일 노드, 클러스터 없음 |
| `HEALTHY` | 모든 노드 활성 |
| `DEGRADED` | 일부 노드 비활성 (과반수 활성) |
| `CRITICAL` | 과반수 노드 비활성 |

### Heartbeat 메커니즘

```
10초 간격
     │
     ▼
┌─────────────────────────────────────────┐
│  sendHeartbeat()                        │
│  CacheEvictionEvent (HEARTBEAT)         │
│  → cacheProvider.broadcastEviction()    │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  다른 노드에서 수신                      │
│  receiveHeartbeat()                     │
│  → NodeStatus 업데이트                   │
└─────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  cleanupInactiveNodes()                 │
│  30초 이상 heartbeat 없음 → 제거         │
└─────────────────────────────────────────┘
```

### 클러스터 상태 API

```java
@Service
@RequiredArgsConstructor
public class ClusterMonitoringService {
    private final ClusterSyncMonitor clusterMonitor;

    public Map<String, Object> getClusterStatus() {
        return clusterMonitor.getClusterStatus();
    }
}
```

---

## 로그 메시지

### 로그 수준별 메시지

| 수준 | 메시지 | 의미 |
|------|--------|------|
| INFO | `✔ SimpliX Hibernate Cache Auto-Management activated` | 모듈 초기화 완료 |
| INFO | `✔ Found N cached entities across M regions` | 엔티티 스캔 완료 |
| INFO | `✔ Cache eviction strategy initialized with provider: X` | 전략 초기화 |
| INFO | `✔ Configuring @Modifying Query Cache Eviction Aspect (auto-detection enabled)` | @Modifying 자동 감지 활성화 |
| DEBUG | `✔ Auto-evicted cache for X operation on Y` | 자동 무효화 성공 |
| DEBUG | `✔ Auto-collected eviction for X via @Modifying on Y` | @Modifying 쿼리 캐시 무효화 수집 |
| DEBUG | `✔ Processed remote cache eviction from node X` | 원격 무효화 수신 |
| WARN | `⚠ Scheduled retry for failed eviction: X` | 재시도 예약 |
| WARN | `⚠ Node X is inactive (last seen: Y)` | 노드 비활성 |
| ERROR | `✖ Failed to auto-evict cache for X` | 자동 무효화 실패 |
| ERROR | `✖ Max retries exceeded, moved to DLQ: X` | DLQ 이동 |

### 로그 설정

```yaml
logging:
  level:
    dev.simplecore.simplix.hibernate.cache: DEBUG
```

**운영 환경 권장:**
```yaml
logging:
  level:
    dev.simplecore.simplix.hibernate.cache: INFO
    dev.simplecore.simplix.hibernate.cache.aspect: WARN
    dev.simplecore.simplix.hibernate.cache.listener: WARN
```

---

## 트러블슈팅

### 캐시가 무효화되지 않는 경우

**증상:** 엔티티 변경 후에도 이전 데이터가 반환됨

**확인 사항:**

1. **@Cache 어노테이션 확인**
   ```java
   @Entity
   @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)  // 필수
   public class User { }
   ```

2. **모듈 활성화 확인**
   ```yaml
   simplix:
     hibernate:
       cache:
         disabled: false  # 기본값
   ```

3. **로그 확인**
   ```yaml
   logging:
     level:
       dev.simplecore.simplix.hibernate.cache: DEBUG
   ```

4. **Actuator로 상태 확인**
   ```bash
   curl http://localhost:8080/actuator/cache-admin
   ```

### 성능 저하

**증상:** 캐시 무효화로 인한 지연

**해결 방법:**

1. **배치 모드 활성화**
   ```yaml
   hibernate:
     cache:
       batch:
         threshold: 50
         max-delay: 200
   ```

2. **@CacheEvictionPolicy로 무효화 범위 축소**
   ```java
   @CacheEvictionPolicy(ignoreFields = {"viewCount", "lastAccessDate"})
   public class Article { }
   ```

3. **쿼리 캐시 무효화 비활성화**
   ```java
   @CacheEvictionPolicy(evictQueryCache = false)
   public class Config { }
   ```

### 메모리 부족

**증상:** OutOfMemoryError, 캐시 크기 증가

**해결 방법:**

1. **EhCache 크기 제한**
   ```xml
   <cache alias="users">
       <heap unit="entries">1000</heap>
       <expiry>
           <ttl unit="minutes">30</ttl>
       </expiry>
   </cache>
   ```

2. **캐시 리전별 설정**
   ```yaml
   spring:
     jpa:
       properties:
         hibernate:
           cache:
             default_cache_concurrency_strategy: read-write
   ```

3. **정기 캐시 정리**
   ```java
   @Scheduled(cron = "0 0 * * * *")  // 매시간
   public void cleanupCache() {
       cacheManager.evictAll();
   }
   ```

### @Modifying 쿼리 캐시 미무효화

**증상:** `@Modifying` 쿼리 실행 후에도 캐시가 갱신되지 않음

**확인 사항:**

1. **JPQL 엔티티명 확인**
   ```java
   // 올바른 예 - 엔티티 클래스명과 일치
   @Query("UPDATE User u SET u.status = :status")

   // 잘못된 예 - 테이블명 사용 (감지 불가)
   @Query("UPDATE users SET status = :status")  // nativeQuery=true 포함
   ```

2. **@Cache 어노테이션 확인**
   ```java
   @Entity
   @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)  // 필수
   public class User { }
   ```

3. **로그 확인**
   ```yaml
   logging:
     level:
       dev.simplecore.simplix.hibernate.cache.aspect: DEBUG
   ```

   정상 작동 시 로그:
   ```
   ✔ Auto-collected eviction for User via @Modifying on UserRepository.updateStatus(..)
   ```

4. **명시적 @EvictCache 사용**
   자동 감지 실패 시 명시적으로 지정:
   ```java
   @Modifying
   @Query(value = "UPDATE users SET status = ?1", nativeQuery = true)
   @EvictCache(User.class)  // 네이티브 쿼리는 명시적 지정 필요
   int updateStatus(String status);
   ```

### 분산 환경 동기화 실패

**증상:** 노드 간 캐시 불일치

**확인 사항:**

1. **프로바이더 연결 확인**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

2. **노드 ID 고유성 확인**
   ```yaml
   simplix:
     hibernate:
       cache:
         node-id: ${HOSTNAME}-${random.uuid}
   ```

3. **Redis 채널 확인**
   ```yaml
   simplix:
     hibernate:
       cache:
         redis:
           channel: prod-hibernate-cache-sync
           pub-sub-enabled: true
   ```

4. **DLQ 상태 확인**
   ```bash
   curl http://localhost:8080/actuator/cache-admin
   # retry.deadLetterQueueSize 확인
   ```

5. **DLQ 재처리**
   ```bash
   curl -X POST http://localhost:8080/actuator/cache-admin
   ```

---

## 알림 설정

### Prometheus + Alertmanager

```yaml
groups:
  - name: hibernate-cache
    rules:
      - alert: HighCacheEvictionFailureRate
        expr: rate(cache_eviction_failures_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High cache eviction failure rate"

      - alert: ClusterSyncDegraded
        expr: cluster_sync_health != "HEALTHY"
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Cluster sync is degraded"
```

### Spring Boot Admin

```yaml
spring:
  boot:
    admin:
      notify:
        mail:
          enabled: true
          to: admin@example.com
```

---

## Related Documents

- [Overview (아키텍처 상세)](./overview.md) - 모듈 구조 및 컴포넌트
- [Configuration Guide (설정 가이드)](./configuration.md) - 설정 옵션 및 @Cache 사용법
- [Cache Eviction Guide (캐시 무효화)](./cache-eviction.md) - 수동 제거 및 재시도
