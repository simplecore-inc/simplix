# SimpliX Hibernate Cache Module Overview

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Application Layer                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  @Entity + @Cache                                         │  │
│  │  JpaRepository                                            │  │
│  └──────────────────────────┬────────────────────────────────┘  │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│              SimpliX Hibernate Cache Module                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  4가지 인터셉션 포인트                                      │  │
│  │  ┌─────────────────┐  ┌─────────────────────────────────┐ │  │
│  │  │GlobalEntity     │  │AutoCacheEvictionAspect (AOP)   │ │  │
│  │  │Listener (JPA)   │  │- save*, delete* 인터셉트        │ │  │
│  │  │- @PostPersist   │  │                                 │ │  │
│  │  │- @PostUpdate    │  ├─────────────────────────────────┤ │  │
│  │  │- @PostRemove    │  │AutoCacheEvictionListener       │ │  │
│  │  │                 │  │- Spring ApplicationEvent        │ │  │
│  │  └────────┬────────┘  └────────────────┬────────────────┘ │  │
│  │           │                            │                   │  │
│  └───────────┼────────────────────────────┼───────────────────┘  │
│              └──────────────┬─────────────┘                      │
│                             ▼                                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  CacheEvictionStrategy                     │  │
│  │  - Cache Mode 판단 (LOCAL/DISTRIBUTED/HYBRID)              │  │
│  │  - 로컬 캐시 제거                                           │  │
│  │  - 분산 캐시 브로드캐스트                                    │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                    │
│         ┌───────────────────┼───────────────────┐               │
│         ▼                   ▼                   ▼               │
│  ┌────────────┐  ┌────────────────────┐  ┌────────────────┐    │
│  │BatchEviction│  │EvictionRetryHandler│  │ClusterSync     │    │
│  │Optimizer   │  │- 실패 재시도        │  │Monitor         │    │
│  │- 배치 최적화│  │- Dead letter 큐    │  │- 노드 동기화   │    │
│  └────────────┘  └────────────────────┘  └────────────────┘    │
│                             │                                    │
│                             ▼                                    │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                  HibernateCacheManager                     │  │
│  │  - evictEntity()        - evictQueryRegion()               │  │
│  │  - evictEntityCache()   - evictAll()                       │  │
│  │  - evictRegion()        - contains()                       │  │
│  └──────────────────────────┬────────────────────────────────┘  │
│                             │                                    │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Hibernate 2nd Level Cache                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  EhCache / JCache                                          │ │
│  │  Entity Cache Regions | Query Cache Regions                │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

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

    // 통계 조회
    CacheProviderStats getStats();
}
```

**지원 프로바이더:**

| Provider | 상태 | 설명 |
|----------|------|------|
| LocalCacheProvider | ✔ 지원 | 로컬 전용 (분산 무효화 없음) |
| RedisProvider | 향후 지원 | Redis Pub/Sub 기반 |
| HazelcastProvider | 향후 지원 | Hazelcast 토픽 기반 |

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
     └─ 기타 → LOCAL
```

---

## 4가지 인터셉션 포인트

SimpliX Hibernate는 4가지 방식으로 엔티티 변경을 감지합니다:

### 1. GlobalEntityListener (JPA)

JPA 표준 엔티티 리스너로 `@PostPersist`, `@PostUpdate`, `@PostRemove` 이벤트를 처리합니다.

```java
@PostPersist
@PostUpdate
@PostRemove
public void onEntityChange(Object entity) {
    // @Cache 어노테이션 확인 후 캐시 제거
}
```

**장점:** JPA 표준, 모든 엔티티 변경 감지
**적용:** EntityManager를 통한 모든 변경

### 2. AutoCacheEvictionAspect (AOP)

Spring AOP로 JpaRepository의 `save*`, `delete*` 메서드를 인터셉트합니다.

```java
@AfterReturning(
    value = "execution(* JpaRepository+.save*(..)) " +
            "|| execution(* JpaRepository+.delete*(..))",
    returning = "result"
)
public void handleRepositoryOperation(JoinPoint joinPoint, Object result) {
    // 엔티티 클래스 추출 후 캐시 제거
    // 연관된 쿼리 캐시도 함께 제거
}
```

**장점:** Repository 레벨 인터셉트, 쿼리 캐시 연동
**적용:** Spring Data JPA Repository 사용 시

### 3. AutoCacheEvictionListener (Spring Event)

Spring ApplicationEvent를 통한 캐시 무효화 이벤트 처리입니다.

```java
@EventListener
public void handleCacheEviction(CacheEvictionEvent event) {
    // 이벤트 기반 캐시 제거
}
```

**장점:** 느슨한 결합, 커스텀 이벤트 발행 가능
**적용:** 수동 이벤트 발행 시

### 4. HibernateIntegrator (Hibernate SPI)

Hibernate 내부 SPI를 통한 저수준 인터셉션입니다.

**장점:** Hibernate 네이티브 통합
**적용:** EntityManager 우회 시에도 동작

---

## Cache Eviction Flow

```
Entity Change (save/update/delete)
     │
     ▼
┌─────────────────────────────────────────┐
│  인터셉션 포인트에서 변경 감지           │
│  - GlobalEntityListener                 │
│  - AutoCacheEvictionAspect              │
│  - AutoCacheEvictionListener            │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  CacheEvictionEvent 생성                 │
│  - entityClass: User                    │
│  - entityId: 123                        │
│  - nodeId: node-xxx                     │
│  - timestamp: 1702xxx                   │
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
| EntityCacheScanner | 엔티티 스캔 |
| QueryCacheManager | 쿼리 캐시 관리 |
| GlobalEntityListener | JPA 리스너 |
| AutoCacheEvictionAspect | AOP 어스펙트 |
| AutoCacheEvictionListener | 이벤트 리스너 |
| CacheEvictionStrategy | 무효화 전략 |
| BatchEvictionOptimizer | 배치 최적화 |
| EvictionRetryHandler | 재시도 핸들러 |
| ClusterSyncMonitor | 클러스터 모니터 |
| EvictionMetrics | Micrometer 메트릭 |
| CacheAdminController | Actuator 엔드포인트 |

---

## CacheEvictionEvent

캐시 무효화 이벤트의 데이터 구조입니다.

```java
public class CacheEvictionEvent {
    String entityClass;    // 엔티티 클래스명
    String entityId;       // 엔티티 ID
    String region;         // 캐시 리전
    String operation;      // 작업 유형 (INSERT/UPDATE/DELETE)
    Long timestamp;        // 발생 시각
    String nodeId;         // 발생 노드 ID
}
```

분산 환경에서 노드 간 캐시 무효화 브로드캐스트에 사용됩니다.

---

## Related Documents

- [Configuration Guide (설정 가이드)](./configuration.md) - 설정 옵션 및 @Cache 사용법
- [Cache Eviction Guide (캐시 무효화)](./cache-eviction.md) - 수동 제거 및 정책 설정
- [Monitoring Guide (모니터링)](./monitoring.md) - 메트릭, Actuator, 트러블슈팅
