# Cache Eviction Guide

캐시 무효화 메커니즘 및 수동 제거 방법 가이드입니다.

## 자동 캐시 무효화

SimpliX Hibernate는 4가지 인터셉션 포인트를 통해 엔티티 변경을 자동으로 감지하고 캐시를 무효화합니다.

### 1. GlobalEntityListener (JPA)

JPA 표준 엔티티 라이프사이클 콜백을 사용합니다.

```java
public class GlobalEntityListener {
    @PostPersist
    public void onPersist(Object entity) { ... }

    @PostUpdate
    public void onUpdate(Object entity) { ... }

    @PostRemove
    public void onRemove(Object entity) { ... }
}
```

**동작:**
- `@Cache` 어노테이션 확인
- 엔티티 ID 추출
- `HibernateCacheManager.evictEntity()` 호출
- `CacheEvictionEvent` 발행

### 2. AutoCacheEvictionAspect (AOP)

Spring AOP로 JpaRepository 메서드를 인터셉트합니다.

```java
@AfterReturning(
    value = "execution(* JpaRepository+.save*(..)) " +
            "|| execution(* JpaRepository+.delete*(..))",
    returning = "result"
)
public void handleRepositoryOperation(JoinPoint joinPoint, Object result) {
    // 1. Repository에서 엔티티 클래스 추출
    // 2. @Cache 어노테이션 확인
    // 3. save 또는 delete에 따른 캐시 제거
    // 4. 연관된 쿼리 캐시 제거
}
```

**인터셉트 대상:**
- `save()`, `saveAll()`, `saveAndFlush()`
- `delete()`, `deleteById()`, `deleteAll()`, `deleteAllById()`

### 3. AutoCacheEvictionListener (Spring Event)

Spring ApplicationEvent 기반 캐시 무효화입니다.

```java
@EventListener
public void handleCacheEviction(CacheEvictionEvent event) {
    Class<?> entityClass = Class.forName(event.getEntityClass());
    cacheManager.evictEntity(entityClass, event.getEntityId());
}
```

**수동 이벤트 발행:**
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final ApplicationEventPublisher eventPublisher;

    public void customOperation() {
        // ... 비즈니스 로직 ...

        // 수동 캐시 무효화 이벤트 발행
        eventPublisher.publishEvent(CacheEvictionEvent.builder()
            .entityClass(User.class.getName())
            .entityId("123")
            .operation("CUSTOM")
            .timestamp(System.currentTimeMillis())
            .build());
    }
}
```

### 4. HibernateIntegrator (Hibernate SPI)

Hibernate 내부 SPI를 통한 저수준 인터셉션입니다. EntityManager를 우회하는 네이티브 쿼리에서도 동작합니다.

---

## CacheEvictionEvent

캐시 무효화 이벤트의 데이터 구조입니다.

```java
public class CacheEvictionEvent {
    String entityClass;    // 엔티티 클래스명 (FQCN)
    String entityId;       // 엔티티 ID (null이면 전체 제거)
    String region;         // 캐시 리전
    String operation;      // INSERT, UPDATE, DELETE, CUSTOM
    Long timestamp;        // 발생 시각 (ms)
    String nodeId;         // 발생 노드 ID
}
```

---

## 수동 캐시 제거

### HibernateCacheManager API

```java
@Service
@RequiredArgsConstructor
public class CacheAdminService {
    private final HibernateCacheManager cacheManager;

    // 특정 엔티티 캐시 제거
    public void evictUser(Long userId) {
        cacheManager.evictEntity(User.class, userId);
    }

    // 엔티티 타입 전체 캐시 제거
    public void evictAllUsers() {
        cacheManager.evictEntityCache(User.class);
    }

    // 특정 리전 제거
    public void evictRegion(String regionName) {
        cacheManager.evictRegion(regionName);
    }

    // 쿼리 캐시 리전 제거
    public void evictQueryCache(String queryRegion) {
        cacheManager.evictQueryRegion(queryRegion);
    }

    // 모든 캐시 제거
    public void evictAll() {
        cacheManager.evictAll();
    }

    // 캐시 존재 확인
    public boolean isCached(Long userId) {
        return cacheManager.contains(User.class, userId);
    }

    // 활성 리전 목록
    public Set<String> getActiveRegions() {
        return cacheManager.getActiveRegions();
    }
}
```

### API 상세

| 메서드 | 설명 |
|--------|------|
| `evictEntity(Class, id)` | 특정 엔티티 ID의 캐시 제거 |
| `evictEntityCache(Class)` | 엔티티 타입 전체 캐시 제거 |
| `evictRegion(name)` | 특정 리전의 모든 캐시 제거 |
| `evictQueryRegion(name)` | 쿼리 캐시 리전 제거 |
| `evictAll()` | 모든 2nd-level 캐시 제거 |
| `contains(Class, id)` | 캐시 존재 여부 확인 |
| `getActiveRegions()` | 활성 캐시 리전 목록 |

---

## BatchEvictionOptimizer

대량 작업 시 네트워크 오버헤드를 줄이기 위한 배치 최적화입니다.

### 배치 모드 사용

```java
@Service
@RequiredArgsConstructor
public class BulkOperationService {
    private final BatchEvictionOptimizer batchOptimizer;
    private final UserRepository userRepository;

    @Transactional
    public void bulkUpdateUsers(List<User> users) {
        // 배치 모드 시작
        try (var batch = batchOptimizer.startBatch()) {
            for (User user : users) {
                userRepository.save(user);
                // 캐시 무효화 요청은 큐에 추가됨
            }
        }
        // try 블록 종료 시 자동으로 배치 플러시
    }
}
```

### 설정

```yaml
hibernate:
  cache:
    batch:
      threshold: 10        # 이 개수 도달 시 자동 플러시
      max-delay: 100       # 최대 지연 시간 (ms)
```

### 동작 방식

```
┌─────────────────────────────────────────┐
│  startBatch()                           │
│  batchMode = true                       │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  addToBatch(event)                      │
│  pendingEvictions.offer(event)          │
│                                         │
│  if (count >= threshold)                │
│      flushBatch()                       │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  flushBatch()                           │
│  1. 유사 이벤트 병합                     │
│  2. cacheProvider.broadcastEviction()   │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  endBatch()                             │
│  batchMode = false                      │
└─────────────────────────────────────────┘
```

### 이벤트 병합 규칙

동일한 `entityClass + region` 조합의 이벤트는 병합됩니다:

```
Before:
  - User:123, region=users
  - User:456, region=users
  - User:789, region=users

After (merged):
  - User:null, region=users  (전체 리전 제거)
```

---

## EvictionRetryHandler

분산 환경에서 네트워크 장애 시 재시도를 처리합니다.

### 재시도 흐름

```
캐시 무효화 실패
     │
     ▼
┌─────────────────────────────────────────┐
│  scheduleRetry(event, error)            │
│  retryQueue.offer(failedEviction)       │
└────────────────┬────────────────────────┘
                 │
                 ▼ (1초 간격)
┌─────────────────────────────────────────┐
│  processRetries()                       │
│                                         │
│  if (attempts >= maxRetryAttempts)      │
│      → deadLetterQueue 이동              │
│  else                                   │
│      → 재시도                            │
│      → 성공 시 큐에서 제거               │
│      → 실패 시 attempts++ 후 재큐        │
└─────────────────────────────────────────┘
```

### 설정

```yaml
hibernate:
  cache:
    retry:
      max-attempts: 3      # 최대 재시도 횟수
      delay: 1000          # 재시도 간격 (ms)
```

### Dead Letter Queue 재처리

```java
@Service
@RequiredArgsConstructor
public class CacheAdminService {
    private final EvictionRetryHandler retryHandler;

    // DLQ 재처리
    public void reprocessDeadLetterQueue() {
        retryHandler.reprocessDeadLetterQueue();
    }

    // 재시도 통계 조회
    public Map<String, Object> getRetryStats() {
        return retryHandler.getRetryStatistics();
    }
}
```

**통계 정보:**
```json
{
  "retryQueueSize": 0,
  "deadLetterQueueSize": 2,
  "maxRetryAttempts": 3,
  "retryDelayMs": 1000
}
```

---

## @CacheEvictionPolicy

엔티티별 캐시 무효화 정책을 세밀하게 제어합니다.

### 기본 사용

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy
public class User {
    // 모든 변경 시 캐시 제거
}
```

### 특정 필드 변경 시에만 제거

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(evictOnChange = {"name", "email", "status"})
public class User {
    private String name;       // 변경 시 캐시 제거
    private String email;      // 변경 시 캐시 제거
    private UserStatus status; // 변경 시 캐시 제거
    private LocalDateTime lastLoginDate; // 변경해도 캐시 유지
}
```

### 특정 필드 무시

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(ignoreFields = {"lastModifiedDate", "version", "viewCount"})
public class Article {
    private int viewCount;     // 변경해도 캐시 유지
    private int version;       // 변경해도 캐시 유지
}
```

### 쿼리 캐시 제거 비활성화

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(evictQueryCache = false)
public class Config {
    // 엔티티 캐시만 제거, 쿼리 캐시는 유지
}
```

### 커스텀 제거 전략

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(strategy = VIPOnlyEvictionStrategy.class)
public class User {
    private UserType type;
    private String name;
}

public class VIPOnlyEvictionStrategy implements CacheEvictionPolicy.EvictionStrategy {
    @Override
    public boolean shouldEvict(Object entity, String[] dirtyFields) {
        if (entity instanceof User user) {
            // VIP 사용자만 캐시 제거
            return user.getType() == UserType.VIP;
        }
        return true;
    }
}
```

---

## 쿼리 캐시 무효화

### 자동 무효화

엔티티 변경 시 연관된 쿼리 캐시가 자동으로 무효화됩니다.

```java
// AutoCacheEvictionAspect에서 처리
private void evictQueryCaches(Class<?> entityClass) {
    var queryRegions = queryCacheManager.getQueryRegionsForEntity(entityClass);

    for (String region : queryRegions) {
        cacheManager.evictQueryRegion(region);
    }

    // 기본 쿼리 캐시도 제거
    cacheManager.evictQueryRegion("default");
}
```

### 수동 무효화

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    private final HibernateCacheManager cacheManager;

    public void evictUserQueries() {
        cacheManager.evictQueryRegion("user-queries");
    }

    public void evictAllQueries() {
        cacheManager.evictQueryRegion("default");
    }
}
```

---

## 분산 캐시 무효화

### 브로드캐스트 흐름

```
Node A: 엔티티 변경
     │
     ▼
┌─────────────────────────────────────────┐
│  CacheEvictionStrategy.evict()          │
│  1. 로컬 캐시 제거                       │
│  2. CacheEvictionEvent 생성              │
│  3. cacheProvider.broadcastEviction()   │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  Redis Pub/Sub (또는 다른 프로바이더)     │
│  channel: hibernate-cache-sync          │
└────────────────┬────────────────────────┘
                 │
     ┌───────────┴───────────┐
     ▼                       ▼
   Node B                  Node C
     │                       │
     ▼                       ▼
onEvictionEvent()      onEvictionEvent()
로컬 캐시 제거          로컬 캐시 제거
```

### 이벤트 필터링

같은 노드에서 발생한 이벤트는 무시합니다:

```java
@Override
public void onEvictionEvent(CacheEvictionEvent event) {
    // 자신이 발행한 이벤트는 무시
    if (nodeId.equals(event.getNodeId())) {
        return;
    }

    // 다른 노드의 이벤트 처리
    cacheManager.evictEntity(entityClass, event.getEntityId());
}
```

---

## 코드 예제

### 조건부 캐시 제거

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final HibernateCacheManager cacheManager;
    private final UserRepository userRepository;

    @Transactional
    public void updateUserProfile(Long userId, UserProfileDto dto) {
        User user = userRepository.findById(userId).orElseThrow();

        boolean significantChange = !Objects.equals(user.getEmail(), dto.getEmail())
                                 || !Objects.equals(user.getName(), dto.getName());

        user.updateProfile(dto);
        userRepository.save(user);

        // 중요한 변경 시에만 쿼리 캐시 제거
        if (significantChange) {
            cacheManager.evictQueryRegion("user-queries");
        }
    }
}
```

### 대량 데이터 처리

```java
@Service
@RequiredArgsConstructor
public class DataMigrationService {
    private final BatchEvictionOptimizer batchOptimizer;
    private final UserRepository userRepository;

    @Transactional
    public void migrateUsers(List<UserMigrationDto> migrations) {
        try (var batch = batchOptimizer.startBatch()) {
            for (UserMigrationDto dto : migrations) {
                User user = userRepository.findById(dto.getUserId()).orElseThrow();
                user.migrate(dto);
                userRepository.save(user);
            }
        }
        // 모든 캐시 무효화가 배치로 처리됨
    }
}
```

---

## Related Documents

- [Overview (아키텍처 상세)](./overview.md) - 모듈 구조 및 컴포넌트
- [Configuration Guide (설정 가이드)](./configuration.md) - 설정 옵션 및 @Cache 사용법
- [Monitoring Guide (모니터링)](./monitoring.md) - 메트릭, Actuator, 트러블슈팅
