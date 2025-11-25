# SimpliX Cache 모듈

Spring Boot 애플리케이션을 위한 유연한 전략 기반 캐싱 모듈로, 다양한 캐시 구현체를 지원합니다.

## 중요 아키텍처 변경사항

최신 리팩토링 이후 변경사항:
- **Spring Boot Auto-Configuration**: SPI 방식에서 Spring Boot 자동 구성으로 전환
- **생성자 주입**: 의존성 주입을 위한 생성자 기반 패턴 사용
- **조건부 빈 생성**: Redis 미설치 환경에서도 정상 동작하도록 개선
- **Nested Configuration**: Redis 클래스 로딩 이슈 해결을 위한 분리된 설정

## 주요 기능

- ✔ **다중 캐시 전략**: 로컬(Caffeine), Redis, Hazelcast(예정)
- ✔ **전략 패턴**: 캐시 구현체 간 쉬운 전환
- ✔ **Spring Boot 통합**: 자동 구성 및 IDE 지원
- ✔ **메트릭 및 모니터링**: 내장 헬스체크 및 메트릭 수집
- ✔ **유연한 설정**: 캐시별 TTL 및 크기 설정
- ✔ **다중 인스턴스 지원**: Redis를 통한 분산 캐싱
- ✔ **포괄적인 로깅**: 디버그 및 트레이스 레벨 로깅

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-cache:${simplixVersion}'
}
```

### 2. 캐시 모드 설정

```yaml
# application.yml
simplix:
  cache:
    mode: local  # or 'redis' for distributed caching
```

### 3. 코드에서 사용

```java
@Service
public class MyService {

    @Autowired
    private CacheService cacheService;

    public String getData(String key) {
        return cacheService.getOrCompute(
            "myCache",           // 캐시 이름
            key,                 // 캐시 키
            () -> fetchData(key), // 값 로더
            String.class,        // 값 타입
            Duration.ofHours(1)  // TTL
        );
    }
}
```

## 설정

### 기본 설정

```yaml
simplix:
  cache:
    mode: local                    # 캐시 전략: local, redis
    default-ttl-seconds: 3600      # 기본 TTL (1시간)
    max-size: 10000               # 최대 항목 수 (로컬 캐시만)
    cache-null-values: false      # null 값 캐싱 여부
```

### 캐시별 설정

```yaml
simplix:
  cache:
    cache-configs:
      userPermissions:
        ttl-seconds: 900          # 15분
        max-size: 2000
      siteTimeZones:
        ttl-seconds: 86400        # 24시간
        max-size: 10000
```

### Redis 설정

```yaml
simplix:
  cache:
    mode: redis
    redis:
      key-prefix: "app:cache:"
      command-timeout: 2000

spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD}
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
```

## 캐시 전략

### 로컬 캐시 (Caffeine)

**사용 시기:**
- 단일 인스턴스 애플리케이션
- 개발 환경
- 낮은 지연 시간 요구사항
- 외부 의존성 불필요

**설정:**
```yaml
simplix:
  cache:
    mode: local
    max-size: 10000
```

### Redis 캐시

**사용 시기:**
- 다중 인스턴스 배포
- 운영 환경
- 영구 캐시 필요
- 분산 캐시 요구

**설정:**
```yaml
simplix:
  cache:
    mode: redis
spring:
  data:
    redis:
      host: redis.example.com
      port: 6379
```

## API 레퍼런스

### CacheService 메서드

```java
// 캐시에서 값 가져오기
Optional<T> get(String cacheName, Object key, Class<T> type)

// 캐시에 값 저장
void put(String cacheName, Object key, T value)
void put(String cacheName, Object key, T value, Duration ttl)

// 값이 없으면 계산하여 가져오기
T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type)
T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl)

// 항목 제거
void evict(String cacheName, Object key)
void evictAll(String cacheName, Collection<?> keys)
void clear(String cacheName)
void clearAll()

// 캐시 작업
boolean exists(String cacheName, Object key)
Collection<Object> getKeys(String cacheName)
Map<Object, T> getAll(String cacheName, Class<T> type)
void putAll(String cacheName, Map<Object, T> entries)

// 통계
CacheStatistics getStatistics(String cacheName)
```

## 모니터링

### 헬스 체크

모듈은 헬스 인디케이터를 제공합니다:

```bash
curl http://localhost:8080/actuator/health/cache
```

응답:
```json
{
  "status": "UP",
  "details": {
    "strategy": "RedisCacheStrategy",
    "available": true
  }
}
```

### 메트릭

캐시 메트릭이 자동으로 수집됩니다:

```bash
curl http://localhost:8080/actuator/metrics/cache.hits
```

사용 가능한 메트릭:
- `cache.hits` - 캐시 히트 수
- `cache.misses` - 캐시 미스 수
- `cache.evictions` - 제거된 항목 수
- `cache.hit.ratio` - 히트율 백분율

### 로깅

캐시 작업을 보려면 디버그 로깅을 활성화하세요:

```yaml
logging:
  level:
    dev.simplecore.simplix.cache: DEBUG
```

## 환경 변수

모듈은 환경 변수를 통한 설정을 지원합니다:

| 변수 | 설명 | 기본값 |
|----------|-------------|---------|
| `CACHE_MODE` | 캐시 전략 (local/redis) | local |
| `CACHE_DEFAULT_TTL` | 기본 TTL (초) | 3600 |
| `CACHE_MAX_SIZE` | 최대 캐시 크기 | 10000 |
| `CACHE_NULL_VALUES` | null 값 캐싱 | false |
| `CACHE_METRICS_ENABLED` | 메트릭 활성화 | true |
| `CACHE_REDIS_PREFIX` | Redis 키 접두사 | cache: |
| `REDIS_HOST` | Redis 서버 호스트 | localhost |
| `REDIS_PORT` | Redis 서버 포트 | 6379 |
| `REDIS_PASSWORD` | Redis 비밀번호 | (비어있음) |

## 프로파일

### 개발 프로파일

```yaml
spring:
  profiles:
    active: development

# 자동 사용:
# - 로컬 캐시
# - 5분 TTL
# - 메트릭 비활성화
```

### 운영 프로파일

```yaml
spring:
  profiles:
    active: production

# 자동 사용:
# - Redis 캐시
# - 1시간 TTL
# - 메트릭 활성화
# - Redis SSL
```

### 테스트 프로파일

```yaml
spring:
  profiles:
    active: test

# 자동 사용:
# - 로컬 캐시
# - 1분 TTL
# - 작은 캐시 크기
```

## 예제

### 기본 사용법

```java
@Service
public class UserService {

    @Autowired
    private CacheService cacheService;

    public User getUser(String userId) {
        return cacheService.getOrCompute(
            "users",
            userId,
            () -> userRepository.findById(userId),
            User.class
        );
    }

    public void updateUser(User user) {
        userRepository.save(user);
        cacheService.evict("users", user.getId());
    }
}
```

### 커스텀 TTL

```java
public class ConfigService {

    public Config getConfig(String key) {
        // 5분 동안 캐싱
        return cacheService.getOrCompute(
            "configs",
            key,
            () -> loadConfig(key),
            Config.class,
            Duration.ofMinutes(5)
        );
    }
}
```

### 배치 작업

```java
public class BatchService {

    public Map<String, Data> getAllData(Set<String> keys) {
        Map<String, Data> cached = cacheService.getAll("data", Data.class);

        Set<String> missingKeys = keys.stream()
            .filter(k -> !cached.containsKey(k))
            .collect(Collectors.toSet());

        if (!missingKeys.isEmpty()) {
            Map<String, Data> fetched = fetchData(missingKeys);
            cacheService.putAll("data", fetched);
            cached.putAll(fetched);
        }

        return cached;
    }
}
```

### 캐시 통계

```java
@Component
public class CacheMonitor {

    @Autowired
    private CacheService cacheService;

    @Scheduled(fixedDelay = 60000)
    public void logStatistics() {
        CacheStatistics stats = cacheService.getStatistics("users");

        log.info("캐시 통계 - 히트: {}, 미스: {}, 히트율: {}%",
            stats.hits(),
            stats.misses(),
            stats.hitRate() * 100
        );
    }
}
```

### Spring @Cacheable 어노테이션 사용

SimpliX Cache는 Spring의 표준 캐시 어노테이션을 지원합니다:

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    // 조회 시 자동 캐싱
    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(String productId) {
        // 캐시 미스 시에만 실행됨
        return productRepository.findById(productId).orElse(null);
    }

    // 업데이트 후 캐시 갱신
    @CachePut(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    // 삭제 시 캐시에서 제거
    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(String productId) {
        productRepository.deleteById(productId);
    }

    // 전체 캐시 삭제
    @CacheEvict(value = "products", allEntries = true)
    public void clearAllProducts() {
        log.info("모든 상품 캐시를 삭제합니다");
    }

    // 조건부 캐싱
    @Cacheable(value = "products", key = "#productId", condition = "#productId != null")
    public Product getProductConditional(String productId) {
        return productRepository.findById(productId).orElse(null);
    }

    // 복합 키 사용
    @Cacheable(value = "productsByCategory", key = "#category + '_' + #page")
    public List<Product> getProductsByCategory(String category, int page) {
        return productRepository.findByCategory(category, page);
    }
}
```

### CacheProvider 직접 사용

고급 사용 사례를 위해 `CacheProvider`를 직접 주입할 수 있습니다:

```java
@Service
public class OrderService {

    @Autowired
    private CacheProvider cacheProvider;

    @Autowired
    private OrderRepository orderRepository;

    public Order getOrder(String orderId) {
        String cacheKey = "order:" + orderId;

        // 캐시에서 조회
        Optional<Order> cached = cacheProvider.get("orders", cacheKey, Order.class);
        if (cached.isPresent()) {
            log.debug("캐시 히트: {}", cacheKey);
            return cached.get();
        }

        // 캐시 미스 - DB에서 로드
        log.debug("캐시 미스: {}", cacheKey);
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order != null) {
            // 30분 TTL로 캐시에 저장
            cacheProvider.put("orders", cacheKey, order, Duration.ofMinutes(30));
        }

        return order;
    }

    // CacheProvider를 사용한 복잡한 캐시 로직
    public List<Order> getRecentOrders(String userId, int limit) {
        String cacheKey = "recent_orders:" + userId + ":" + limit;

        return cacheProvider.getOrCompute(
            "orderLists",
            cacheKey,
            () -> orderRepository.findRecentByUserId(userId, limit),
            List.class
        );
    }
}
```

### simplix-core CacheManager 사용 (레거시)

정적 접근이 필요한 경우 `CacheManager`를 사용할 수 있습니다:

```java
public class LegacyService {

    public Data getSomeData(String key) {
        // Spring DI 없이 정적 접근
        CacheManager cacheManager = CacheManager.getInstance();

        return cacheManager.getOrCompute(
            "dataCache",
            key,
            () -> loadFromDatabase(key),
            Data.class
        );
    }

    private Data loadFromDatabase(String key) {
        // 데이터베이스에서 로드하는 로직
        return database.load(key);
    }
}
```

**참고**: 가능하면 Spring의 의존성 주입(`CacheService` 또는 `CacheProvider`)을 사용하는 것을 권장합니다.

## 마이그레이션 가이드

### Spring Cache 어노테이션에서 전환

이전:
```java
@Cacheable(value = "users", key = "#userId")
public User getUser(String userId) {
    return userRepository.findById(userId);
}

@CacheEvict(value = "users", key = "#user.id")
public void updateUser(User user) {
    userRepository.save(user);
}
```

이후:
```java
public User getUser(String userId) {
    return cacheService.getOrCompute(
        "users", userId,
        () -> userRepository.findById(userId),
        User.class
    );
}

public void updateUser(User user) {
    userRepository.save(user);
    cacheService.evict("users", user.getId());
}
```

### 직접 Redis 사용에서 전환

이전:
```java
@Autowired
private RedisTemplate<String, Object> redisTemplate;

public Data getData(String key) {
    Data cached = (Data) redisTemplate.opsForValue().get(key);
    if (cached == null) {
        cached = fetchData(key);
        redisTemplate.opsForValue().set(key, cached, 1, TimeUnit.HOURS);
    }
    return cached;
}
```

이후:
```java
@Autowired
private CacheService cacheService;

public Data getData(String key) {
    return cacheService.getOrCompute(
        "data", key,
        () -> fetchData(key),
        Data.class,
        Duration.ofHours(1)
    );
}
```

## 문제 해결

### 문제: 캐시가 작동하지 않음

1. 캐시 모듈이 클래스패스에 있는지 확인
2. `application.yml`의 설정 확인
3. 캐시 작업을 보려면 디버그 로깅 활성화
4. 헬스 엔드포인트 확인: `/actuator/health/cache`

### 문제: Redis 연결 실패

1. Redis가 실행 중인지 확인: `redis-cli ping`
2. 설정에서 연결 설정 확인
3. 연결 테스트: `redis-cli -h host -p port`
4. 방화벽/보안 그룹 설정 확인

### 문제: 높은 메모리 사용량

1. 로컬 캐시의 `max-size` 감소
2. TTL 값 감소
3. 제거율 모니터링
4. 로컬 캐시 대신 Redis 사용 고려

### 문제: 캐시 미스가 너무 높음

1. 안정적인 데이터의 TTL 증가
2. 시작 시 캐시 사전 준비
3. 키가 일관적인지 확인
4. 히트율이 낮은 캐시 모니터링

## 성능 팁

1. **적절한 TTL 사용**: 신선도와 성능 간 균형
2. **히트율 모니터링**: 80% 이상 히트율 목표
3. **크기 제한**: OOM 방지를 위한 합리적인 최대 크기 설정
4. **키 설계**: 일관되고 예측 가능한 캐시 키 사용
5. **배치 작업**: 대량 작업에 `putAll`과 `getAll` 사용
6. **비동기 로딩**: 비용이 많이 드는 작업에 비동기 캐시 로딩 고려
