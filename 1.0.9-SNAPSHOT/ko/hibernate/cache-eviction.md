# Cache Eviction Guide

캐시 무효화 메커니즘 및 수동 제거 방법 가이드입니다.

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

## Related Documents

- [Overview (아키텍처 상세)](overview.md) - 모듈 구조 및 컴포넌트
- [Configuration Guide (설정 가이드)](configuration.md) - 설정 옵션 및 @Cache 사용법
- [Distributed Cache](overview.md#distributed-cache) - 분산 캐시 설정
