# Cache Eviction Guide

캐시 무효화 메커니즘 및 수동 제거 방법 가이드입니다.

## 목차

- [Hibernate Native Cache Management](#hibernate-native-cache-management)
- [@EvictCache Annotation](#evictcache-annotation)
  - [기본 사용](#기본-사용)
  - [@EvictCache 속성](#evictcache-속성)
  - [Operation 타입 자동 감지](#operation-타입-자동-감지)
- [수동 캐시 제거](#수동-캐시-제거)
  - [HibernateCacheManager API](#hibernatecachemanager-api)
  - [API 상세](#api-상세)
- [쿼리 캐시 무효화](#쿼리-캐시-무효화)
- [코드 예제](#코드-예제)
- [EntityCacheScanner API](#entitycachescanner-api)
- [HibernateCacheHolder](#hibernatecacheholder)
- [PendingEviction](#pendingeviction)
- [TransactionAwareCacheEvictionCollector](#transactionawarecacheevictioncollector)
- [PendingEvictionCompletedEvent](#pendingevictioncompletedevent)
- [Related Documents](#related-documents)

---

## Hibernate Native Cache Management

Hibernate 2nd-level 캐시는 기본 EntityManager 작업에 대해 **자동으로 캐시를 관리**합니다:

| 작업 | 캐시 처리 |
|------|----------|
| `repository.save()` | Hibernate가 자동 처리 |
| `repository.delete()` | Hibernate가 자동 처리 |
| `entityManager.persist()` | Hibernate가 자동 처리 |
| `entityManager.remove()` | Hibernate가 자동 처리 |
| **`@Modifying` 쿼리** | **SimpliX @EvictCache 필요** |

---

## @EvictCache Annotation

`@Modifying` 쿼리는 Hibernate 엔티티 이벤트를 발생시키지 않습니다. SimpliX는 `@EvictCache` 어노테이션을 통해 명시적 캐시 무효화를 지원합니다.

### 기본 사용

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // 단일 엔티티 캐시 무효화
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
    @EvictCache(User.class)
    int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);

    // 삭제 쿼리
    @Modifying
    @Query("DELETE FROM User u WHERE u.deletedAt < :date")
    @EvictCache(User.class)
    int deleteOldUsers(@Param("date") LocalDateTime date);

    // 여러 엔티티 캐시 무효화
    @Modifying
    @Query("UPDATE User u SET u.status = :status")
    @EvictCache({User.class, UserProfile.class})
    int updateUserStatus(@Param("status") Status status);

    // 쿼리 캐시 무효화 비활성화
    @Modifying
    @Query("UPDATE User u SET u.lastLoginDate = :date WHERE u.id = :id")
    @EvictCache(value = User.class, evictQueryCache = false)
    int updateLastLogin(@Param("id") Long id, @Param("date") LocalDateTime date);

    // 커스텀 리전 지정
    @Modifying
    @Query("UPDATE User u SET u.email = :email WHERE u.id = :id")
    @EvictCache(value = User.class, regions = "user-profile-region")
    int updateEmail(@Param("id") Long id, @Param("email") String email);
}
```

### @EvictCache 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `value` | `Class<?>[]` | (필수) | 무효화할 엔티티 클래스들 |
| `regions` | `String[]` | `{}` | 커스텀 캐시 리전명 (엔티티와 1:1 매핑) |
| `evictQueryCache` | `boolean` | `true` | 쿼리 캐시도 함께 무효화할지 여부 |

### Operation 타입 자동 감지

메서드명에 따라 `BULK_UPDATE` 또는 `BULK_DELETE`가 자동으로 결정됩니다:
- `delete`, `remove` 포함 -> `BULK_DELETE`
- 그 외 -> `BULK_UPDATE`

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

## 쿼리 캐시 무효화

### 자동 무효화

`@EvictCache(evictQueryCache = true)` (기본값) 설정 시 연관된 쿼리 캐시가 자동으로 무효화됩니다.

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

---

## EntityCacheScanner API

캐시된 엔티티를 조회하는 유틸리티입니다.

### 엔티티 조회

```java
@Service
@RequiredArgsConstructor
public class CacheInspectorService {
    private final EntityCacheScanner cacheScanner;

    // 단순 클래스명으로 엔티티 조회 (대소문자 무시)
    public Class<?> findEntity(String simpleName) {
        return cacheScanner.findBySimpleName(simpleName)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + simpleName));
    }

    // 캐시된 모든 엔티티 목록
    public Set<Class<?>> getAllCachedEntities() {
        return cacheScanner.getCachedEntities();
    }

    // 모든 캐시 리전 목록
    public Set<String> getAllRegions() {
        return cacheScanner.getCacheRegions();
    }

    // 특정 엔티티 캐시 여부 확인
    public boolean isEntityCached(Class<?> entityClass) {
        return cacheScanner.isCached(entityClass);
    }
}
```

### API 상세

| 메서드 | 설명 |
|--------|------|
| `findBySimpleName(name)` | 단순 클래스명으로 엔티티 검색 (대소문자 무시) |
| `getCachedEntities()` | 캐시된 모든 엔티티 클래스 반환 |
| `getCacheRegions()` | 모든 캐시 리전명 반환 |
| `isCached(Class)` | 특정 엔티티의 캐시 여부 확인 |

---

## HibernateCacheHolder

캐시 인스턴스에 대한 전역 접근을 제공하는 정적 유틸리티입니다.

```java
// 현재 캐시 인스턴스 조회
Cache cache = HibernateCacheHolder.getCache();

// 캐시 초기화 여부 확인
boolean isInitialized = !HibernateCacheHolder.isReset();
```

### 특징

- Thread-safe: `AtomicReference`를 사용한 lock-free 초기화
- `setCache()`는 한 번만 설정 가능 (compare-and-set)
- 주로 내부용이지만, 고급 사용자가 네이티브 Hibernate 캐시에 직접 접근할 때 유용

---

## PendingEviction

### EvictionOperation Enum

트랜잭션 중 발생한 캐시 무효화 작업의 유형입니다:

| 값 | 설명 |
|----|------|
| `INSERT` | 엔티티 삽입 |
| `UPDATE` | 단일 엔티티 업데이트 |
| `DELETE` | 단일 엔티티 삭제 |
| `BULK_UPDATE` | `@Modifying` UPDATE 쿼리 |
| `BULK_DELETE` | `@Modifying` DELETE 쿼리 |

### 사용 예시

```java
@EventListener
public void onEvictionCompleted(PendingEvictionCompletedEvent event) {
    for (PendingEviction eviction : event.getEvictions()) {
        String entityName = eviction.getEntityClassName();
        PendingEviction.EvictionOperation operation = eviction.getOperation();
        long timestamp = eviction.getTimestamp();

        log.info("Eviction completed: {} {} at {}",
                operation, entityName, timestamp);
    }
}
```

### 팩토리 메서드

```java
// 기본 생성
PendingEviction.of(User.class, userId, EvictionOperation.UPDATE);

// 쿼리 캐시 제어 포함
PendingEviction.of(User.class, null, EvictionOperation.BULK_UPDATE, true);
```

---

## TransactionAwareCacheEvictionCollector

### 디버그 API

트랜잭션 내 보류 중인 eviction 상태를 확인할 수 있습니다:

```java
@Service
@RequiredArgsConstructor
public class CacheDebugService {
    private final TransactionAwareCacheEvictionCollector collector;

    @Transactional
    public void debugPendingEvictions() {
        // 현재 트랜잭션의 보류 중인 eviction 수
        int pendingCount = collector.getPendingCount();
        log.debug("Pending evictions: {}", pendingCount);

        // 보류 중인 eviction 존재 여부
        boolean hasPending = collector.hasPendingEvictions();
        log.debug("Has pending: {}", hasPending);
    }
}
```

### 상수

| 상수 | 값 | 설명 |
|------|----|------|
| `MAX_PENDING_EVICTIONS` | 10000 | 트랜잭션 당 최대 보류 eviction 수 (OOM 방지) |
| `MAX_PUBLISH_RETRY_ATTEMPTS` | 2 | 이벤트 발행 재시도 횟수 |

### 비트랜잭션 환경

트랜잭션 컨텍스트가 없는 경우, eviction이 즉시 실행됩니다:

```java
// 트랜잭션 없이 호출 시 -> 즉시 캐시 무효화
cacheManager.evictEntity(User.class, userId);
```

---

## PendingEvictionCompletedEvent

트랜잭션 커밋 후 발행되는 이벤트입니다.

```java
@EventListener
public void onEvictionCompleted(PendingEvictionCompletedEvent event) {
    // 완료된 eviction 수
    int count = event.getEvictionCount();

    // 모든 eviction 목록 (불변, 방어 복사본)
    List<PendingEviction> evictions = event.getEvictions();

    log.info("Evicted {} cache entries after transaction commit", count);
}
```

### 불변성 보장

- `getEvictions()`는 원본의 방어 복사본을 반환
- `Collections.unmodifiableList()`로 래핑되어 수정 불가

---

## Related Documents

- [Overview (아키텍처 상세)](ko/hibernate/overview.md) - 모듈 구조 및 컴포넌트
- [Configuration Guide (설정 가이드)](ko/hibernate/configuration.md) - 설정 옵션 및 @Cache 사용법
- [Distributed Cache](ko/hibernate/overview.md#distributed-cache) - 분산 캐시 설정
