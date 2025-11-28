package dev.simplecore.simplix.cache.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.cache.config.CacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis Cache Strategy
 * Suitable for multi-instance deployments with distributed caching
 */
@Slf4j
public class RedisCacheStrategy implements CacheStrategy {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;
    private static final String KEY_SEPARATOR = "::";

    public RedisCacheStrategy(StringRedisTemplate redisTemplate, CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String getName() {
        return "RedisCacheStrategy";
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        String redisKey = buildKey(cacheName, key);

        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            if (value != null) {
                log.trace("Redis cache hit for key {} in cache {}", key, cacheName);
                T result = deserialize(value, type);
                return Optional.ofNullable(result);
            }
            log.trace("Redis cache miss for key {} in cache {}", key, cacheName);
        } catch (Exception e) {
            log.error("Failed to get value from Redis for key {} in cache {}", key, cacheName, e);
        }

        return Optional.empty();
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        put(cacheName, key, value, Duration.ofHours(1));
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        if (value == null) {
            log.debug("Skipping null value for key {} in cache {}", key, cacheName);
            return;
        }

        String redisKey = buildKey(cacheName, key);

        try {
            String serialized = serialize(value);
            redisTemplate.opsForValue().set(redisKey, serialized, ttl);
            log.trace("Put key {} in Redis cache {} with TTL {}", key, cacheName, ttl);
        } catch (Exception e) {
            log.error("Failed to put value in Redis for key {} in cache {}", key, cacheName, e);
        }
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        return getOrCompute(cacheName, key, valueLoader, type, Duration.ofHours(1));
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type, Duration ttl) {
        Optional<T> cached = get(cacheName, key, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        try {
            T value = valueLoader.call();
            if (value != null) {
                put(cacheName, key, value, ttl);
            }
            return value;
        } catch (Exception e) {
            log.error("Failed to compute value for key {} in cache {}", key, cacheName, e);
            throw new RuntimeException("Cache value computation failed", e);
        }
    }

    @Override
    public void evict(String cacheName, Object key) {
        String redisKey = buildKey(cacheName, key);
        Boolean deleted = redisTemplate.delete(redisKey);
        if (deleted) {
            log.trace("Evicted key {} from Redis cache {}", key, cacheName);
        }
    }

    @Override
    public void evictAll(String cacheName, Collection<?> keys) {
        Set<String> redisKeys = keys.stream()
            .map(key -> buildKey(cacheName, key))
            .collect(Collectors.toSet());

        Long deleted = redisTemplate.delete(redisKeys);
        log.trace("Evicted {} keys from Redis cache {}", deleted, cacheName);
    }

    @Override
    public void clear(String cacheName) {
        String pattern = buildKeyPattern(cacheName);
        Set<String> keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            Long deleted = redisTemplate.delete(keys);
            log.debug("Cleared {} entries from Redis cache {}", deleted, cacheName);
        }
    }

    @Override
    public void clearAll() {
        log.warn("Clearing all Redis caches - this affects all applications using this Redis instance!");
        try {
            Set<String> keys = redisTemplate.keys("*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Cleared {} total entries from Redis", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to clear all Redis caches", e);
        }
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        String redisKey = buildKey(cacheName, key);
        return redisTemplate.hasKey(redisKey);
    }

    @Override
    public Collection<Object> getKeys(String cacheName) {
        String pattern = buildKeyPattern(cacheName);
        Set<String> keys = redisTemplate.keys(pattern);
		String prefix = getFullPrefix(cacheName);
		return keys.stream()
			.map(k -> k.substring(prefix.length()))
			.collect(Collectors.toSet());
	}

    @Override
	public <T> Map<Object, T> getAll(String cacheName, Class<T> type) {
        Map<Object, T> result = new HashMap<>();
        String pattern = buildKeyPattern(cacheName);
        Set<String> keys = redisTemplate.keys(pattern);

        if (!keys.isEmpty()) {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values != null) {
                Iterator<String> keyIterator = keys.iterator();
                Iterator<String> valueIterator = values.iterator();
                String prefix = getFullPrefix(cacheName);

                while (keyIterator.hasNext() && valueIterator.hasNext()) {
                    String redisKey = keyIterator.next();
                    String value = valueIterator.next();

                    if (value != null) {
                        try {
                            String originalKey = redisKey.substring(prefix.length());
                            T deserializedValue = deserialize(value, type);
                            result.put(originalKey, deserializedValue);
                        } catch (Exception e) {
                            log.error("Failed to deserialize value for key {}", redisKey, e);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries) {
        putAll(cacheName, entries, Duration.ofHours(1));
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries, Duration ttl) {
        Map<String, String> serializedEntries = new HashMap<>();

        entries.forEach((key, value) -> {
            if (value != null) {
                try {
                    String redisKey = buildKey(cacheName, key);
                    String serialized = serialize(value);
                    serializedEntries.put(redisKey, serialized);
                } catch (Exception e) {
                    log.error("Failed to serialize value for key {} in cache {}", key, cacheName, e);
                }
            }
        });

        if (!serializedEntries.isEmpty()) {
            redisTemplate.opsForValue().multiSet(serializedEntries);

            // Set TTL for each key
            serializedEntries.keySet().forEach(key ->
                redisTemplate.expire(key, ttl.toMillis(), TimeUnit.MILLISECONDS)
            );

            log.trace("Put {} entries in Redis cache {}", serializedEntries.size(), cacheName);
        }
    }

    @Override
    public CacheStatistics getStatistics(String cacheName) {
        try {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            if (factory == null) {
                log.warn("Cannot get statistics: RedisConnectionFactory is null");
                return CacheStatistics.empty();
            }

            RedisConnection connection = factory.getConnection();

			Properties info = connection.serverCommands().info("stats");

			assert info != null;
			long hits = getLongProperty(info, "keyspace_hits");
            long misses = getLongProperty(info, "keyspace_misses");
            long evictions = getLongProperty(info, "evicted_keys");

            String pattern = buildKeyPattern(cacheName);
            Set<String> keys = redisTemplate.keys(pattern);
            long size = keys.size();

            double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0.0;

            return new CacheStatistics(
                hits,
                misses,
                evictions,
                0, // puts not tracked by Redis
                0, // removals not tracked separately
                hitRate,
                size,
                0  // memory usage requires additional Redis commands
            );
        } catch (Exception e) {
            log.error("Failed to get Redis statistics for cache {}", cacheName, e);
            return CacheStatistics.empty();
        }
    }

    @Override
    public void initialize() {
        try {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            if (factory == null) {
                throw new IllegalStateException("RedisConnectionFactory is null");
            }

            RedisConnection connection = factory.getConnection();

			connection.ping();
            log.info("Redis cache strategy initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Redis cache strategy", e);
            throw new RuntimeException("Redis connection failed", e);
        }
    }

    @Override
    public void shutdown() {
        log.info("Redis cache strategy shutdown complete");
    }

    @Override
    public boolean isAvailable() {
        try {
            RedisConnectionFactory factory = redisTemplate.getConnectionFactory();
            if (factory == null) {
                log.debug("Redis is not available: RedisConnectionFactory is null");
                return false;
            }

            RedisConnection connection = factory.getConnection();

			connection.ping();
            return true;
        } catch (Exception e) {
            log.debug("Redis is not available: {}", e.getMessage());
            return false;
        }
    }

    private String buildKey(String cacheName, Object key) {
        String baseKey = cacheName + KEY_SEPARATOR + key.toString();

        // Apply Redis key prefix if configured
        if (properties.getRedis().isUseKeyPrefix()) {
            return properties.getRedis().getKeyPrefix() + baseKey;
        }

        return baseKey;
    }

    private String buildKeyPattern(String cacheName) {
        String pattern = cacheName + KEY_SEPARATOR + "*";

        // Apply Redis key prefix if configured
        if (properties.getRedis().isUseKeyPrefix()) {
            return properties.getRedis().getKeyPrefix() + pattern;
        }

        return pattern;
    }

    private String getFullPrefix(String cacheName) {
        String prefix = cacheName + KEY_SEPARATOR;

        // Apply Redis key prefix if configured
        if (properties.getRedis().isUseKeyPrefix()) {
            return properties.getRedis().getKeyPrefix() + prefix;
        }

        return prefix;
    }

    private <T> String serialize(T value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T deserialize(String value, Class<T> type) throws Exception {
        return objectMapper.readValue(value, type);
    }

    private long getLongProperty(Properties props, String key) {
        String value = props.getProperty(key);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.trace("Failed to parse {} as long: {}", key, value);
            }
        }
        return 0;
    }
}