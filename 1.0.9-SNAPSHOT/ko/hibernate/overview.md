# SimpliX Hibernate Cache Module Overview

## Architecture

```
+-------------------------------------------------------------------+
|                      Application Layer                            |
|  +-------------------------------------------------------------+  |
|  |  @Entity + @Cache                                           |  |
|  |  JpaRepository                                              |  |
|  +--------------------------+----------------------------------+  |
+-----------------------------+-------------------------------------+
                              |
                              |  @Modifying Query with @EvictCache
                              v
+-------------------------------------------------------------------+
|  +-------------------------------------------------------------+  |
|  |  ModifyingQueryCacheEvictionAspect (AOP)                    |  |
|  |  - Process @EvictCache annotation                           |  |
|  |  - Entity class based cache eviction                        |  |
|  +--------------------------+----------------------------------+  |
|                             |                                     |
+-----------------------------+-------------------------------------+
                              |
+----------------------- Transaction Boundary ----------------------+
|                             |                                     |
|                             v                                     |
|  +-------------------------------------------------------------+  |
|  |  TransactionAwareCacheEvictionCollector                     |  |
|  |  - Collect pending evictions via ThreadLocal                |  |
|  |  - Register TransactionSynchronization                      |  |
|  +--------------------------+----------------------------------+  |
|                             |                                     |
+-----------------------------+-------------------------------------+
                              |
                              | AFTER_COMMIT
                              v
+-------------------------------------------------------------------+
|  +-------------------------------------------------------------+  |
|  |  PostCommitCacheEvictionHandler                             |  |
|  |  @EventListener(PendingEvictionCompletedEvent)              |  |
|  +--------------------------+----------------------------------+  |
|                             |                                     |
|                             v                                     |
|  +-------------------------------------------------------------+  |
|  |                  CacheEvictionStrategy                      |  |
|  |  - Evict local cache                                        |  |
|  +--------------------------+----------------------------------+  |
|                             |                                     |
|                             v                                     |
|  +-------------------------------------------------------------+  |
|  |                  HibernateCacheManager                      |  |
|  |  - evictEntity()        - evictQueryRegion()                |  |
|  |  - evictEntityCache()   - evictAll()                        |  |
|  |  - evictRegion()        - contains()                        |  |
|  +--------------------------+----------------------------------+  |
|                             |                                     |
+-----------------------------+-------------------------------------+
                              |
                              v
+-------------------------------------------------------------------+
|                  Hibernate 2nd Level Cache                        |
|  +-------------------------------------------------------------+  |
|  |  EhCache / JCache                                           |  |
|  |  Entity Cache Regions | Query Cache Regions                 |  |
|  +-------------------------------------------------------------+  |
+-------------------------------------------------------------------+
```

### Key Design: Transaction-Aware Eviction

캐시 무효화가 **트랜잭션 커밋 후에만** 실행됩니다:

- `TransactionSynchronization.afterCommit()`에서만 캐시 삭제 이벤트 발행
- 롤백 시 캐시 유지

---

## Hibernate Native Cache Management

Hibernate 2nd-level 캐시는 기본 EntityManager 작업에 대해 자동으로 캐시를 관리합니다:

| 작업 | Hibernate 처리 | SimpliX 역할 |
|------|---------------|-------------|
| `repository.save()` | 자동 캐시 업데이트 | 불필요 |
| `repository.delete()` | 자동 캐시 제거 | 불필요 |
| `entityManager.persist()` | 자동 캐시 업데이트 | 불필요 |
| `entityManager.remove()` | 자동 캐시 제거 | 불필요 |
| `@Modifying` 쿼리 | **처리 안 됨** | **@EvictCache 필요** |

SimpliX Hibernate 모듈은 Hibernate의 네이티브 기능을 대체하지 않고, **@Modifying 쿼리에 대한 보완적 지원**을 제공합니다.

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

### CacheEvictionStrategy

캐시 무효화를 수행합니다.

```java
public class CacheEvictionStrategy {
    // 엔티티 변경 시 캐시 제거
    public void evict(Class<?> entityClass, Object entityId);

    // 클래스명으로 캐시 제거
    public void evict(String entityClassName, Object entityId);
}
```

---

## @Modifying 쿼리 캐시 무효화

`@Modifying` 쿼리는 Hibernate 엔티티 이벤트를 발생시키지 않습니다. SimpliX는 **`@EvictCache` 어노테이션**을 통해 명시적으로 캐시 무효화를 지정합니다.

```java
@Modifying
@Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
@EvictCache(User.class)
int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);
```

상세 사용법: [Cache Eviction Guide](./cache-eviction.md#evictcache-annotation)

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
| ModifyingQueryCacheEvictionAspect | @EvictCache 어노테이션 처리 |
| EntityCacheScanner | @Cache 엔티티 스캔 |
| CacheEvictionStrategy | 캐시 무효화 전략 |
| HibernateCacheInitializer | 시작 시 엔티티 스캔 초기화 |

---

## Distributed Cache

분산 캐시 동기화가 필요한 경우, Hibernate의 네이티브 분산 캐시 통합을 사용하세요:

| Provider | 설정 방법 |
|----------|----------|
| Hazelcast | `hibernate-jcache` + Hazelcast JCache provider |
| Infinispan | `hibernate-cache-infinispan` |
| Redis | Redisson Hibernate cache |

이러한 프로바이더들은 클러스터 전체 캐시 무효화를 자동으로 처리합니다.

---

## Related Documents

- [Configuration Guide (설정 가이드)](./configuration.md) - 설정 옵션 및 @Cache 사용법
- [Cache Eviction Guide (캐시 무효화)](./cache-eviction.md) - 수동 제거 및 @EvictCache 사용법
