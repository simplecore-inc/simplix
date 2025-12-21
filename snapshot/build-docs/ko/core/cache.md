# Cache Guide

## Overview

SimpliX는 SPI(Service Provider Interface) 기반의 캐시 추상화를 제공합니다. 다양한 캐시 백엔드(Redis, Caffeine, In-memory 등)를 플러그인 방식으로 교체할 수 있습니다.

---

## CacheManager

싱글톤 패턴의 캐시 관리자입니다.

### 구조

```java
public class CacheManager {
    private static final CacheManager INSTANCE = new CacheManager();

    public static CacheManager getInstance() {
        return INSTANCE;
    }
}
```

### 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `get(cacheName, key, type)` | 캐시에서 값 조회 |
| `put(cacheName, key, value)` | 캐시에 값 저장 |
| `put(cacheName, key, value, ttl)` | TTL과 함께 저장 |
| `getOrCompute(...)` | 없으면 계산 후 저장 |
| `evict(cacheName, key)` | 특정 키 제거 |
| `clear(cacheName)` | 캐시 전체 제거 |
| `exists(cacheName, key)` | 키 존재 여부 확인 |
| `isAvailable()` | 프로바이더 사용 가능 여부 |
| `getProviderName()` | 프로바이더 이름 |

### 기본 사용

```java
CacheManager cache = CacheManager.getInstance();

// 저장
cache.put("users", "user:123", user);

// 조회
Optional<User> cachedUser = cache.get("users", "user:123", User.class);

// TTL과 함께 저장
cache.put("sessions", "sess:abc", session, Duration.ofHours(1));

// 제거
cache.evict("users", "user:123");

// 전체 제거
cache.clear("users");
```

### Cache-aside 패턴

```java
CacheManager cache = CacheManager.getInstance();

// 캐시에 없으면 DB에서 조회 후 캐싱
User user = cache.getOrCompute(
    "users",
    "user:" + userId,
    () -> userRepository.findById(userId).orElse(null),
    User.class
);
```

---

## CacheProvider

### 인터페이스

```java
public interface CacheProvider {
    <T> Optional<T> get(String cacheName, Object key, Class<T> type);
    <T> void put(String cacheName, Object key, T value);
    <T> void put(String cacheName, Object key, T value, Duration ttl);
    <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type);
    void evict(String cacheName, Object key);
    void clear(String cacheName);
    boolean exists(String cacheName, Object key);
    boolean isAvailable();
    String getName();
    default int getPriority() { return 0; }
}
```

### 프로바이더 우선순위

- SPI로 로드된 프로바이더 중 `getPriority()` 값이 가장 높은 것이 선택됩니다
- 프로바이더가 없으면 `NoOpCacheProvider` (아무 동작 없음)가 사용됩니다

---

## 서비스 사용 예제

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CacheManager cache = CacheManager.getInstance();
    private static final String CACHE_NAME = "users";
    private static final Duration TTL = Duration.ofHours(2);

    public User getUser(Long id) {
        String key = "user:" + id;
        return cache.getOrCompute(
            CACHE_NAME,
            key,
            () -> userRepository.findById(id).orElse(null),
            User.class
        );
    }

    public User updateUser(User user) {
        User saved = userRepository.save(user);
        cache.put(CACHE_NAME, "user:" + user.getId(), saved, TTL);
        return saved;
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        cache.evict(CACHE_NAME, "user:" + id);
    }
}
```

---

## 커스텀 CacheProvider 구현

### 1. 인터페이스 구현

```java
public class RedisCacheProvider implements CacheProvider {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        String fullKey = cacheName + ":" + key;
        Object value = redisTemplate.opsForValue().get(fullKey);
        return Optional.ofNullable(type.cast(value));
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        String fullKey = cacheName + ":" + key;
        redisTemplate.opsForValue().set(fullKey, value);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        String fullKey = cacheName + ":" + key;
        redisTemplate.opsForValue().set(fullKey, value, ttl);
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        Optional<T> cached = get(cacheName, key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            T value = valueLoader.call();
            if (value != null) {
                put(cacheName, key, value);
            }
            return value;
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute cache value", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        String fullKey = cacheName + ":" + key;
        redisTemplate.delete(fullKey);
    }

    @Override
    public void clear(String cacheName) {
        Set<String> keys = redisTemplate.keys(cacheName + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        String fullKey = cacheName + ":" + key;
        return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
    }

    @Override
    public boolean isAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "RedisCacheProvider";
    }

    @Override
    public int getPriority() {
        return 100;  // higher priority than default
    }
}
```

### 2. SPI 등록

`META-INF/services/dev.simplecore.simplix.core.cache.CacheProvider` 파일 생성:

```
com.example.cache.RedisCacheProvider
```

### 3. Spring Bean 등록 (선택사항)

```java
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheProvider redisCacheProvider(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheProvider(redisTemplate);
    }
}
```

---

## In-Memory CacheProvider 예제

```java
public class InMemoryCacheProvider implements CacheProvider {
    private final Map<String, Map<Object, CacheEntry>> caches = new ConcurrentHashMap<>();

    @Getter
    @AllArgsConstructor
    private static class CacheEntry {
        private final Object value;
        private final Instant expireAt;

        boolean isExpired() {
            return expireAt != null && Instant.now().isAfter(expireAt);
        }
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        Map<Object, CacheEntry> cache = caches.get(cacheName);
        if (cache == null) return Optional.empty();

        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) cache.remove(key);
            return Optional.empty();
        }
        return Optional.ofNullable(type.cast(entry.getValue()));
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        put(cacheName, key, value, null);
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        caches.computeIfAbsent(cacheName, k -> new ConcurrentHashMap<>())
              .put(key, new CacheEntry(value,
                  ttl != null ? Instant.now().plus(ttl) : null));
    }

    @Override
    public void evict(String cacheName, Object key) {
        Map<Object, CacheEntry> cache = caches.get(cacheName);
        if (cache != null) cache.remove(key);
    }

    @Override
    public void clear(String cacheName) {
        caches.remove(cacheName);
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        return get(cacheName, key, Object.class).isPresent();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getName() {
        return "InMemoryCacheProvider";
    }

    @Override
    public int getPriority() {
        return 0;  // default priority
    }
}
```

---

## Related Documents

- [Overview (아키텍처 개요)](ko/core/overview.md) - 모듈 구조
- [Entity & Repository Guide (엔티티/리포지토리)](ko/core/entity-repository.md) - 베이스 엔티티, 복합 키
- [Tree Structure Guide (트리 구조)](ko/core/tree-structure.md) - TreeEntity, SimpliXTreeService
- [Type Converters Guide (타입 변환)](ko/core/type-converters.md) - Boolean, Enum, DateTime 변환
- [Security Guide (보안)](ko/core/security.md) - XSS 방지, 해싱, 마스킹
- [Exception & API Guide (예외/API)](ko/core/exception-api.md) - 에러 코드, API 응답
