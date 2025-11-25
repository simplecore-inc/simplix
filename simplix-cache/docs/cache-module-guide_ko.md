# SimpliX 캐시 모듈 가이드

## 목차

1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [설치 및 설정](#설치-및-설정)
4. [통합 방법](#통합-방법)
5. [캐시 전략](#캐시-전략)
6. [고급 기능](#고급-기능)
7. [모니터링 및 운영](#모니터링-및-운영)
8. [성능 최적화](#성능-최적화)
9. [문제 해결](#문제-해결)
10. [마이그레이션 가이드](#마이그레이션-가이드)

## 개요

SimpliX 캐시 모듈은 Spring Boot 애플리케이션을 위한 포괄적인 캐싱 솔루션입니다. 전략 패턴을 사용하여 다양한 캐시 구현체를 지원하며, 런타임에 쉽게 전환할 수 있습니다.

### 핵심 특징

- **플러그형 아키텍처**: 전략 패턴을 통한 캐시 구현체 교체
- **다중 백엔드 지원**: Caffeine (로컬), Redis (분산), Hazelcast (예정)
- **Spring Boot 자동 구성**: 최소한의 설정으로 즉시 사용 가능
- **포괄적인 메트릭**: 캐시 성능 모니터링 및 분석
- **유연한 구성**: 캐시별 세밀한 설정 지원

## 아키텍처

### 계층 구조

```
┌─────────────────────────────────────────────┐
│           Application Layer                 │
│         (Your Business Logic)               │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│           CacheService API                  │
│      (Unified Cache Interface)              │
└─────────────────┬───────────────────────────┘
                  │
┌─────────────────▼───────────────────────────┐
│         CacheStrategy Interface             │
│    (Strategy Pattern Implementation)        │
└──────┬──────────┬──────────┬────────────────┘
       │          │          │
┌──────▼─────┐ ┌─▼──────┐ ┌─▼──────────┐
│  Caffeine  │ │ Redis  │ │ Hazelcast  │
│  Strategy  │ │Strategy│ │ Strategy   │
└────────────┘ └────────┘ └────────────┘
```

### 주요 컴포넌트

#### 1. CacheService
모든 캐시 작업을 위한 통합 인터페이스로, 애플리케이션이 캐시와 상호작용하는 주요 진입점입니다.

#### 2. CacheStrategy
캐시 구현을 추상화하는 전략 인터페이스입니다. 각 캐시 백엔드는 이 인터페이스를 구현합니다.

#### 3. CacheConfiguration
자동 구성 클래스로 Spring Boot의 설정을 기반으로 적절한 캐시 전략을 선택합니다.

#### 4. CacheHealthIndicator
캐시 상태를 모니터링하고 Spring Boot Actuator와 통합됩니다.

## 설치 및 설정

### 1. Gradle 의존성 추가

```gradle
dependencies {
    implementation implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-cache:${simplixVersion}'

    // Redis 사용 시 추가
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Caffeine은 모듈에 포함되어 있음
}
```

### 2. 기본 설정

#### application.yml 설정

```yaml
simplix:
  cache:
    # 캐시 전략 선택: local, redis, hazelcast
    mode: local

    # 전역 설정
    default-ttl-seconds: 3600      # 기본 TTL (1시간)
    max-size: 10000                # 최대 항목 수 (로컬 캐시)
    cache-null-values: false       # null 값 캐싱 여부

    # 메트릭 설정
    metrics:
      enabled: true
      export-interval: 60          # 메트릭 수집 주기 (초)

    # 캐시별 개별 설정
    cache-configs:
      userCache:
        ttl-seconds: 900          # 15분
        max-size: 1000
      sessionCache:
        ttl-seconds: 1800         # 30분
        max-size: 5000
```

### 3. Redis 설정 (분산 캐시)

```yaml
simplix:
  cache:
    mode: redis
    redis:
      key-prefix: "app:cache:"     # Redis 키 접두사
      command-timeout: 2000        # 명령 타임아웃 (ms)
      use-ssl: false              # SSL 사용 여부

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
        shutdown-timeout: 100ms
```

### 4. 프로파일별 설정

#### 개발 환경 (application-dev.yml)

```yaml
simplix:
  cache:
    mode: local
    default-ttl-seconds: 300      # 5분
    max-size: 1000
    metrics:
      enabled: false              # 개발 환경에서는 메트릭 비활성화
```

#### 운영 환경 (application-prod.yml)

```yaml
simplix:
  cache:
    mode: redis
    default-ttl-seconds: 3600     # 1시간
    redis:
      use-ssl: true
      key-prefix: "prod:cache:"
    metrics:
      enabled: true
      export-interval: 30
```

## 통합 방법

### 1. 기본 사용법

#### CacheService 주입

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final CacheService cacheService;
    private final UserRepository userRepository;

    public User getUser(String userId) {
        // 캐시에서 조회 후 없으면 DB에서 로드
        return cacheService.getOrCompute(
            "users",                              // 캐시 이름
            userId,                               // 캐시 키
            () -> userRepository.findById(userId), // 값 로더
            User.class                            // 반환 타입
        );
    }

    public void updateUser(User user) {
        userRepository.save(user);
        // 캐시 무효화
        cacheService.evict("users", user.getId());
    }
}
```

### 2. 고급 사용법

#### 커스텀 TTL 설정

```java
@Service
public class ConfigurationService {

    private final CacheService cacheService;

    public SystemConfig getConfig(String key) {
        // 10분 TTL로 캐싱
        return cacheService.getOrCompute(
            "configs",
            key,
            () -> loadConfigFromDatabase(key),
            SystemConfig.class,
            Duration.ofMinutes(10)
        );
    }
}
```

#### 배치 작업

```java
@Service
public class BatchProcessingService {

    private final CacheService cacheService;

    public Map<String, Product> getProducts(Set<String> productIds) {
        // 캐시에서 모든 제품 조회
        Map<String, Product> cached = cacheService.getAll("products", Product.class);

        // 누락된 항목 찾기
        Set<String> missingIds = productIds.stream()
            .filter(id -> !cached.containsKey(id))
            .collect(Collectors.toSet());

        // 누락된 항목 로드 및 캐싱
        if (!missingIds.isEmpty()) {
            Map<String, Product> fetched = productRepository.findByIds(missingIds);
            cacheService.putAll("products", fetched);
            cached.putAll(fetched);
        }

        return cached;
    }
}
```

### 3. Spring Cache 어노테이션과 함께 사용

캐시 모듈은 Spring Cache 어노테이션과 호환됩니다:

```java
@Service
@EnableCaching
public class AnnotationBasedService {

    @Cacheable(value = "employees", key = "#id")
    public Employee getEmployee(Long id) {
        return employeeRepository.findById(id);
    }

    @CacheEvict(value = "employees", key = "#employee.id")
    public void updateEmployee(Employee employee) {
        employeeRepository.save(employee);
    }

    @CacheEvict(value = "employees", allEntries = true)
    public void clearAllEmployees() {
        // 모든 직원 캐시 제거
    }
}
```

### 4. 트랜잭션과 캐시

```java
@Service
@Transactional
public class TransactionalCacheService {

    private final CacheService cacheService;

    @Transactional
    public void performTransaction(String id, Data data) {
        // 트랜잭션 시작
        updateDatabase(id, data);

        // 트랜잭션 커밋 후 캐시 업데이트
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.put("data", id, data);
                }
            }
        );
    }
}
```

## 캐시 전략

### 로컬 캐시 (Caffeine)

#### 특징
- 단일 JVM 인스턴스 내에서 동작
- 매우 빠른 응답 시간 (나노초 단위)
- 외부 의존성 없음
- 메모리 효율적

#### 적합한 사용 사례
- 단일 서버 애플리케이션
- 개발 및 테스트 환경
- 읽기 전용 참조 데이터
- 사용자별 세션 데이터 (단일 서버)

#### 설정 예시

```yaml
simplix:
  cache:
    mode: local
    caffeine:
      initial-capacity: 100
      maximum-size: 10000
      maximum-weight: 1000000
      expire-after-write: 5m
      expire-after-access: 2m
      refresh-after-write: 1m
      record-stats: true
```

### Redis 캐시

#### 특징
- 분산 캐싱 지원
- 데이터 영속성
- 다중 인스턴스 간 공유
- 풍부한 데이터 구조 지원

#### 적합한 사용 사례
- 마이크로서비스 아키텍처
- 다중 인스턴스 배포
- 세션 클러스터링
- 실시간 데이터 공유

#### 고급 설정

```yaml
simplix:
  cache:
    mode: redis
    redis:
      cluster:
        enabled: true
        nodes:
          - redis-node1:6379
          - redis-node2:6379
          - redis-node3:6379
      sentinel:
        enabled: false
        master: mymaster
        nodes:
          - sentinel1:26379
          - sentinel2:26379
      serialization:
        type: json  # json, kryo, jdk
```

## 고급 기능

### 1. 캐시 워밍업

애플리케이션 시작 시 캐시를 미리 로드합니다:

```java
@Component
public class CacheWarmer implements ApplicationRunner {

    private final CacheService cacheService;
    private final DataRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("캐시 워밍업 시작");

        // 자주 사용되는 데이터 미리 로드
        List<FrequentData> data = repository.findFrequentlyUsed();

        data.forEach(item ->
            cacheService.put("frequent", item.getId(), item)
        );

        log.info("캐시 워밍업 완료: {} 항목 로드", data.size());
    }
}
```

### 2. 캐시 이벤트 처리

```java
@Component
public class CacheEventListener {

    @EventListener
    public void handleCacheEvict(CacheEvictEvent event) {
        log.info("캐시 제거: cache={}, key={}",
            event.getCacheName(), event.getKey());

        // 추가 처리 (예: 통계 업데이트)
        updateStatistics(event);
    }

    @EventListener
    public void handleCacheMiss(CacheMissEvent event) {
        log.warn("캐시 미스: cache={}, key={}",
            event.getCacheName(), event.getKey());

        // 캐시 미스 분석
        analyzeMissPattern(event);
    }
}
```

### 3. 조건부 캐싱

```java
@Service
public class ConditionalCacheService {

    private final CacheService cacheService;

    public Data getData(String id, boolean useCache) {
        if (!useCache) {
            return loadFromDatabase(id);
        }

        return cacheService.getOrCompute(
            "conditionalCache",
            id,
            () -> loadFromDatabase(id),
            Data.class,
            this::determineTTL
        );
    }

    private Duration determineTTL(String id) {
        // 동적 TTL 결정 로직
        if (isFrequentlyAccessed(id)) {
            return Duration.ofHours(1);
        }
        return Duration.ofMinutes(5);
    }
}
```

### 4. 캐시 태깅 및 그룹 관리

```java
@Service
public class TaggedCacheService {

    private final CacheService cacheService;
    private final Map<String, Set<String>> tagToKeys = new ConcurrentHashMap<>();

    public void putWithTags(String cacheName, String key, Object value, String... tags) {
        cacheService.put(cacheName, key, value);

        // 태그별로 키 관리
        for (String tag : tags) {
            tagToKeys.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet())
                     .add(key);
        }
    }

    public void evictByTag(String cacheName, String tag) {
        Set<String> keys = tagToKeys.get(tag);
        if (keys != null) {
            cacheService.evictAll(cacheName, keys);
            tagToKeys.remove(tag);
        }
    }
}
```

## 모니터링 및 운영

### 1. 헬스 체크

```java
@Component
public class CacheHealthContributor implements HealthIndicator {

    private final CacheService cacheService;

    @Override
    public Health health() {
        try {
            // 캐시 연결 테스트
            cacheService.put("health", "test", "value");
            String value = cacheService.get("health", "test", String.class)
                .orElse(null);

            if ("value".equals(value)) {
                return Health.up()
                    .withDetail("strategy", cacheService.getStrategyName())
                    .withDetail("available", true)
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }

        return Health.down()
            .withDetail("reason", "캐시 테스트 실패")
            .build();
    }
}
```

### 2. 메트릭 수집

```java
@Component
@RequiredArgsConstructor
public class CacheMetricsCollector {

    private final CacheService cacheService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 30000)
    public void collectMetrics() {
        CacheStatistics stats = cacheService.getStatistics("mainCache");

        // Micrometer 메트릭 등록
        meterRegistry.gauge("cache.hit.ratio", stats.hitRate());
        meterRegistry.counter("cache.hits", stats.hits());
        meterRegistry.counter("cache.misses", stats.misses());
        meterRegistry.counter("cache.evictions", stats.evictions());
        meterRegistry.gauge("cache.size", stats.size());
    }
}
```

### 3. 관리 엔드포인트

```java
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
public class CacheAdminController {

    private final CacheService cacheService;

    @GetMapping("/stats")
    public Map<String, CacheStatistics> getStatistics() {
        return cacheService.getAllStatistics();
    }

    @PostMapping("/clear/{cacheName}")
    public ResponseEntity<Void> clearCache(@PathVariable String cacheName) {
        cacheService.clear(cacheName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/clear-all")
    public ResponseEntity<Void> clearAllCaches() {
        cacheService.clearAll();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/size/{cacheName}")
    public Map<String, Object> getCacheSize(@PathVariable String cacheName) {
        return Map.of(
            "cacheName", cacheName,
            "size", cacheService.getKeys(cacheName).size(),
            "keys", cacheService.getKeys(cacheName)
        );
    }
}
```

## 성능 최적화

### 1. 키 설계 전략

```java
public class CacheKeyBuilder {

    // 계층적 키 구조
    public static String buildKey(String... parts) {
        return String.join(":", parts);
    }

    // 사용 예시
    public String getUserPermissionKey(String tenantId, String userId) {
        return buildKey("permissions", tenantId, userId);
    }

    public String getConfigKey(String module, String property) {
        return buildKey("config", module, property);
    }
}
```

### 2. 직렬화 최적화

```java
@Configuration
public class CacheSerializationConfig {

    @Bean
    public RedisSerializer<Object> redisSerializer() {
        // JSON 직렬화 (가독성 우선)
        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(Object.class);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
            mapper.getPolymorphicTypeValidator(),
            ObjectMapper.DefaultTyping.NON_FINAL
        );

        serializer.setObjectMapper(mapper);
        return serializer;
    }

    // 또는 Kryo 직렬화 (성능 우선)
    @Bean
    @Profile("production")
    public RedisSerializer<Object> kryoSerializer() {
        return new KryoRedisSerializer();
    }
}
```

### 3. 배치 처리 최적화

```java
@Service
public class OptimizedBatchService {

    private final CacheService cacheService;

    public void processBatch(List<String> ids) {
        // 파이프라이닝을 통한 배치 처리
        int batchSize = 100;

        for (int i = 0; i < ids.size(); i += batchSize) {
            List<String> batch = ids.subList(i,
                Math.min(i + batchSize, ids.size()));

            Map<String, Data> batchData = loadBatchData(batch);
            cacheService.putAll("data", batchData);
        }
    }
}
```

### 4. 비동기 캐시 로딩

```java
@Service
public class AsyncCacheLoader {

    private final CacheService cacheService;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public CompletableFuture<Data> getDataAsync(String id) {
        // 캐시 확인
        Optional<Data> cached = cacheService.get("asyncData", id, Data.class);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached.get());
        }

        // 비동기 로드
        return CompletableFuture.supplyAsync(() -> {
            Data data = loadExpensiveData(id);
            cacheService.put("asyncData", id, data);
            return data;
        }, executor);
    }
}
```

## 문제 해결

### 1. 캐시 미스율이 높은 경우

#### 진단 방법

```java
@Component
public class CacheMissDiagnostics {

    @Scheduled(fixedDelay = 60000)
    public void analyzeMissRate() {
        CacheStatistics stats = cacheService.getStatistics("problemCache");

        if (stats.hitRate() < 0.5) {
            log.warn("낮은 캐시 히트율 감지: {}%", stats.hitRate() * 100);

            // 미스 패턴 분석
            analyzeMissPatterns();

            // TTL 조정 제안
            suggestTTLAdjustment(stats);
        }
    }
}
```

#### 해결 방법

1. **TTL 증가**: 안정적인 데이터의 경우 TTL을 늘립니다
2. **캐시 크기 증가**: max-size 파라미터 조정
3. **캐시 워밍업**: 자주 사용되는 데이터 사전 로드
4. **키 전략 검토**: 일관된 키 생성 확인

### 2. 메모리 부족

#### 모니터링

```java
@Component
public class MemoryMonitor {

    @EventListener
    public void handleOutOfMemory(OutOfMemoryError error) {
        // 긴급 캐시 정리
        cacheService.clearAll();

        // 알림 발송
        alertService.sendCriticalAlert("캐시 메모리 부족", error);

        // 캐시 크기 재조정
        reconfigureCacheSize();
    }
}
```

#### 해결 방법

1. **캐시 크기 제한**: maximumSize 설정
2. **가중치 기반 제거**: maximumWeight 사용
3. **TTL 단축**: 메모리 압박 시 TTL 감소
4. **선택적 캐싱**: 중요한 데이터만 캐싱

### 3. Redis 연결 문제

#### 재시도 로직

```java
@Component
public class RedisCacheWithRetry {

    private final RetryTemplate retryTemplate;

    public RedisCacheWithRetry() {
        this.retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .fixedBackoff(1000)
            .retryOn(RedisConnectionException.class)
            .build();
    }

    public <T> T getWithRetry(String key, Class<T> type) {
        return retryTemplate.execute(context -> {
            log.debug("Redis 조회 시도 #{}", context.getRetryCount() + 1);
            return redisTemplate.opsForValue().get(key);
        });
    }
}
```

### 4. 캐시 일관성 문제

#### 분산 락 사용

```java
@Service
public class ConsistentCacheService {

    private final RedissonClient redissonClient;
    private final CacheService cacheService;

    public void updateWithLock(String id, Data data) {
        RLock lock = redissonClient.getLock("lock:" + id);

        try {
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 데이터베이스 업데이트
                updateDatabase(id, data);

                // 캐시 업데이트
                cacheService.put("data", id, data);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheException("락 획득 실패", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

## 마이그레이션 가이드

### 1. Spring Cache에서 마이그레이션

#### 기존 코드 (Spring Cache)

```java
@Service
public class OldService {

    @Cacheable(value = "users", key = "#id")
    public User getUser(Long id) {
        return userRepository.findById(id);
    }

    @CacheEvict(value = "users", key = "#user.id")
    public void updateUser(User user) {
        userRepository.save(user);
    }

    @CachePut(value = "users", key = "#user.id")
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}
```

#### 새 코드 (CacheService)

```java
@Service
@RequiredArgsConstructor
public class NewService {

    private final CacheService cacheService;
    private final UserRepository userRepository;

    public User getUser(Long id) {
        return cacheService.getOrCompute(
            "users",
            id,
            () -> userRepository.findById(id),
            User.class
        );
    }

    public void updateUser(User user) {
        userRepository.save(user);
        cacheService.evict("users", user.getId());
    }

    public User saveUser(User user) {
        User saved = userRepository.save(user);
        cacheService.put("users", saved.getId(), saved);
        return saved;
    }
}
```

### 2. 단계별 마이그레이션 전략

#### 1단계: 의존성 추가
```gradle
dependencies {
    implementation implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-cache:${simplixVersion}'
}
```

#### 2단계: 설정 마이그레이션
```yaml
# 기존 Spring Cache 설정
spring:
  cache:
    type: redis
    redis:
      time-to-live: 600000

# 새 캐시 모듈 설정
simplix:
  cache:
    mode: redis
    default-ttl-seconds: 600
```

#### 3단계: 코드 점진적 전환
```java
@Service
public class HybridService {

    private final CacheService cacheService;

    // 새 메서드 추가
    public Data getDataV2(String id) {
        return cacheService.getOrCompute(
            "data", id,
            () -> getDataV1(id),  // 기존 메서드 활용
            Data.class
        );
    }

    // 기존 메서드 유지 (deprecated)
    @Deprecated
    @Cacheable("data")
    public Data getDataV1(String id) {
        return repository.findById(id);
    }
}
```

#### 4단계: 테스트 및 검증
```java
@SpringBootTest
class CacheMigrationTest {

    @Test
    void testCacheCompatibility() {
        // 기존 캐시 동작 확인
        Data v1Data = service.getDataV1("test");

        // 새 캐시 동작 확인
        Data v2Data = service.getDataV2("test");

        // 일관성 검증
        assertThat(v1Data).isEqualTo(v2Data);
    }
}
```

### 3. 롤백 계획

```java
@Configuration
@ConditionalOnProperty(name = "cache.migration.rollback", havingValue = "true")
public class CacheRollbackConfig {

    @Bean
    @Primary
    public CacheManager springCacheManager() {
        // 기존 Spring Cache Manager로 롤백
        return new RedisCacheManager();
    }
}
```

## 베스트 프랙티스

### 1. 캐시 네이밍 규칙

```java
public class CacheNames {
    // 모듈별 캐시 이름 상수
    public static final String USER_CACHE = "users";
    public static final String PERMISSION_CACHE = "permissions";
    public static final String CONFIG_CACHE = "configs";

    // 키 생성 규칙
    public static String userKey(String tenantId, String userId) {
        return String.format("%s:%s", tenantId, userId);
    }
}
```

### 2. 캐시 계층화

```java
@Service
public class LayeredCacheService {

    private final CacheService l1Cache;  // Local
    private final CacheService l2Cache;  // Redis

    public Data getData(String id) {
        // L1 캐시 확인
        Optional<Data> l1Data = l1Cache.get("data", id, Data.class);
        if (l1Data.isPresent()) {
            return l1Data.get();
        }

        // L2 캐시 확인
        Optional<Data> l2Data = l2Cache.get("data", id, Data.class);
        if (l2Data.isPresent()) {
            // L1 캐시에 복사
            l1Cache.put("data", id, l2Data.get(), Duration.ofMinutes(5));
            return l2Data.get();
        }

        // 데이터베이스에서 로드
        Data data = loadFromDatabase(id);

        // 양쪽 캐시에 저장
        l2Cache.put("data", id, data, Duration.ofHours(1));
        l1Cache.put("data", id, data, Duration.ofMinutes(5));

        return data;
    }
}
```

### 3. 캐시 무효화 전략

```java
@Component
public class CacheInvalidationStrategy {

    // 즉시 무효화
    public void immediateInvalidation(String key) {
        cacheService.evict("cache", key);
    }

    // 지연 무효화
    @Async
    public void delayedInvalidation(String key, Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            cacheService.evict("cache", key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 조건부 무효화
    public void conditionalInvalidation(String key, Predicate<Data> condition) {
        cacheService.get("cache", key, Data.class)
            .filter(condition)
            .ifPresent(data -> cacheService.evict("cache", key));
    }
}
```
