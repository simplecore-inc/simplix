# SimpliX Hibernate Cache Module

Spring Boot 애플리케이션을 위한 Hibernate 2nd-level 캐시 관리 확장 모듈입니다.

## Features

- **@Modifying 쿼리 캐시 관리** - @EvictCache 어노테이션으로 bulk 연산 시 캐시 무효화
- **수동 캐시 제거 API** - HibernateCacheManager를 통한 프로그래밍 방식 캐시 관리
- **트랜잭션 인식** - 커밋 후에만 캐시 제거 (롤백 시 캐시 유지)

## Hibernate Native Cache Management

Hibernate 2nd-level 캐시는 `save()`, `delete()` 등의 기본 EntityManager 작업에 대해 **자동으로 캐시를 관리**합니다. SimpliX Hibernate 모듈은 이러한 네이티브 기능을 대체하지 않고, **@Modifying 쿼리에 대한 보완적 지원**을 제공합니다.

### Hibernate가 자동으로 처리하는 것
- `entityManager.persist()` / `repository.save()` - 캐시 자동 업데이트
- `entityManager.remove()` / `repository.delete()` - 캐시 자동 제거
- 엔티티 수정 후 flush - 캐시 자동 동기화

### SimpliX가 필요한 경우
- `@Modifying` 쿼리 - Hibernate 엔티티 이벤트를 우회하므로 명시적 캐시 무효화 필요
- 수동 캐시 제어 - 특정 시점에 캐시를 명시적으로 제거해야 할 때

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-hibernate'

    // EhCache (권장)
    implementation 'org.hibernate.orm:hibernate-jcache'
    implementation 'org.ehcache:ehcache'
}
```

### 2. Entity 설정

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
}
```

### 3. @Modifying 쿼리에 @EvictCache 적용

```java
public interface UserRepository extends JpaRepository<User, Long> {

    // @Modifying 쿼리는 Hibernate 캐시 이벤트를 발생시키지 않음
    // @EvictCache로 명시적 캐시 무효화 지정
    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
    @EvictCache(User.class)
    int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);

    @Modifying
    @Query("DELETE FROM User u WHERE u.deletedAt < :date")
    @EvictCache(User.class)
    int deleteOldUsers(@Param("date") LocalDateTime date);

    // 여러 엔티티 캐시 무효화
    @Modifying
    @Query("UPDATE User u SET u.status = :status")
    @EvictCache({User.class, UserProfile.class})
    int updateUserStatus(@Param("status") Status status);
}
```

## @EvictCache Annotation

`@Modifying` 쿼리에서 캐시를 무효화할 엔티티를 명시합니다.

### 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `value` | `Class<?>[]` | (필수) | 무효화할 엔티티 클래스들 |
| `regions` | `String[]` | `{}` | 커스텀 캐시 리전명 |
| `evictQueryCache` | `boolean` | `true` | 쿼리 캐시도 함께 무효화할지 여부 |

### 사용 예제

```java
// 기본 사용
@Modifying
@Query("UPDATE User u SET u.email = :email WHERE u.id = :id")
@EvictCache(User.class)
int updateEmail(@Param("id") Long id, @Param("email") String email);

// 쿼리 캐시 무효화 비활성화
@Modifying
@Query("UPDATE User u SET u.lastLoginDate = :date WHERE u.id = :id")
@EvictCache(value = User.class, evictQueryCache = false)
int updateLastLogin(@Param("id") Long id, @Param("date") LocalDateTime date);

// 커스텀 리전 지정
@Modifying
@Query("UPDATE User u SET u.status = :status")
@EvictCache(value = User.class, regions = "user-status-region")
int updateStatus(@Param("status") Status status);
```

## Manual Cache Eviction

`HibernateCacheManager`를 통해 프로그래밍 방식으로 캐시를 제어할 수 있습니다.

```java
@Service
@RequiredArgsConstructor
public class CacheService {
    private final HibernateCacheManager cacheManager;

    public void evictUser(Long userId) {
        cacheManager.evictEntity(User.class, userId);
    }

    public void evictAllUsers() {
        cacheManager.evictEntityCache(User.class);
    }

    public void evictQueryCache() {
        cacheManager.evictQueryRegion("default");
    }

    public void evictAll() {
        cacheManager.evictAll();
    }

    public boolean isCached(Long userId) {
        return cacheManager.contains(User.class, userId);
    }
}
```

## Configuration

```yaml
simplix:
  hibernate:
    cache:
      disabled: false                    # 모듈 비활성화
      query-cache-auto-eviction: true    # 쿼리 캐시 자동 제거
      scan-packages:                     # @Cache 엔티티 스캔 패키지
        - com.example.domain
```

## Distributed Cache

분산 캐시 동기화가 필요한 경우, Hibernate의 네이티브 분산 캐시 통합을 사용하세요:

- **Hazelcast**: `hibernate-jcache` + Hazelcast JCache provider
- **Infinispan**: `hibernate-cache-infinispan`
- **Redis**: Redisson Hibernate cache

이러한 프로바이더들은 클러스터 전체 캐시 무효화를 자동으로 처리합니다.

## Disabling

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

## Documentation

- [Overview (아키텍처 상세)](docs/ko/overview.md)
- [Configuration Guide (설정 가이드)](docs/ko/configuration.md)
- [Cache Eviction Guide (캐시 무효화)](docs/ko/cache-eviction.md)

## Requirements

- Spring Boot 3.x
- Hibernate 6.x
- Java 17+

## License

SimpleCORE License 1.0 (SCL-1.0)
