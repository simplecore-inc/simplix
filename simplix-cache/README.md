# SimpliX Cache Module

Flexible strategy-based caching module for Spring Boot applications with support for multiple cache implementations.

## Architecture Changes

Recent refactoring changes:
- **Spring Boot Auto-Configuration**: Migrated from SPI to Spring Boot auto-configuration
- **Constructor Injection**: Constructor-based dependency injection pattern
- **Conditional Bean Creation**: Works seamlessly even without Redis dependency
- **Nested Configuration**: Isolated Redis configuration to prevent class loading issues

## Features

- ✔ **Multiple Cache Strategies** - Local (Caffeine), Redis, and Hazelcast (planned)
- ✔ **Strategy Pattern** - Easy switching between cache implementations
- ✔ **Spring Boot Integration** - Auto-configuration with IDE support
- ✔ **Metrics & Monitoring** - Built-in health checks and metrics collection
- ✔ **Flexible Configuration** - Per-cache TTL and size settings
- ✔ **Multi-Instance Support** - Distributed caching via Redis
- ✔ **Comprehensive Logging** - Debug and trace level logging

## Quick Start

### 1. Add Dependency

```gradle
dependencies {
    implementation 'dev.simplecore.simplix:spring-boot-starter-simplix-cache:${simplixVersion}'

    // Optional: For Redis support
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

### 2. Configure Cache Mode

```yaml
# application.yml
simplix:
  cache:
    mode: local  # or 'redis' for distributed caching
```

### 3. Use in Code

```java
@Service
public class MyService {

    @Autowired
    private CacheService cacheService;

    public String getData(String key) {
        return cacheService.getOrCompute(
            "myCache",           // Cache name
            key,                 // Cache key
            () -> fetchData(key), // Value loader
            String.class,        // Value type
            Duration.ofHours(1)  // TTL
        );
    }
}
```

## Configuration

### Basic Configuration

```yaml
simplix:
  cache:
    mode: local                    # Cache strategy: local, redis
    default-ttl-seconds: 3600      # Default TTL (1 hour)
    max-size: 10000               # Max entries (local cache only)
    cache-null-values: false      # Whether to cache null values
```

### Per-Cache Configuration

```yaml
simplix:
  cache:
    cache-configs:
      userPermissions:
        ttl-seconds: 900          # 15 minutes
        max-size: 2000
      siteTimeZones:
        ttl-seconds: 86400        # 24 hours
        max-size: 10000
```

### Redis Configuration

```yaml
simplix:
  cache:
    mode: redis
    redis:
      key-prefix: "app:cache:"
      use-key-prefix: true
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

## Cache Strategies

### Local Cache (Caffeine)

**When to use:**
- Single instance applications
- Development environments
- Low latency requirements
- No external dependencies needed

**Configuration:**
```yaml
simplix:
  cache:
    mode: local
    max-size: 10000
```

### Redis Cache

**When to use:**
- Multi-instance deployments
- Production environments
- Persistent cache needed
- Distributed cache requirements

**Configuration:**
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

## API Reference

### CacheService Methods

```java
// Get value from cache
Optional<T> get(String cacheName, Object key, Class<T> type)

// Store value in cache
void put(String cacheName, Object key, T value)
void put(String cacheName, Object key, T value, Duration ttl)

// Get or compute if absent
T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type)
T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl)

// Remove entries
void evict(String cacheName, Object key)
void evictAll(String cacheName, Collection<?> keys)
void clear(String cacheName)
void clearAll()

// Cache operations
boolean exists(String cacheName, Object key)
Collection<Object> getKeys(String cacheName)
Map<Object, T> getAll(String cacheName, Class<T> type)
void putAll(String cacheName, Map<Object, T> entries)

// Statistics
CacheStatistics getStatistics(String cacheName)
```

## Usage Examples

### Basic Usage

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

### Custom TTL

```java
public class ConfigService {

    public Config getConfig(String key) {
        // Cache for 5 minutes
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

### Batch Operations

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

### Cache Statistics

```java
@Component
public class CacheMonitor {

    @Autowired
    private CacheService cacheService;

    @Scheduled(fixedDelay = 60000)
    public void logStatistics() {
        CacheStatistics stats = cacheService.getStatistics("users");

        log.info("Cache stats - Hits: {}, Misses: {}, Hit Rate: {}%",
            stats.hits(),
            stats.misses(),
            stats.hitRate() * 100
        );
    }
}
```

### Spring @Cacheable Annotations

SimpliX Cache supports Spring's standard cache annotations:

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    // Auto-cache on retrieval
    @Cacheable(value = "products", key = "#productId")
    public Product getProduct(String productId) {
        // Only executed on cache miss
        return productRepository.findById(productId).orElse(null);
    }

    // Update cache after modification
    @CachePut(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    // Evict from cache on deletion
    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(String productId) {
        productRepository.deleteById(productId);
    }

    // Clear entire cache
    @CacheEvict(value = "products", allEntries = true)
    public void clearAllProducts() {
        log.info("Clearing all product cache");
    }

    // Conditional caching
    @Cacheable(value = "products", key = "#productId", condition = "#productId != null")
    public Product getProductConditional(String productId) {
        return productRepository.findById(productId).orElse(null);
    }

    // Composite key
    @Cacheable(value = "productsByCategory", key = "#category + '_' + #page")
    public List<Product> getProductsByCategory(String category, int page) {
        return productRepository.findByCategory(category, page);
    }
}
```

### Direct CacheProvider Usage

For advanced use cases, inject `CacheProvider` directly:

```java
@Service
public class OrderService {

    @Autowired
    private CacheProvider cacheProvider;

    @Autowired
    private OrderRepository orderRepository;

    public Order getOrder(String orderId) {
        String cacheKey = "order:" + orderId;

        // Try cache first
        Optional<Order> cached = cacheProvider.get("orders", cacheKey, Order.class);
        if (cached.isPresent()) {
            log.debug("Cache hit: {}", cacheKey);
            return cached.get();
        }

        // Cache miss - load from DB
        log.debug("Cache miss: {}", cacheKey);
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order != null) {
            // Store with 30 minute TTL
            cacheProvider.put("orders", cacheKey, order, Duration.ofMinutes(30));
        }

        return order;
    }

    // Complex caching logic with CacheProvider
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

### Static CacheManager Access (Legacy)

For cases requiring static access, use `CacheManager`:

```java
public class LegacyService {

    public Data getSomeData(String key) {
        // Static access without Spring DI
        CacheManager cacheManager = CacheManager.getInstance();

        return cacheManager.getOrCompute(
            "dataCache",
            key,
            () -> loadFromDatabase(key),
            Data.class
        );
    }

    private Data loadFromDatabase(String key) {
        // Database loading logic
        return database.load(key);
    }
}
```

**Note**: Prefer Spring dependency injection (`CacheService` or `CacheProvider`) when possible.

## Monitoring

### Health Check

The module provides a health indicator:

```bash
curl http://localhost:8080/actuator/health/cache
```

Response:
```json
{
  "status": "UP",
  "details": {
    "strategy": "RedisCacheStrategy",
    "available": true
  }
}
```

### Metrics

Cache metrics are automatically collected:

```bash
curl http://localhost:8080/actuator/metrics/cache.hits
```

Available metrics:
- `cache.hits` - Number of cache hits
- `cache.misses` - Number of cache misses
- `cache.evictions` - Number of evicted entries
- `cache.hit.ratio` - Hit rate percentage

### Logging

Enable debug logging to see cache operations:

```yaml
logging:
  level:
    dev.simplecore.simplix.cache: DEBUG
```

## Environment Variables

The module supports configuration via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `CACHE_MODE` | Cache strategy (local/redis) | local |
| `CACHE_DEFAULT_TTL` | Default TTL (seconds) | 3600 |
| `CACHE_MAX_SIZE` | Maximum cache size | 10000 |
| `CACHE_NULL_VALUES` | Cache null values | false |
| `CACHE_METRICS_ENABLED` | Enable metrics | true |
| `CACHE_REDIS_PREFIX` | Redis key prefix | cache: |
| `REDIS_HOST` | Redis server host | localhost |
| `REDIS_PORT` | Redis server port | 6379 |
| `REDIS_PASSWORD` | Redis password | (empty) |

## Profiles

### Development Profile

```yaml
spring:
  profiles:
    active: development

# Auto-configured:
# - Local cache
# - 5 minute TTL
# - Metrics disabled
```

### Production Profile

```yaml
spring:
  profiles:
    active: production

# Auto-configured:
# - Redis cache
# - 1 hour TTL
# - Metrics enabled
# - Redis SSL
```

### Test Profile

```yaml
spring:
  profiles:
    active: test

# Auto-configured:
# - Local cache
# - 1 minute TTL
# - Small cache size
```

## Migration Guide

### From Spring Cache Annotations

Before:
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

After:
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

### From Direct Redis Usage

Before:
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

After:
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

## Troubleshooting

### Issue: Cache Not Working

1. Verify cache module is on classpath
2. Check configuration in `application.yml`
3. Enable debug logging to see cache operations
4. Check health endpoint: `/actuator/health/cache`

### Issue: Redis Connection Failed

1. Verify Redis is running: `redis-cli ping`
2. Check connection settings in configuration
3. Test connection: `redis-cli -h host -p port`
4. Check firewall/security group settings

### Issue: High Memory Usage

1. Reduce `max-size` for local caches
2. Decrease TTL values
3. Monitor eviction rates
4. Consider using Redis instead of local cache

### Issue: High Cache Miss Rate

1. Increase TTL for stable data
2. Pre-warm cache on startup
3. Verify keys are consistent
4. Monitor caches with low hit rates

## Performance Tips

1. **Use Appropriate TTL**: Balance freshness with performance
2. **Monitor Hit Rates**: Target 80%+ hit rate
3. **Size Limits**: Set reasonable max size to prevent OOM
4. **Key Design**: Use consistent and predictable cache keys
5. **Batch Operations**: Use `putAll` and `getAll` for bulk operations
6. **Async Loading**: Consider async cache loading for expensive operations

## Documentation

- [Korean Documentation (한국어 문서)](docs/README_ko.md)

## License

This project is developed for internal use.