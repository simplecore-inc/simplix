# SimpliX Hibernate Cache Module Overview

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                          │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  @Entity + @Cache                                         │  │
│  │  JpaRepository                                            │  │
│  └──────────────────────────┬────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────┘
                              │
┌─────────────────────── Transaction Boundary ───────────────────┐
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  HibernateIntegrator (POST_COMMIT events)                 │  │
│  │  - requiresPostCommitHandling() = true                    │  │
│  │  - POST_INSERT, POST_UPDATE, POST_DELETE 감지             │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
│                             ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  TransactionAwareCacheEvictionCollector                   │  │
│  │  - ThreadLocal로 pending evictions 수집                   │  │
│  │  - TransactionSynchronization 등록                        │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              │ AFTER_COMMIT
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  PostCommitCacheEvictionHandler                           │  │
│  │  @EventListener(PendingEvictionCompletedEvent)           │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
│                             ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  CacheEvictionStrategy                    │  │
│  │  - Cache Mode 판단 (LOCAL/DISTRIBUTED/HYBRID)             │  │
│  │  - 로컬 캐시 제거                                         │  │
│  │  - 분산 캐시 브로드캐스트                                 │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
│         ┌───────────────────┼───────────────────┐               │
│         ▼                   ▼                   ▼               │
│  ┌──────────────┐  ┌────────────────────┐  ┌────────────────┐   │
│  │BatchEviction │  │EvictionRetryHandler│  │ClusterSync     │   │
│  │Optimizer     │  │- 실패 재시도       │  │Monitor         │   │
│  │- 배치 최적화 │  │- Dead letter 큐    │  │- 노드 동기화   │   │
│  └──────────────┘  └────────────────────┘  └────────────────┘   │
│                             │                                   │
│                             ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  HibernateCacheManager                    │  │
│  │  - evictEntity()        - evictQueryRegion()              │  │
│  │  - evictEntityCache()   - evictAll()                      │  │
│  │  - evictRegion()        - contains()                      │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                   │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Hibernate 2nd Level Cache                      │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  EhCache / JCache                                          │ │
│  │  Entity Cache Regions | Query Cache Regions                │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Key Design: Transaction-Aware Eviction

캐시 무효화가 **트랜잭션 커밋 후에만** 실행됩니다:

- `TransactionSynchronization.afterCommit()`에서만 캐시 삭제 이벤트 발행
- 롤백 시 캐시 유지, 분산 브로드캐스트도 커밋 후에만 전송

---

## Core Components

### HibernateCacheManager

Hibernate 2nd-level 캐시 작업의 중앙 관리자입니다.

```java
public class HibernateCacheManager {
    // 특정 엔티티 캐시 제거
    public void evictEntity(Class<?> entityClass, Object id);

    // 엔티티 타입 전체 캐시 제거
    public void evictEntityCache(Class<?> entityClass);

    // 특정 리전 제거
    public void evictRegion(String regionName);

    // 쿼리 캐시 리전 제거
    public void evictQueryRegion(String queryRegion);

    // 모든 캐시 제거
    public void evictAll();

    // 캐시 존재 확인
    public boolean contains(Class<?> entityClass, Object id);

    // 활성 리전 목록
    public Set<String> getActiveRegions();
}
```

### EntityCacheScanner

애플리케이션 시작 시 `@Cache` 어노테이션이 있는 엔티티를 자동으로 스캔합니다.

```java
// 지정 패키지에서 캐시된 엔티티 스캔
scanner.scanForCachedEntities("com.example.domain");

// 캐시된 엔티티 목록 조회
Set<Class<?>> cachedEntities = scanner.getCachedEntities();

// 캐시 리전 목록 조회
Set<String> regions = scanner.getCacheRegions();

// 특정 엔티티 캐시 여부 확인
boolean isCached = scanner.isCached(User.class);
```

**자동 스캔:**
- `simplix.hibernate.cache.scan-packages` 설정 또는
- 패키지 미지정 시 전체 클래스패스 스캔

### CacheProvider

분산 캐시 프로바이더의 추상화 인터페이스입니다.

```java
public interface CacheProvider {
    // 프로바이더 타입
    String getType();

    // 사용 가능 여부
    boolean isAvailable();

    // 캐시 무효화 브로드캐스트
    void broadcastEviction(CacheEvictionEvent event);

    // 무효화 이벤트 구독
    void subscribeToEvictions(CacheEvictionEventListener listener);

    // 프로바이더 초기화
    void initialize();

    // 프로바이더 종료
    void shutdown();

    // 통계 조회
    CacheProviderStats getStats();
}
```

**지원 프로바이더:**

| Provider | 상태 | 설명 |
|----------|------|------|
| LocalCacheProvider | ✔ 지원 | 로컬 전용 (분산 무효화 없음) |
| RedisCacheProvider | ✔ 지원 | Redis Pub/Sub 기반 |
| HazelcastCacheProvider | ✔ 지원 | Hazelcast ITopic 기반 |
| InfinispanCacheProvider | ✔ 지원 | Infinispan 클러스터 기반 |

프로바이더는 클래스패스에 따라 자동으로 선택됩니다 (우선순위: Redis > Hazelcast > Infinispan > Local).

### CacheEvictionStrategy

캐시 모드에 따른 무효화 전략을 결정합니다.

```java
public class CacheEvictionStrategy {
    // 엔티티 변경 시 캐시 제거
    public void evict(Class<?> entityClass, Object entityId);

    // 현재 프로바이더 정보
    public String getActiveProviderInfo();
}
```

**동작 흐름:**
1. Cache Mode 판단 (AUTO/LOCAL/DISTRIBUTED/HYBRID)
2. 로컬 캐시 제거
3. DISTRIBUTED/HYBRID 모드면 다른 노드에 브로드캐스트

---

## Cache Modes

| Mode | 설명 | 사용 환경 |
|------|------|----------|
| `AUTO` | 프로바이더 기반 자동 감지 | 기본값 |
| `LOCAL` | 로컬 캐시만 사용 | 단일 인스턴스 |
| `DISTRIBUTED` | 분산 캐시 동기화 | 다중 인스턴스 |
| `HYBRID` | 로컬 + 분산 혼합 | 고성능 다중 인스턴스 |
| `DISABLED` | 캐시 관리 비활성화 | 디버깅/테스트 |

### AUTO Mode 동작

프로바이더에 따라 자동으로 모드가 결정됩니다:

```
CacheProviderFactory.selectBestAvailable()
     │
     ├─ Redis 사용 가능 → DISTRIBUTED
     ├─ Hazelcast 사용 가능 → DISTRIBUTED
     ├─ Infinispan 사용 가능 → DISTRIBUTED
     └─ 기타 → LOCAL
```

---

## 캐시 무효화 인터셉션 포인트

SimpliX Hibernate는 다음 방식으로 엔티티 변경을 감지합니다:

### 1. HibernateIntegrator (Primary - Hibernate SPI)

Hibernate 내부 SPI를 통한 저수준 인터셉션으로, **트랜잭션 커밋 후** 이벤트를 처리합니다.

```java
// requiresPostCommitHandling() = true 로 설정하여 커밋 후 호출됨
eventListenerRegistry.appendListeners(EventType.POST_INSERT,
    new PostInsertEventListener() {
        @Override
        public void onPostInsert(PostInsertEvent event) {
            // TransactionAwareCacheEvictionCollector에 eviction 수집
        }

        @Override
        public boolean requiresPostCommitHandling(EntityPersister persister) {
            return true; // 커밋 후에만 호출됨
        }
    });
```

**장점:** Hibernate 네이티브 통합, 트랜잭션 인식
**적용:** 모든 EntityManager를 통한 변경

### 2. ModifyingQueryCacheEvictionAspect (AOP)

`@Modifying` 쿼리에 대한 캐시 무효화를 처리합니다. JPQL에서 대상 엔티티를 **자동으로 감지**합니다.

```java
// 자동 감지 모드: @Query의 JPQL을 파싱하여 엔티티 추출
@Around("@annotation(org.springframework.data.jpa.repository.Modifying)")
public Object handleModifyingQuery(ProceedingJoinPoint joinPoint) {
    Object result = joinPoint.proceed();
    // "UPDATE User u SET ..." → User 엔티티 자동 감지
    // "DELETE FROM Order o WHERE ..." → Order 엔티티 자동 감지
    Class<?> entityClass = extractEntityFromQuery(joinPoint);
    if (entityClass != null) {
        evictionCollector.collect(PendingEviction.of(entityClass, null, null, operation));
    }
    return result;
}

// 명시적 지정 모드: @EvictCache로 대상 엔티티 직접 지정
@Around("@annotation(dev.simplecore.simplix.hibernate.cache.annotation.EvictCache)")
public Object handleEvictCache(ProceedingJoinPoint joinPoint) {
    Object result = joinPoint.proceed();
    // @EvictCache에 지정된 엔티티 클래스의 캐시 무효화 수집
    return result;
}
```

**자동 감지 지원 JPQL 패턴:**
- `UPDATE EntityName alias SET ...`
- `DELETE FROM EntityName alias WHERE ...`
- `DELETE EntityName alias WHERE ...`

**장점:** Bulk 연산 지원, @EvictCache 없이 자동 캐시 관리
**적용:** @Modifying 쿼리 사용 시 (Spring Data JPA)

### 3. AutoCacheEvictionAspect (AOP)

Spring AOP로 JpaRepository의 `save*`, `delete*` 메서드를 인터셉트합니다.

```java
@AfterReturning(
    value = "execution(* JpaRepository+.save*(..)) " +
            "|| execution(* JpaRepository+.delete*(..))",
    returning = "result"
)
public void handleRepositoryOperation(JoinPoint joinPoint, Object result) {
    // 연관된 쿼리 캐시 제거
}
```

**장점:** Repository 레벨 인터셉트, 쿼리 캐시 연동
**적용:** Spring Data JPA Repository 사용 시

---

## Cache Eviction Flow

```
Entity Change (save/update/delete)
     │
     ▼
┌─────────────────────────────────────────┐
│  HibernateIntegrator                     │
│  - POST_INSERT/UPDATE/DELETE 이벤트      │
│  - requiresPostCommitHandling() = true  │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  TransactionAwareCacheEvictionCollector  │
│  - ThreadLocal에 PendingEviction 수집    │
│  - TransactionSynchronization 등록       │
└────────────────┬────────────────────────┘
                 │
     ┌───────────┴───────────┐
     │                       │
  COMMIT                  ROLLBACK
     │                       │
     ▼                       ▼
┌──────────────┐   ┌──────────────────────┐
│afterCommit() │   │ThreadLocal 정리       │
│PendingEvict- │   │→ 캐시 삭제 없음       │
│ionCompleted  │   └──────────────────────┘
│Event 발행    │
└──────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  PostCommitCacheEvictionHandler          │
│  @EventListener                          │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  CacheEvictionStrategy.evict()          │
└────────────────┬────────────────────────┘
                 │
     ┌───────────┴───────────┐
     ▼                       ▼
LOCAL Mode              DISTRIBUTED Mode
     │                       │
     ▼                       ▼
┌──────────────┐   ┌──────────────────────┐
│HibernateCache│   │1. 로컬 캐시 제거      │
│Manager       │   │2. CacheProvider.     │
│.evictEntity()│   │   broadcastEviction()│
└──────────────┘   └──────────────────────┘
                            │
                            ▼
                   ┌──────────────────────┐
                   │다른 노드에서 수신     │
                   │onEvictionEvent()     │
                   │→ 로컬 캐시 제거       │
                   └──────────────────────┘
```

---

## Auto-Configuration

`SimpliXHibernateCacheAutoConfiguration`이 자동으로 활성화됩니다.

**활성화 조건:**
- Hibernate 클래스패스 존재
- EntityManagerFactory 빈 존재
- `simplix.hibernate.cache.disabled=false` (기본값)

**자동 등록 빈:**

| Bean | 설명 |
|------|------|
| HibernateCacheManager | 캐시 작업 관리자 |
| TransactionAwareCacheEvictionCollector | 트랜잭션 인식 eviction 수집기 |
| PostCommitCacheEvictionHandler | 커밋 후 캐시 삭제 핸들러 |
| ModifyingQueryCacheEvictionAspect | @Modifying 쿼리 캐시 무효화 (자동 감지/명시적) |
| EntityCacheScanner | @Cache 엔티티 스캔 |
| QueryCacheManager | 쿼리 캐시 관리 |
| AutoCacheEvictionAspect | JpaRepository 메서드 인터셉트 |
| LocalCacheProvider | 로컬 캐시 프로바이더 |
| CacheProviderFactory | 프로바이더 팩토리 |
| CacheEvictionStrategy | 무효화 전략 (LOCAL/DISTRIBUTED/HYBRID) |
| BatchEvictionOptimizer | 대량 작업 배치 최적화 |
| EvictionRetryHandler | 실패 재시도 핸들러 |
| ClusterSyncMonitor | 클러스터 동기화 모니터 + HealthIndicator |
| EvictionMetrics | Micrometer 메트릭 수집 |
| CacheAdminController | Actuator 엔드포인트 (/actuator/cache-admin) |
| HibernateCacheInitializer | 시작 시 엔티티 스캔 초기화 |

**조건부 등록 빈 (분산 캐시):**

| Bean | 조건 | 설명 |
|------|------|------|
| RedisCacheProvider | `spring-data-redis` 존재 | Redis Pub/Sub 프로바이더 |
| HazelcastCacheProvider | `hazelcast` 존재 | Hazelcast ITopic 프로바이더 |
| InfinispanCacheProvider | `infinispan-core` 존재 | Infinispan 클러스터 프로바이더 |

---

## CacheEvictionEvent

캐시 무효화 이벤트의 데이터 구조입니다. 불변(Immutable) 객체로 설계되어 동시성 문제를 방지합니다.

```java
@Getter
@Builder(toBuilder = true)
public class CacheEvictionEvent implements Serializable {
    String eventId;        // 이벤트 고유 ID (멱등성 보장)
    String entityClass;    // 엔티티 클래스명
    String entityId;       // 엔티티 ID
    String region;         // 캐시 리전
    String operation;      // 작업 유형 (INSERT/UPDATE/DELETE)
    Long timestamp;        // 발생 시각
    String nodeId;         // 발생 노드 ID
}
```

**주요 특징:**
- `eventId`로 이벤트 중복 처리 방지 (네트워크 재시도 시)
- `@JsonCreator`를 통한 Jackson 역직렬화 지원
- `withNodeId()` 메서드로 불변성 유지하며 nodeId 설정

분산 환경에서 노드 간 캐시 무효화 브로드캐스트에 사용됩니다.

---

## Related Documents

- [Configuration Guide (설정 가이드)](./configuration.md) - 설정 옵션 및 @Cache 사용법
- [Cache Eviction Guide (캐시 무효화)](./cache-eviction.md) - 수동 제거 및 정책 설정
- [Monitoring Guide (모니터링)](./monitoring.md) - 메트릭, Actuator, 트러블슈팅
