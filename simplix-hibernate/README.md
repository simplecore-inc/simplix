# SimpliX Hibernate Cache Module

Spring Boot 애플리케이션을 위한 Hibernate 2nd-level 캐시 자동 관리 모듈입니다.

## Features

- ✔ **제로 설정 (Zero Configuration)** - 의존성 추가만으로 자동 활성화
- ✔ **트랜잭션 인식 캐시 무효화** - 커밋 후에만 캐시 제거 (롤백 시 캐시 유지)
- ✔ **자동 캐시 무효화** - 엔티티 변경 시 자동 캐시 제거
- ✔ **EhCache 기반 로컬 캐시** - JCache 통합
- ✔ **분산 캐시 동기화** - Redis, Hazelcast, Infinispan 지원
- ✔ **쿼리 캐시 관리** - 엔티티 변경 시 연관 쿼리 캐시 자동 제거
- ✔ **@Modifying 쿼리 지원** - @EvictCache 어노테이션으로 bulk 연산 캐시 관리
- ✔ **AOP 기반 Repository 인터셉트** - save*, delete* 자동 감지
- ✔ **실패 재시도 메커니즘** - Dead Letter Queue 지원
- ✔ **Micrometer 메트릭** - 캐시 무효화 통계
- ✔ **Actuator 관리 엔드포인트** - 캐시 상태 조회 및 수동 제거

## Why SimpliX Hibernate?

Hibernate 2nd-level 캐시를 사용할 때 가장 큰 문제는 **캐시 무효화 관리**입니다.

### Before (Without SimpliX)

SimpliX Hibernate 없이 캐시를 관리하려면:

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;
    private final SessionFactory sessionFactory;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public User save(User user) {
        User saved = repository.save(user);

        // 1. 수동 캐시 무효화
        sessionFactory.getCache().evict(User.class, saved.getId());

        // 2. 연관된 쿼리 캐시도 수동 제거
        sessionFactory.getCache().evictQueryRegion("default");

        // 3. 분산 환경이면 다른 노드에도 알림 (직접 구현)
        redisTemplate.convertAndSend("cache-eviction",
            "User:" + saved.getId());

        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);

        // 삭제도 동일하게 수동 처리 필요...
        sessionFactory.getCache().evict(User.class, id);
        sessionFactory.getCache().evictQueryRegion("default");
        redisTemplate.convertAndSend("cache-eviction", "User:" + id);
    }
}
```

**문제점:**
- 모든 엔티티, 모든 메서드에 반복적인 캐시 무효화 코드 필요
- 쿼리 캐시 관리 누락 시 stale data 발생
- 분산 환경 동기화 인프라 직접 구축 필요
- 실패 시 재시도 메커니즘 없음
- 모니터링/메트릭 수집 코드 별도 작성

### After (With SimpliX)

SimpliX Hibernate를 사용하면:

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;

    @Transactional
    public User save(User user) {
        return repository.save(user);
        // 캐시 무효화 자동 처리:
        // - 엔티티 캐시 자동 제거
        // - 연관 쿼리 캐시 자동 제거
        // - 분산 환경 자동 브로드캐스트
        // - 실패 시 자동 재시도
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        // 자동 처리됨
    }
}
```

**장점:**
- 비즈니스 로직에만 집중
- 캐시 무효화 로직 중복 제거
- 분산 캐시 동기화 자동화 (Redis, Hazelcast, Infinispan)
- 실패 재시도 및 Dead Letter Queue 내장
- Micrometer 메트릭 자동 수집

## Quick Start

### 1. Dependency

```gradle
dependencies {
    implementation 'dev.simplecore:simplix-hibernate:${version}'

    // EhCache (권장)
    implementation 'org.hibernate.orm:hibernate-jcache'
    implementation 'org.ehcache:ehcache:3.10.8'
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

### 3. 완료

**추가 설정 없이 자동으로 작동합니다:**
- 엔티티 저장/수정/삭제 시 캐시 자동 무효화
- 연관된 쿼리 캐시 자동 제거
- 분산 환경 대비 이벤트 브로드캐스트
- **트랜잭션 롤백 시 캐시 유지** (일관성 보장)

## @Modifying Query Support

`@Modifying` 쿼리는 Hibernate 엔티티 이벤트를 발생시키지 않지만, SimpliX는 JPQL을 자동으로 파싱하여 대상 엔티티를 감지하고 캐시를 무효화합니다.

### 자동 감지 (권장)

```java
public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.role = :role")
    int updateStatusByRole(@Param("status") Status status, @Param("role") Role role);
    // User 캐시 자동 무효화 - @EvictCache 불필요!

    @Modifying
    @Query("DELETE FROM User u WHERE u.deletedAt < :date")
    int deleteOldUsers(@Param("date") LocalDateTime date);
    // User 캐시 자동 무효화
}
```

### 명시적 지정 (@EvictCache)

자동 감지가 어려운 경우나 여러 엔티티를 무효화해야 할 때 `@EvictCache`를 사용하세요:

```java
@Modifying
@Query("UPDATE User u SET u.status = :status")
@EvictCache({User.class, UserProfile.class})  // 여러 엔티티 명시
int updateUserStatus(@Param("status") Status status);
```

## Cache Modes

| Mode | 설명 | 사용 환경 |
|------|------|----------|
| AUTO | 프로바이더 기반 자동 감지 | 기본값 |
| LOCAL | 로컬 캐시만 사용 | 단일 인스턴스 |
| DISTRIBUTED | 분산 캐시 동기화 | 다중 인스턴스 |
| HYBRID | 로컬 + 분산 혼합 | 고성능 다중 인스턴스 |
| DISABLED | 캐시 관리 비활성화 | 디버깅/테스트 |

## Configuration

```yaml
simplix:
  hibernate:
    cache:
      disabled: false                    # 모듈 비활성화
      mode: AUTO                         # 캐시 모드
      query-cache-auto-eviction: true    # 쿼리 캐시 자동 제거
      node-id: node-1                    # 분산 환경 노드 ID
      scan-packages:                     # @Cache 엔티티 스캔 패키지
        - com.example.domain

      # Redis 설정 (분산 캐시)
      redis:
        channel: hibernate-cache-sync
        pub-sub-enabled: true
        key-prefix: "hibernate:cache:"
        connection-timeout: 2000

      # 재시도 설정
      retry:
        max-attempts: 3
        delay-ms: 1000
```

## Distributed Cache Providers

다중 인스턴스 환경에서 캐시 무효화를 동기화하기 위한 분산 캐시 프로바이더를 지원합니다.

| Provider | 의존성 | 설명 |
|----------|--------|------|
| Redis | `spring-boot-starter-data-redis` | Redis Pub/Sub 기반 |
| Hazelcast | `hazelcast` | Hazelcast ITopic 기반 |
| Infinispan | `infinispan-core` | Infinispan 클러스터 기반 |

프로바이더는 클래스패스에 따라 자동으로 선택됩니다 (우선순위: Redis > Hazelcast > Infinispan > Local).

## 수동 캐시 제거

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

    public void evictAll() {
        cacheManager.evictAll();
    }
}
```

## 비활성화

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
- [Monitoring Guide (모니터링)](docs/ko/monitoring.md)

## Requirements

- Spring Boot 3.x
- Hibernate 6.x
- Java 17+

## License

SimpleCORE License 1.0 (SCL-1.0)
