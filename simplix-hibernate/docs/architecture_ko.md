# 아키텍처 설계

## 모듈 구조

```
simplix-hibernate/
├── config/                 # 설정 및 자동 구성
│   ├── SimpliXHibernateCacheAutoConfiguration
│   ├── HibernateCacheProperties
│   ├── HibernateCacheInitializer
│   ├── HibernateCacheHolder
│   └── HibernateIntegrator
├── core/                   # 핵심 기능
│   ├── HibernateCacheManager
│   ├── QueryCacheManager
│   ├── EntityCacheScanner
│   └── CacheMode
├── provider/              # 캐시 프로바이더
│   ├── CacheProvider (인터페이스)
│   ├── CacheProviderFactory
│   └── LocalCacheProvider
├── listener/              # 이벤트 리스너
│   ├── GlobalEntityListener
│   └── AutoCacheEvictionListener
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

> **참고**: 현재 분산 캐시 프로바이더(Redis, Hazelcast, Infinispan)와 일부 고급 기능(ConditionalEvictionListener)은 개발 중이며 향후 버전에서 지원 예정입니다.

## 핵심 컴포넌트

### 1. SimpliXHibernateCacheAutoConfiguration

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

## 프로바이더 아키텍처

### CacheProvider 인터페이스

```java
public interface CacheProvider {
    String getType();
    boolean isAvailable();
    void broadcastEviction(CacheEvictionEvent event);
    void subscribeToEvictions(CacheEvictionEventListener listener);
    void initialize();
    void shutdown();
    CacheProviderStats getStats();

    interface CacheEvictionEventListener {
        void onEvictionEvent(CacheEvictionEvent event);
    }

    record CacheProviderStats(
        long evictionsSent,
        long evictionsReceived,
        boolean connected,
        String nodeId
    ) {}
}
```

### LocalCacheProvider

- EhCache 기반 로컬 캐시 관리
- JCache (JSR-107) 표준 구현
- 메모리 내 캐싱으로 빠른 응답 속도
- 힙 메모리 관리 및 자동 eviction

> **향후 계획**: Redis, Hazelcast 기반 분산 캐시 프로바이더 지원 예정

## 성능 최적화 전략

### 1. 배치 무효화

BatchEvictionOptimizer는 여러 캐시 무효화 이벤트를 모아서 한 번에 처리합니다:

```java
@Component
public class BatchEvictionOptimizer {
    private final Queue<CacheEvictionEvent> pendingEvictions;

    public void processBatch() {
        List<CacheEvictionEvent> batch = drainQueue();
        if (!batch.isEmpty()) {
            provider.evictBatch(batch);
        }
    }
}
```

### 2. 선택적 무효화

CacheEvictionStrategy는 실제로 변경이 필요한 경우에만 캐시를 무효화합니다:
- 변경된 필드 추적
- 불필요한 캐시 제거 방지
- 리전별 독립적인 무효화

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