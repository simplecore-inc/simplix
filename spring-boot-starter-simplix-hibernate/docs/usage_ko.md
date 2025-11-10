# 사용 방법

## 시작하기

### 1. 의존성 추가

`build.gradle`에 모듈 의존성을 추가합니다:

```gradle
dependencies {
    implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-hibernate:${simplixVersion}'
}
```

### 2. Hibernate 2차 캐시 활성화

`application.yml`에서 Hibernate 2차 캐시를 활성화합니다:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region.factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
```

### 3. 엔티티에 캐시 어노테이션 추가

캐시할 엔티티에 `@Cache` 어노테이션을 추가합니다:

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Product {
    @Id
    private Long id;
    private String name;
    private BigDecimal price;
    // ...
}
```

**이제 완료되었습니다!** 모듈이 자동으로 캐시를 관리합니다.

## 설정 옵션

### 기본 설정 (설정 파일 불필요)

모듈은 기본적으로 다음과 같이 작동합니다:
- 자동 활성화
- 최적의 캐시 프로바이더 자동 선택
- 모든 캐시 무효화 메커니즘 활성화

### 비활성화

필요한 경우 모듈을 완전히 비활성화할 수 있습니다:

```yaml
simplix:
  hibernate:
    cache:
      disabled: true
```

### 고급 설정 (선택사항)

특정 프로바이더를 강제로 사용하려면:

```yaml
simplix:
  hibernate:
    cache:
      provider:
        type: redis  # redis, hazelcast, infinispan, local 중 선택
```

Redis 관련 설정:

```yaml
simplix:
  hibernate:
    cache:
      redis:
        channel: "my-cache-sync"  # Redis Pub/Sub 채널명
        key-prefix: "myapp:cache:" # 캐시 키 접두어
```

## 캐시 전략

### CacheConcurrencyStrategy 선택

| 전략 | 설명 | 사용 시나리오 |
|-----|------|-------------|
| `READ_ONLY` | 읽기 전용 | 변경되지 않는 참조 데이터 |
| `NONSTRICT_READ_WRITE` | 비엄격 읽기/쓰기 | 가끔 변경되는 데이터 |
| `READ_WRITE` | 읽기/쓰기 | 자주 변경되는 데이터 |
| `TRANSACTIONAL` | 트랜잭션 | 강력한 일관성이 필요한 데이터 |

### 캐시 리전 설정

엔티티별로 캐시 리전을 지정할 수 있습니다:

```java
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "user-cache")
public class User {
    // ...
}
```

## 쿼리 캐시 사용

### Repository에서 쿼리 캐시

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @QueryHints({
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "org.hibernate.cacheRegion", value = "query.user.findByEmail")
    })
    Optional<User> findByEmail(String email);
}
```

### JPQL에서 쿼리 캐시

```java
@Query("SELECT u FROM User u WHERE u.active = true")
@QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
List<User> findActiveUsers();
```

## 프로그래밍 방식으로 캐시 제어

### 캐시 매니저 주입

```java
@Service
public class MyService {

    @Autowired
    private HibernateCacheManager cacheManager;

    public void clearUserCache(Long userId) {
        // 특정 엔티티 캐시 제거
        cacheManager.evictEntity(User.class, userId);
    }

    public void clearAllUserCache() {
        // User 엔티티 전체 캐시 제거
        cacheManager.evictEntityCache(User.class);
    }

    public void clearQueryCache() {
        // 특정 쿼리 캐시 리전 제거
        cacheManager.evictQueryRegion("query.user.findByEmail");
    }
}
```

## 분산 환경 설정

### Redis를 사용한 분산 캐시

1. Redis 의존성 추가:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

2. Redis 연결 설정:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: mypassword  # 선택사항
```

모듈이 자동으로 Redis를 감지하고 분산 캐시 동기화를 활성화합니다.

### Hazelcast를 사용한 분산 캐시

1. Hazelcast 의존성 추가:

```gradle
implementation 'com.hazelcast:hazelcast:5.3.6'
implementation 'com.hazelcast:hazelcast-spring:5.3.6'
```

2. Hazelcast 설정 (선택사항):

```yaml
hazelcast:
  network:
    join:
      multicast:
        enabled: true
```

## 모니터링 및 디버깅

### 로그 레벨 설정

상세한 캐시 작업 로그를 보려면:

```yaml
logging:
  level:
    dev.simplecore.hibernate.cache: DEBUG  # 라이브러리 패키지에 맞게 조정
```

### 캐시 통계 확인

```java
@RestController
public class CacheStatsController {

    @Autowired
    private CacheProviderFactory providerFactory;

    @GetMapping("/cache/stats")
    public Map<String, CacheProvider.CacheProviderStats> getCacheStats() {
        return providerFactory.getAllStats();
    }
}
```

## 성능 튜닝

### 1. 적절한 TTL 설정

```yaml
simplix:
  hibernate:
    cache:
      provider:
        default-ttl: 3600  # 1시간 (초 단위)
```

### 2. 캐시 크기 제한

```yaml
simplix:
  hibernate:
    cache:
      provider:
        max-entries: 10000  # 최대 엔트리 수
```

### 3. 리전별 세부 설정

```yaml
simplix:
  hibernate:
    cache:
      regions:
        user-cache:
          ttl: 1800  # 30분
          max-entries: 5000
        product-cache:
          ttl: 7200  # 2시간
          max-entries: 10000
```

## 주의사항

1. **캐시 일관성**: 분산 환경에서는 최종 일관성(eventual consistency)을 보장합니다.
2. **메모리 사용량**: 캐시 크기를 적절히 제한하여 메모리 부족을 방지하세요.
3. **네트워크 지연**: 분산 캐시 사용 시 네트워크 지연을 고려하세요.
4. **보안**: 민감한 데이터는 캐시하지 않거나 암호화를 적용하세요.