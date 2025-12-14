# Configuration Guide

SimpliX Hibernate Cache 모듈 설정 가이드입니다.

## Configuration Properties

### 기본 설정 (`simplix.hibernate.cache.*`)

```yaml
simplix:
  hibernate:
    cache:
      disabled: false                    # 모듈 비활성화 (기본: false)
      mode: AUTO                         # 캐시 모드
      query-cache-auto-eviction: true    # 쿼리 캐시 자동 제거
      auto-detect-eviction-strategy: true # 전략 자동 감지
      node-id: node-${random.uuid}       # 분산 환경 노드 ID
      scan-packages:                     # @Cache 엔티티 스캔 패키지
        - com.example.domain
```

### 설정 속성 상세

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `disabled` | boolean | `false` | `true`로 설정 시 모듈 완전 비활성화 |
| `mode` | CacheMode | `AUTO` | 캐시 작동 모드 |
| `query-cache-auto-eviction` | boolean | `true` | 엔티티 변경 시 연관된 쿼리 캐시 자동 제거 |
| `auto-detect-eviction-strategy` | boolean | `true` | 최적의 무효화 전략 자동 선택 |
| `node-id` | String | `{hostname}-{uuid}` | 분산 환경에서 노드 식별자 (자동 생성) |
| `scan-packages` | String[] | (전체 스캔) | @Cache 엔티티 스캔 대상 패키지 |

### Redis 설정 (`simplix.hibernate.cache.redis.*`)

```yaml
simplix:
  hibernate:
    cache:
      redis:
        channel: hibernate-cache-sync    # Pub/Sub 채널명
        pub-sub-enabled: true            # Pub/Sub 활성화
        key-prefix: "hibernate:cache:"   # 캐시 키 접두사
        connection-timeout: 5000         # 연결 타임아웃 (ms)
```

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `channel` | String | `hibernate-cache-sync` | Redis Pub/Sub 채널명 |
| `pub-sub-enabled` | boolean | `true` | Redis Pub/Sub 활성화 |
| `key-prefix` | String | `hibernate:cache:` | 캐시 키 접두사 |
| `connection-timeout` | int | `5000` | 연결 타임아웃 (ms) |

### 재시도 설정 (`simplix.hibernate.cache.retry.*`)

분산 캐시 무효화 실패 시 재시도 설정입니다.

```yaml
simplix:
  hibernate:
    cache:
      retry:
        max-attempts: 3    # 최대 재시도 횟수
        delay-ms: 1000     # 재시도 간격 (ms)
```

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `max-attempts` | int | `3` | 최대 재시도 횟수 |
| `delay-ms` | long | `1000` | 재시도 간격 (ms) |

---

## Cache Mode

### 모드 종류

```yaml
simplix:
  hibernate:
    cache:
      mode: AUTO  # AUTO, LOCAL, DISTRIBUTED, HYBRID, DISABLED
```

| Mode | 설명 | 적용 환경 |
|------|------|----------|
| `AUTO` | 프로바이더 기반 자동 감지 | 기본값, 권장 |
| `LOCAL` | 로컬 캐시만 사용 | 단일 인스턴스 |
| `DISTRIBUTED` | 분산 캐시 동기화 | 다중 인스턴스 + Redis |
| `HYBRID` | 로컬 + 분산 혼합 | 고성능 다중 인스턴스 |
| `DISABLED` | 캐시 관리 비활성화 | 디버깅/테스트 |

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

## @CacheEvictionPolicy

SimpliX 전용 어노테이션으로 캐시 무효화 정책을 세밀하게 제어합니다.

### 기본 사용

```java
import dev.simplecore.simplix.hibernate.cache.annotation.CacheEvictionPolicy;

@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy
public class User {
    // ...
}
```

### 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `evictOnChange` | String[] | `{}` | 변경 시 캐시 제거할 필드 목록 |
| `ignoreFields` | String[] | `{"lastModifiedDate", "version", "updatedBy"}` | 캐시 제거 무시 필드 |
| `evictQueryCache` | boolean | `true` | 쿼리 캐시 함께 제거 여부 |
| `strategy` | Class | `DefaultEvictionStrategy` | 커스텀 제거 전략 |

### 특정 필드 변경 시에만 제거

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(evictOnChange = {"name", "email", "status"})
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
    private UserStatus status;
    private LocalDateTime lastLoginDate;  // 변경해도 캐시 유지
}
```

### 특정 필드 변경 무시

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@CacheEvictionPolicy(ignoreFields = {"lastModifiedDate", "version", "viewCount"})
public class Article {
    @Id
    private Long id;
    private String title;
    private String content;
    private int viewCount;           // 변경해도 캐시 유지
    private LocalDateTime lastModifiedDate;  // 변경해도 캐시 유지
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
@CacheEvictionPolicy(strategy = BusinessHoursEvictionStrategy.class)
public class Report {
    // ...
}

public class BusinessHoursEvictionStrategy implements CacheEvictionPolicy.EvictionStrategy {
    @Override
    public boolean shouldEvict(Object entity, String[] dirtyFields) {
        // 업무 시간에만 캐시 제거
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 0)) &&
               now.isBefore(LocalTime.of(18, 0));
    }
}
```

---

## @EvictCache 어노테이션

`@Modifying` 쿼리에서 캐시 무효화 대상을 지정합니다.

### 자동 감지 (권장)

SimpliX는 `@Query` 어노테이션의 JPQL을 파싱하여 엔티티를 자동으로 감지합니다. 대부분의 경우 `@EvictCache`가 불필요합니다:

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
    int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);
    // @EvictCache 불필요 - "User"가 JPQL에서 자동 추출됨

    @Modifying
    @Query("DELETE FROM User u WHERE u.deletedAt < :date")
    int deleteOldUsers(@Param("date") LocalDateTime date);
    // @EvictCache 불필요 - "User"가 자동 추출됨
}
```

### 명시적 지정

다음 경우에 `@EvictCache`를 사용하세요:
- 여러 엔티티를 함께 무효화해야 할 때
- 자동 감지가 실패하는 복잡한 쿼리
- 특정 캐시 리전을 지정할 때

```java
import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;

public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.status = :status")
    @EvictCache({User.class, UserProfile.class})  // 여러 엔티티 명시
    int updateUserStatus(@Param("status") Status status);

    @Modifying
    @Query("DELETE FROM User u WHERE u.orgId = :orgId")
    @EvictCache(value = User.class, regions = {"users", "user-queries"})  // 리전 지정
    int deleteByOrgId(@Param("orgId") Long orgId);
}
```

### @EvictCache 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|-------|------|
| `value` | Class<?>[] | 필수 | 캐시 무효화 대상 엔티티 클래스 |
| `regions` | String[] | `{}` | 추가 캐시 리전 (비어있으면 기본 리전) |
| `evictQueryCache` | boolean | `true` | 연관 쿼리 캐시 함께 제거 여부 |

### @EvictCache 사용 예시

```java
// 쿼리 캐시 제거 비활성화 (엔티티 캐시만 제거)
@Modifying
@Query("UPDATE Config c SET c.value = :value WHERE c.key = :key")
@EvictCache(value = Config.class, evictQueryCache = false)
int updateConfig(@Param("key") String key, @Param("value") String value);
```

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

### 쿼리 캐시 리전 지정

```java
@Query("SELECT u FROM User u WHERE u.status = :status")
@QueryHints(@QueryHint(name = "org.hibernate.cacheRegion", value = "user-queries"))
List<User> findByStatus(@Param("status") UserStatus status);
```

---

## 환경별 설정 예제

### 개발 환경

```yaml
simplix:
  hibernate:
    cache:
      mode: LOCAL
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
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

### 스테이징 환경

```yaml
simplix:
  hibernate:
    cache:
      mode: AUTO
      node-id: staging-${random.uuid}
      redis:
        channel: staging-cache-sync
```

### 운영 환경 (다중 인스턴스)

```yaml
simplix:
  hibernate:
    cache:
      mode: DISTRIBUTED
      node-id: ${HOSTNAME}-${random.uuid}
      query-cache-auto-eviction: true
      redis:
        channel: prod-hibernate-cache-sync
        pub-sub-enabled: true

spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
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
    implementation 'org.ehcache:ehcache:3.10.8'
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
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
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

## 모듈 비활성화

전체 모듈을 비활성화하려면:

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

비활성화 시:
- 모든 자동 캐시 관리 중단
- HibernateIntegrator 이벤트 처리 비활성화
- AutoCacheEvictionAspect 비활성화
- ModifyingQueryCacheEvictionAspect 비활성화
- EvictionMetrics 비수집

---

## Related Documents

- [Overview (아키텍처 상세)](./overview.md) - 모듈 구조 및 컴포넌트
- [Cache Eviction Guide (캐시 무효화)](./cache-eviction.md) - 수동 제거 및 재시도
- [Monitoring Guide (모니터링)](./monitoring.md) - 메트릭, Actuator, 트러블슈팅
