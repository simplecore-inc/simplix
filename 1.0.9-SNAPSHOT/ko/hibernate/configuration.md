# Configuration Guide

SimpliX Hibernate Cache 모듈 설정 가이드입니다.

## Configuration Properties

### 기본 설정 (`simplix.hibernate.cache.*`)

```yaml
simplix:
  hibernate:
    cache:
      disabled: false                    # 모듈 비활성화 (기본: false)
      query-cache-auto-eviction: true    # 쿼리 캐시 자동 제거
      scan-packages:                     # @Cache 엔티티 스캔 패키지
        - com.example.domain
```

### 설정 속성 상세

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `disabled` | boolean | `false` | `true`로 설정 시 모듈 완전 비활성화 |
| `query-cache-auto-eviction` | boolean | `true` | 엔티티 변경 시 연관된 쿼리 캐시 자동 제거 |
| `scan-packages` | String[] | (전체 스캔) | @Cache 엔티티 스캔 대상 패키지. 미지정 시 전체 클래스패스 스캔 (성능 영향 가능) |

---

## Entity 캐싱 설정

### @Cache 어노테이션

Hibernate의 `@Cache` 어노테이션으로 2nd-level 캐시를 활성화합니다.

```java
import jakarta.persistence.Entity;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
}
```

### CacheConcurrencyStrategy

| 전략 | 설명 | 사용 시나리오 |
|------|------|-------------|
| `READ_ONLY` | 읽기 전용, 변경 불가 | 코드 테이블, 상수 데이터 |
| `NONSTRICT_READ_WRITE` | 느슨한 일관성, 빠른 성능 | 드물게 변경되는 데이터 |
| `READ_WRITE` | 엄격한 일관성, 락 사용 | 자주 변경되는 데이터 |
| `TRANSACTIONAL` | JTA 트랜잭션 지원 | 분산 트랜잭션 환경 |

### 캐시 리전 지정

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "users")
public class User {
    // ...
}
```

리전을 지정하면 캐시가 논리적으로 분리되어 관리됩니다.

### 연관 컬렉션 캐싱

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User {
    @Id
    private Long id;

    @OneToMany(mappedBy = "user")
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Order> orders;
}
```

---

## @EvictCache 어노테이션

`@Modifying` 쿼리에서 캐시 무효화 대상을 지정합니다.

### 사용 예시

```java
import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;

public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.status = :status")
    @EvictCache({User.class, UserProfile.class})
    int updateUserStatus(@Param("status") Status status);

    @Modifying
    @Query("DELETE FROM User u WHERE u.orgId = :orgId")
    @EvictCache(value = User.class, regions = {"users", "user-queries"})
    int deleteByOrgId(@Param("orgId") Long orgId);
}
```

### @EvictCache 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `value` | Class<?>[] | 필수 | 캐시 무효화 대상 엔티티 클래스 |
| `regions` | String[] | `{}` | 추가 캐시 리전 (비어있으면 기본 리전) |
| `evictQueryCache` | boolean | `true` | 연관 쿼리 캐시 함께 제거 여부 |

---

## 쿼리 캐시 설정

### 쿼리 캐시 활성화

```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_query_cache: true
          use_second_level_cache: true
```

### @QueryHints 사용

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<User> findByStatus(UserStatus status);

    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "activeUsers")
    })
    List<User> findActiveUsers();
}
```

---

## 환경별 설정 예제

### 개발 환경

```yaml
simplix:
  hibernate:
    cache:
      scan-packages:
        - com.example.domain

spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: jcache
```

### 운영 환경

```yaml
simplix:
  hibernate:
    cache:
      query-cache-auto-eviction: true
      scan-packages:
        - com.example.domain

spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: jcache
```

### 테스트 환경 (캐시 비활성화)

```yaml
simplix:
  hibernate:
    cache:
      disabled: true

spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: false
          use_query_cache: false
```

---

## Hibernate 캐시 설정

### EhCache 설정

**build.gradle:**
```gradle
dependencies {
    implementation 'org.hibernate.orm:hibernate-jcache'
    implementation 'org.ehcache:ehcache'
}
```

**application.yml:**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: jcache
          javax.cache:
            provider: org.ehcache.jsr107.EhcacheCachingProvider
```

### ehcache.xml 예제

```xml
<?xml version="1.0" encoding="UTF-8"?>
<config xmlns="http://www.ehcache.org/v3">
    <cache-template name="default">
        <expiry>
            <ttl unit="minutes">30</ttl>
        </expiry>
        <heap unit="entries">1000</heap>
    </cache-template>

    <cache alias="users" uses-template="default">
        <heap unit="entries">5000</heap>
    </cache>

    <cache alias="default-query-results-region" uses-template="default">
        <expiry>
            <ttl unit="minutes">5</ttl>
        </expiry>
    </cache>
</config>
```

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

## 모듈 비활성화

전체 모듈을 비활성화하려면:

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

비활성화 시:
- ModifyingQueryCacheEvictionAspect 비활성화
- @EvictCache 어노테이션 미적용

---

## Related Documents

- [Overview (아키텍처 상세)](overview.md) - 모듈 구조 및 컴포넌트
- [Cache Eviction Guide (캐시 무효화)](cache-eviction.md) - 수동 제거 및 @EvictCache 사용법
