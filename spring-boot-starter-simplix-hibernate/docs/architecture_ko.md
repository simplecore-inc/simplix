# 아키텍처 설계

## 모듈 구조

```
spring-boot-starter-simplix-hibernate/
├── config/                 # 설정 및 자동 구성
│   ├── HibernateCacheAutoConfiguration
│   ├── HibernateCacheProperties
│   ├── HibernateCacheInitializer
│   └── HibernateIntegrator
├── core/                   # 핵심 기능
│   ├── HibernateCacheManager
│   ├── QueryCacheManager
│   ├── EntityCacheScanner
│   └── CacheMode
├── provider/              # 캐시 프로바이더
│   ├── CacheProvider (인터페이스)
│   ├── CacheProviderFactory
│   ├── LocalCacheProvider
│   ├── RedisCacheProvider
│   ├── HazelcastCacheProvider
│   └── InfinispanCacheProvider
├── listener/              # 이벤트 리스너
│   ├── GlobalEntityListener
│   ├── AutoCacheEvictionListener
│   └── ConditionalEvictionListener
├── aspect/                # AOP 관련
│   └── AutoCacheEvictionAspect
├── strategy/              # 캐시 전략
│   └── CacheEvictionStrategy
├── event/                 # 이벤트 처리
│   └── CacheEvictionEvent
├── monitoring/            # 모니터링
│   └── EvictionMetrics
├── batch/                 # 배치 처리
│   └── BatchEvictionOptimizer
├── resilience/            # 복원력
│   └── EvictionRetryHandler
├── cluster/               # 클러스터 동기화
│   └── ClusterSyncMonitor
└── admin/                 # 관리 기능
    └── CacheAdminController
```

## 핵심 컴포넌트

### 1. HibernateCacheAutoConfiguration

Spring Boot 자동 구성 클래스로, 모듈의 진입점입니다.

**책임:**
- Hibernate와 Spring JPA 존재 시 자동 활성화
- 필요한 빈 등록 및 구성
- 캐시된 엔티티 스캔 트리거

**조건부 활성화:**
```java
@ConditionalOnClass({EntityManagerFactory.class, org.hibernate.Cache.class})
@ConditionalOnProperty(
    prefix = "simplix.hibernate.cache",
    name = "disabled",
    havingValue = "false",
    matchIfMissing = true
)
```

### 2. HibernateCacheManager

캐시 작업의 중심 관리자입니다.

**주요 메서드:**
- `evictEntity(Class<?> entityClass, Object id)` - 특정 엔티티 제거
- `evictEntityCache(Class<?> entityClass)` - 엔티티 타입 전체 제거
- `evictQueryRegion(String queryRegion)` - 쿼리 캐시 리전 제거
- `evictAll()` - 모든 캐시 제거

**동작 방식:**
```java
// JPA Cache API 사용
jakarta.persistence.Cache cache = entityManagerFactory.getCache();
cache.evict(entityClass, id);

// Hibernate Cache API 사용
org.hibernate.Cache hibernateCache = sessionFactory.getCache();
hibernateCache.evictRegion(regionName);
```

### 3. CacheProviderFactory

사용 가능한 캐시 프로바이더를 관리하고 선택합니다.

**프로바이더 선택 알고리즘:**
```java
public CacheProvider selectBestAvailable() {
    String[] priority = {"REDIS", "HAZELCAST", "INFINISPAN"};

    for (String type : priority) {
        CacheProvider provider = providers.get(type);
        if (provider != null && provider.isAvailable()) {
            return provider;
        }
    }

    return localProvider; // 폴백
}
```

### 4. GlobalEntityListener

JPA 글로벌 엔티티 리스너로, 모든 엔티티 변경을 감지합니다.

**등록 방식:**
- `META-INF/orm.xml`을 통해 자동 등록
- 모든 엔티티에 자동 적용

**처리 이벤트:**
- `@PostPersist` - 엔티티 저장 후
- `@PostUpdate` - 엔티티 수정 후
- `@PostRemove` - 엔티티 삭제 후

## 캐시 무효화 흐름

### 1. 단일 노드 환경

```
엔티티 변경
    ↓
GlobalEntityListener 감지
    ↓
HibernateCacheManager.evictEntity()
    ↓
로컬 캐시 제거
    ↓
QueryCacheManager.evictRelatedQueries()
    ↓
관련 쿼리 캐시 제거
```

### 2. 분산 환경 (Redis)

```
노드 A: 엔티티 변경
    ↓
CacheEvictionEvent 생성
    ↓
RedisCacheProvider.broadcastEviction()
    ↓
Redis Pub/Sub 채널에 발행
    ↓
노드 B, C: 메시지 수신
    ↓
각 노드에서 로컬 캐시 제거
```

## 프로바이더 아키텍처

### CacheProvider 인터페이스

```java
public interface CacheProvider {
    String getType();
    boolean isAvailable();
    void evictLocal(String region, Object key);
    void evictAll(String region);
    void broadcastEviction(CacheEvictionEvent event);
    void subscribeToEvictions(Consumer<CacheEvictionEvent> handler);
    CacheProviderStats getStats();
}
```

### LocalCacheProvider

- 단순 로컬 캐시 관리
- 분산 동기화 없음
- 항상 사용 가능한 폴백 옵션

### RedisCacheProvider

- Redis Pub/Sub 사용
- 비동기 메시지 브로드캐스팅
- 연결 실패 시 자동 재연결

### HazelcastCacheProvider

- Hazelcast Topic 사용
- 클러스터 멤버십 자동 관리
- Near Cache 지원

## 성능 최적화 전략

### 1. 배치 무효화

```java
@Component
public class BatchEvictionOptimizer {

    private final Queue<CacheEvictionEvent> pendingEvictions;

    @Scheduled(fixedDelay = 100)
    public void processBatch() {
        List<CacheEvictionEvent> batch = drainQueue();
        if (!batch.isEmpty()) {
            provider.broadcastBatch(batch);
        }
    }
}
```

### 2. 조건부 무효화

```java
@Component
public class ConditionalEvictionListener {

    @PreUpdate
    public void checkConditionalEviction(Object entity) {
        CacheEvictionPolicy policy = entity.getClass()
            .getAnnotation(CacheEvictionPolicy.class);

        if (policy != null) {
            String[] dirtyFields = getDirtyFields(entity);
            if (shouldEvict(policy, dirtyFields)) {
                cacheManager.evictEntity(entity);
            }
        }
    }
}
```

### 3. 비동기 처리

```java
@Async
public void processEviction(CacheEvictionEvent event) {
    try {
        provider.broadcastEviction(event);
    } catch (Exception e) {
        retryHandler.scheduleRetry(event);
    }
}
```

## 복원력 메커니즘

### 1. 재시도 처리

```java
@Component
public class EvictionRetryHandler {

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void retryEviction(CacheEvictionEvent event) {
        provider.broadcastEviction(event);
    }
}
```

### 2. 서킷 브레이커

```java
@Component
public class CircuitBreakerProvider {

    private boolean open = false;
    private int failureCount = 0;

    public void evict(CacheEvictionEvent event) {
        if (open) {
            fallbackToLocal(event);
            return;
        }

        try {
            provider.broadcastEviction(event);
            reset();
        } catch (Exception e) {
            recordFailure();
            if (failureCount > threshold) {
                open = true;
                scheduleReset();
            }
        }
    }
}
```

## 모니터링 및 메트릭

### 수집 메트릭

- 캐시 히트율
- 무효화 빈도
- 프로바이더 응답 시간
- 네트워크 실패율
- 큐 크기 및 지연

### Micrometer 통합

```java
@Component
public class EvictionMetrics {

    private final MeterRegistry registry;

    public void recordEviction(String entityType) {
        registry.counter("cache.eviction",
            "entity", entityType).increment();
    }

    public void recordLatency(long millis) {
        registry.timer("cache.eviction.latency")
            .record(millis, TimeUnit.MILLISECONDS);
    }
}
```

## 보안 고려사항

### 1. 네트워크 보안
- Redis/Hazelcast 연결 암호화
- 인증 및 권한 관리
- 방화벽 규칙 설정

### 2. 데이터 보안
- 민감한 데이터 캐싱 금지
- 필요시 암호화 적용
- 캐시 키 난독화

### 3. DoS 방지
- 캐시 크기 제한
- 요청 속도 제한
- 이상 패턴 감지