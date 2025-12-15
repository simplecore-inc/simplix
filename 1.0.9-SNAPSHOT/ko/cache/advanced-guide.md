# 고급 사용 가이드

CacheProvider, 레거시 지원, 마이그레이션, 베스트 프랙티스, 트러블슈팅을 다룹니다.

## Table of Contents

- [CacheProvider](#cacheprovider)
- [CacheManager (Legacy)](#cachemanager-legacy)
- [마이그레이션 가이드](#마이그레이션-가이드)
- [베스트 프랙티스](#베스트-프랙티스)
- [트러블슈팅](#트러블슈팅)
- [성능 최적화](#성능-최적화)

---

## CacheProvider

`CacheProvider`는 simplix-core 모듈에 정의된 SPI 인터페이스로, 캐시 모듈과 다른 모듈 간의 브릿지 역할을 합니다.

### 인터페이스

```java
public interface CacheProvider {

    <T> Optional<T> get(String cacheName, Object key, Class<T> type);

    <T> void put(String cacheName, Object key, T value);

    <T> void put(String cacheName, Object key, T value, Duration ttl);

    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type);

    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl);

    void evict(String cacheName, Object key);

    void clear(String cacheName);

    boolean isAvailable();
}
```

### CacheProvider vs CacheService

| 특성 | CacheProvider | CacheService |
|------|--------------|--------------|
| 위치 | simplix-core SPI | simplix-cache 모듈 |
| 용도 | 모듈 간 연동 | 애플리케이션 직접 사용 |
| 기능 | 기본 기능 | 전체 기능 (배치, 통계 등) |
| 권장 | 라이브러리 개발 | 애플리케이션 개발 |

### 사용 예시

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CacheProvider cacheProvider;
    private final OrderRepository orderRepository;

    /**
     * CacheProvider 직접 사용
     */
    public Order getOrder(String orderId) {
        // 캐시 조회
        Optional<Order> cached = cacheProvider.get("orders", orderId, Order.class);
        if (cached.isPresent()) {
            log.debug("Cache hit: {}", orderId);
            return cached.get();
        }

        // 캐시 미스 - DB 조회
        log.debug("Cache miss: {}", orderId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 캐시에 저장
        cacheProvider.put("orders", orderId, order, Duration.ofMinutes(30));

        return order;
    }

    /**
     * getOrCompute 사용
     */
    public Order getOrderSimple(String orderId) {
        return cacheProvider.getOrCompute(
            "orders",
            orderId,
            () -> orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)),
            Order.class,
            Duration.ofMinutes(30)
        );
    }

    /**
     * 캐시 가용성 확인
     */
    public Order getOrderWithFallback(String orderId) {
        if (cacheProvider.isAvailable()) {
            return cacheProvider.getOrCompute(
                "orders", orderId,
                () -> loadOrder(orderId),
                Order.class
            );
        } else {
            // 캐시 불가 시 직접 조회
            log.warn("Cache not available, loading from DB directly");
            return loadOrder(orderId);
        }
    }

    private Order loadOrder(String orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
```

### 다른 모듈에서 사용

simplix-core를 의존하는 다른 모듈에서 CacheProvider 활용:

```java
// simplix-auth 모듈에서의 예시
@Service
@RequiredArgsConstructor
public class TokenCacheService {

    private final CacheProvider cacheProvider;

    public void cacheToken(String tokenId, TokenInfo tokenInfo) {
        cacheProvider.put("tokens", tokenId, tokenInfo, Duration.ofHours(1));
    }

    public Optional<TokenInfo> getToken(String tokenId) {
        return cacheProvider.get("tokens", tokenId, TokenInfo.class);
    }

    public void invalidateToken(String tokenId) {
        cacheProvider.evict("tokens", tokenId);
    }
}
```

---

## CacheManager (Legacy)

Spring DI를 사용할 수 없는 레거시 코드에서 정적으로 캐시에 접근합니다.

### 정적 접근

```java
public class LegacyService {

    /**
     * Spring DI 없이 캐시 사용
     */
    public Data getSomeData(String key) {
        CacheManager cacheManager = CacheManager.getInstance();

        return cacheManager.getOrCompute(
            "legacyCache",
            key,
            () -> loadFromDatabase(key),
            Data.class
        );
    }

    /**
     * TTL 지정
     */
    public Config getConfig(String key) {
        CacheManager cacheManager = CacheManager.getInstance();

        return cacheManager.getOrCompute(
            "configs",
            key,
            () -> loadConfig(key),
            Config.class,
            Duration.ofHours(1)
        );
    }

    /**
     * 캐시 무효화
     */
    public void clearData(String key) {
        CacheManager.getInstance().evict("legacyCache", key);
    }
}
```

### 주의사항

```java
// 주의: CacheManager는 Spring 컨텍스트 초기화 후에만 사용 가능
public class EarlyInitService {

    // 잘못된 사용: 빈 초기화 시점에 호출하면 null 반환 가능
    private final CacheManager cacheManager = CacheManager.getInstance();  // 위험!

    // 올바른 사용: 메서드에서 호출
    public Data getData(String key) {
        return CacheManager.getInstance().getOrCompute(...);  // 안전
    }
}
```

### 레거시 전환 권장

```java
// Before: 레거시 정적 접근
public class OldService {
    public Data getData(String key) {
        return CacheManager.getInstance().getOrCompute(
            "data", key, () -> load(key), Data.class
        );
    }
}

// After: Spring DI 사용 (권장)
@Service
@RequiredArgsConstructor
public class NewService {
    private final CacheService cacheService;

    public Data getData(String key) {
        return cacheService.getOrCompute(
            "data", key, () -> load(key), Data.class
        );
    }
}
```

---

## 마이그레이션 가이드

### Spring @Cacheable에서 CacheService로

기존 Spring Cache 어노테이션을 CacheService로 전환:

**Before:**
```java
@Service
public class UserService {

    @Cacheable(value = "users", key = "#userId")
    public User getUser(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @CacheEvict(value = "users", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearCache() {
        log.info("Cache cleared");
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final CacheService cacheService;
    private final UserRepository userRepository;

    public User getUser(String userId) {
        return cacheService.getOrCompute(
            "users",
            userId,
            () -> userRepository.findById(userId).orElse(null),
            User.class
        );
    }

    public User updateUser(User user) {
        User saved = userRepository.save(user);
        cacheService.evict("users", user.getId());
        return saved;
    }

    public void clearCache() {
        cacheService.clear("users");
        log.info("Cache cleared");
    }
}
```

### RedisTemplate에서 CacheService로

직접 Redis 사용에서 전환:

**Before:**
```java
@Service
public class DataService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "data:";

    public Data getData(String key) {
        String cacheKey = KEY_PREFIX + key;

        // Redis에서 조회
        Data cached = (Data) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // DB에서 조회
        Data data = fetchFromDatabase(key);
        if (data != null) {
            // Redis에 저장 (1시간 TTL)
            redisTemplate.opsForValue().set(cacheKey, data, 1, TimeUnit.HOURS);
        }

        return data;
    }

    public void deleteData(String key) {
        redisTemplate.delete(KEY_PREFIX + key);
        dataRepository.deleteById(key);
    }

    public void saveData(Data data) {
        dataRepository.save(data);
        redisTemplate.opsForValue().set(KEY_PREFIX + data.getId(), data, 1, TimeUnit.HOURS);
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class DataService {

    private final CacheService cacheService;
    private final DataRepository dataRepository;

    private static final String CACHE_NAME = "data";
    private static final Duration TTL = Duration.ofHours(1);

    public Data getData(String key) {
        return cacheService.getOrCompute(
            CACHE_NAME,
            key,
            () -> fetchFromDatabase(key),
            Data.class,
            TTL
        );
    }

    public void deleteData(String key) {
        dataRepository.deleteById(key);
        cacheService.evict(CACHE_NAME, key);
    }

    public void saveData(Data data) {
        dataRepository.save(data);
        cacheService.put(CACHE_NAME, data.getId(), data, TTL);
    }
}
```

### Caffeine 직접 사용에서 전환

**Before:**
```java
@Service
public class ConfigService {

    private final Cache<String, Config> cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    public Config getConfig(String key) {
        return cache.get(key, k -> loadConfig(k));
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }
}
```

**After:**
```java
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final CacheService cacheService;

    private static final String CACHE_NAME = "configs";
    private static final Duration TTL = Duration.ofMinutes(30);

    public Config getConfig(String key) {
        return cacheService.getOrCompute(
            CACHE_NAME, key,
            () -> loadConfig(key),
            Config.class,
            TTL
        );
    }

    public void invalidate(String key) {
        cacheService.evict(CACHE_NAME, key);
    }
}
```

---

## 베스트 프랙티스

### 1. 캐시 키 설계

```java
// ✔ Good: 명확하고 일관된 네이밍
String cacheKey = "user:" + userId;
String cacheKey = String.format("order:%s:status:%s", orderId, status);
String cacheKey = String.join(":", "product", productId, "variant", variantId);

// ✖ Bad: 불명확하거나 충돌 가능한 키
String cacheKey = userId;                    // 다른 캐시와 충돌 가능
String cacheKey = orderId + status;          // 구분자 없음
String cacheKey = request.toString();        // 예측 불가
```

**권장 패턴:**
```java
@Service
public class CacheKeyBuilder {

    public static String userKey(String userId) {
        return "user:" + userId;
    }

    public static String orderKey(String orderId) {
        return "order:" + orderId;
    }

    public static String userOrdersKey(String userId, OrderStatus status) {
        return String.format("user:%s:orders:%s", userId, status.name());
    }
}
```

### 2. TTL 전략

| 데이터 유형 | 특성 | 권장 TTL |
|------------|------|----------|
| 시스템 설정 | 거의 변경 안됨 | 1-24시간 |
| 사용자 프로필 | 가끔 변경 | 15-60분 |
| 권한 정보 | 보안 민감 | 5-15분 |
| 세션 정보 | 활성 유지 필요 | 15-30분 |
| 검색 결과 | 실시간성 중요 | 1-5분 |
| 기능 플래그 | 즉시 반영 필요 | 10-60초 |

```java
@Component
public class CacheTTLConfig {

    // 중앙 집중식 TTL 관리
    public static final Duration SYSTEM_CONFIG_TTL = Duration.ofHours(24);
    public static final Duration USER_PROFILE_TTL = Duration.ofMinutes(30);
    public static final Duration PERMISSIONS_TTL = Duration.ofMinutes(10);
    public static final Duration SESSION_TTL = Duration.ofMinutes(30);
    public static final Duration SEARCH_TTL = Duration.ofMinutes(3);
    public static final Duration FEATURE_FLAG_TTL = Duration.ofSeconds(30);
}
```

### 3. null 값 처리

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final CacheService cacheService;

    // ✔ Good: null 캐싱 방지 - 예외 발생
    public User getUser(String userId) {
        return cacheService.getOrCompute(
            "users", userId,
            () -> {
                return userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));
            },
            User.class
        );
    }

    // ✔ Good: null 캐싱 방지 - 조건부 캐싱
    public User findUser(String userId) {
        Optional<User> cached = cacheService.get("users", userId, User.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            cacheService.put("users", userId, user);
        }
        return user;
    }

    // ✖ Bad: null이 캐싱되어 계속 null 반환
    public User findUserBad(String userId) {
        return cacheService.getOrCompute(
            "users", userId,
            () -> userRepository.findById(userId).orElse(null),  // null도 캐싱됨
            User.class
        );
    }
}
```

### 4. 캐시 분리

```java
// ✔ Good: 목적별 캐시 분리
@Service
public class UserCacheService {

    private static final String PROFILES_CACHE = "userProfiles";
    private static final String PERMISSIONS_CACHE = "userPermissions";
    private static final String SESSIONS_CACHE = "userSessions";
    private static final String PREFERENCES_CACHE = "userPreferences";

    public UserProfile getProfile(String userId) {
        return cacheService.getOrCompute(PROFILES_CACHE, userId, ...);
    }

    public UserPermissions getPermissions(String userId) {
        return cacheService.getOrCompute(PERMISSIONS_CACHE, userId, ...);
    }
}

// ✖ Bad: 하나의 캐시에 모든 것
@Service
public class UserCacheServiceBad {

    private static final String USERS_CACHE = "users";

    public UserProfile getProfile(String userId) {
        return cacheService.getOrCompute(USERS_CACHE, "profile:" + userId, ...);
    }

    public UserPermissions getPermissions(String userId) {
        return cacheService.getOrCompute(USERS_CACHE, "permissions:" + userId, ...);
    }
}
```

### 5. 캐시 워밍

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmer implements ApplicationRunner {

    private final CacheService cacheService;
    private final ConfigRepository configRepository;
    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        warmupConfigs();
        warmupPopularProducts();
    }

    private void warmupConfigs() {
        log.info("Warming up config cache...");

        List<Config> configs = configRepository.findAll();
        Map<Object, Config> entries = configs.stream()
            .collect(Collectors.toMap(Config::getKey, c -> c));

        cacheService.putAll("configs", entries, Duration.ofHours(24));

        log.info("Config cache warmed up with {} entries", entries.size());
    }

    private void warmupPopularProducts() {
        log.info("Warming up popular products cache...");

        List<Product> products = productRepository.findTop100ByOrderByViewCountDesc();
        Map<Object, Product> entries = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        cacheService.putAll("products", entries, Duration.ofHours(1));

        log.info("Product cache warmed up with {} entries", entries.size());
    }
}
```

### 6. 일관성 유지

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CacheService cacheService;
    private final OrderRepository orderRepository;

    /**
     * 트랜잭션과 캐시 무효화를 함께 처리
     */
    @Transactional
    public Order updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);

        // 트랜잭션 커밋 후 캐시 무효화
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.evict("orders", orderId);
                    cacheService.evict("userOrders", order.getUserId());
                    log.debug("Cache invalidated after commit: order={}", orderId);
                }
            }
        );

        return saved;
    }
}
```

---

## 트러블슈팅

### 캐시가 작동하지 않음

**증상:** 매번 DB 조회가 발생함

**체크리스트:**
1. 캐시 모듈 의존성 확인
2. 설정 확인: `simplix.cache.enabled=true`
3. 디버그 로깅 활성화
4. 헬스 체크 확인

```yaml
# 디버그 로깅 활성화
logging:
  level:
    dev.simplecore.simplix.cache: DEBUG
```

```bash
# 헬스 체크
curl http://localhost:8080/actuator/health/cache
```

**예상 응답:**
```json
{
  "status": "UP",
  "details": {
    "strategy": "LocalCacheStrategy",
    "available": true
  }
}
```

### Redis 연결 실패

**증상:** 캐시가 Local로 폴백됨

**체크리스트:**
1. Redis 서버 실행 확인
2. 연결 설정 확인
3. 네트워크/방화벽 확인

```bash
# Redis 연결 테스트
redis-cli -h localhost -p 6379 ping
# 예상: PONG

# 인증이 필요한 경우
redis-cli -h localhost -p 6379 -a your_password ping
```

**설정 확인:**
```yaml
simplix:
  cache:
    mode: redis

spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
```

### 높은 메모리 사용량

**증상:** OutOfMemoryError 또는 높은 힙 사용량

**해결 방법:**

1. **캐시 크기 제한**
```yaml
simplix:
  cache:
    cache-configs:
      largeObjects:
        ttl-seconds: 300  # 짧은 TTL
```

2. **큰 객체 캐싱 검토**
```java
// 큰 객체는 필요한 필드만 캐싱
public class UserSummary {  // User 대신 경량 객체
    private String id;
    private String name;
    // 대용량 필드 제외
}
```

3. **Redis 사용 고려**
```yaml
simplix:
  cache:
    mode: redis  # Local 대신 Redis 사용
```

### 낮은 캐시 히트율

**증상:** 통계에서 히트율 < 50%

**원인 분석:**

```java
@Scheduled(fixedDelay = 60000)
public void analyzeCachePerformance() {
    CacheStatistics stats = cacheService.getStatistics("users");

    long totalRequests = stats.hits() + stats.misses();
    if (totalRequests > 100 && stats.hitRate() < 0.5) {
        log.warn("Low hit rate detected: {}%", stats.hitRate() * 100);
        log.warn("  - Hits: {}", stats.hits());
        log.warn("  - Misses: {}", stats.misses());
        log.warn("  - Evictions: {}", stats.evictions());
        log.warn("  - Size: {}", stats.size());

        // 제거가 많으면 TTL 또는 크기 문제
        if (stats.evictions() > stats.hits()) {
            log.warn("High eviction rate - consider increasing TTL or cache size");
        }
    }
}
```

**해결 방법:**

| 원인 | 해결 |
|------|------|
| TTL이 너무 짧음 | TTL 증가 |
| 키 불일치 | 키 생성 로직 확인 |
| 캐시 워밍 없음 | 시작 시 워밍 추가 |
| 캐시 크기 부족 | max-size 증가 |

### 직렬화 오류

**증상:** Redis 저장/조회 시 예외 발생

**일반적인 오류:**
```
SerializationException: Cannot serialize; nested exception is ...
```

**해결 방법:**

1. **Serializable 구현**
```java
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private LocalDateTime createdAt;
}
```

2. **직렬화 불가 필드 제외**
```java
public class User implements Serializable {
    private String id;
    private String name;

    // 직렬화에서 제외
    private transient InputStream avatar;
    private transient Connection dbConnection;
}
```

3. **Jackson 어노테이션 사용**
```java
@JsonSerialize
@JsonDeserialize
public class User {
    private String id;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonIgnore  // 직렬화 제외
    private String password;
}
```

### 캐시 불일치

**증상:** DB와 캐시 데이터가 다름

**원인:** 트랜잭션 롤백 시 캐시는 이미 업데이트됨

**해결:**
```java
@Service
@RequiredArgsConstructor
public class SafeUpdateService {

    private final CacheService cacheService;

    /**
     * 트랜잭션 커밋 후 캐시 업데이트
     */
    @Transactional
    public User updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.update(request);
        User saved = userRepository.save(user);

        // 트랜잭션 커밋 후에만 캐시 업데이트
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.put("users", userId, saved);
                }
            }
        );

        return saved;
    }

    /**
     * 또는 캐시 무효화 (더 안전)
     */
    @Transactional
    public User updateUserSafe(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.update(request);
        User saved = userRepository.save(user);

        // 무효화는 더 안전 - 다음 조회 시 최신 데이터 로드
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.evict("users", userId);
                }
            }
        );

        return saved;
    }
}
```

---

## 성능 최적화

### 1. 히트율 목표

| 히트율 | 상태 | 조치 |
|--------|------|------|
| 90% 이상 | 우수 | 유지 |
| 80-90% | 양호 | 모니터링 |
| 50-80% | 개선 필요 | TTL/워밍 검토 |
| 50% 미만 | 심각 | 즉시 분석 필요 |

### 2. 배치 작업 활용

```java
// ✔ Good: 배치 저장
Map<Object, Product> products = loadProducts();
cacheService.putAll("products", products);

// ✖ Bad: 개별 저장
for (Product p : products) {
    cacheService.put("products", p.getId(), p);  // N번의 작업
}
```

### 3. 비동기 캐시 워밍

```java
@Service
@RequiredArgsConstructor
public class AsyncCacheService {

    private final CacheService cacheService;
    private final ProductRepository productRepository;

    /**
     * 비동기 캐시 워밍
     */
    @Async
    public CompletableFuture<Void> warmupProductCacheAsync() {
        return CompletableFuture.runAsync(() -> {
            List<Product> products = productRepository.findPopular(1000);

            Map<Object, Product> entries = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

            cacheService.putAll("products", entries);

            log.info("Async cache warmup completed: {} products", entries.size());
        });
    }

    /**
     * 백그라운드 캐시 갱신
     */
    @Scheduled(fixedDelay = 300000)  // 5분마다
    public void refreshHotData() {
        CompletableFuture.runAsync(() -> {
            List<String> hotKeys = getHotKeys();

            for (String key : hotKeys) {
                try {
                    Product product = productRepository.findById(key).orElse(null);
                    if (product != null) {
                        cacheService.put("products", key, product);
                    }
                } catch (Exception e) {
                    log.warn("Failed to refresh cache for key: {}", key, e);
                }
            }
        });
    }
}
```

### 4. 캐시 크기 최적화

```yaml
simplix:
  cache:
    cache-configs:
      # 자주 사용, 작은 데이터: 큰 크기
      users:
        ttl-seconds: 1800
        # max-size: 10000 (Local 캐시용)

      # 가끔 사용, 큰 데이터: 작은 크기
      reports:
        ttl-seconds: 300
        # max-size: 100

      # 많이 사용, 중간 데이터
      products:
        ttl-seconds: 3600
        # max-size: 5000
```

### 5. 조건부 캐싱

```java
@Service
public class SmartCacheService {

    /**
     * 크기에 따른 조건부 캐싱
     */
    public Document getDocument(String docId) {
        Optional<Document> cached = cacheService.get("documents", docId, Document.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Document doc = documentRepository.findById(docId).orElseThrow();

        // 1MB 이하만 캐싱
        if (doc.getSize() <= 1_048_576) {
            cacheService.put("documents", docId, doc);
        } else {
            log.debug("Document too large to cache: {} bytes", doc.getSize());
        }

        return doc;
    }

    /**
     * 접근 빈도에 따른 캐싱
     */
    public Data getData(String key, int accessCount) {
        // 자주 접근하는 데이터만 캐싱
        if (accessCount > 10) {
            return cacheService.getOrCompute(
                "frequentData", key, () -> loadData(key), Data.class
            );
        }

        // 드물게 접근하는 데이터는 직접 조회
        return loadData(key);
    }
}
```

---

## Related Documents

- [Overview](ko/cache/overview.md) - 모듈 개요 및 아키텍처
- [CacheService Guide](ko/cache/cacheservice-guide.md) - CacheService 사용법
- [Spring Cache Guide](ko/cache/spring-cache-guide.md) - @Cacheable 어노테이션 사용법
