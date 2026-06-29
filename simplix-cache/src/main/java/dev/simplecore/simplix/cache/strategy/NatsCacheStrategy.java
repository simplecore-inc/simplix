package dev.simplecore.simplix.cache.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.cache.config.CacheProperties;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * NATS JetStream KV-based cache strategy.
 *
 * <p>Each cache name is mapped to a separate JetStream KV bucket named
 * {@code <bucketPrefix><sanitized-cacheName>}. NATS KV applies a single
 * {@code maxAge} per bucket — when a bucket is created (idempotently, on
 * first access), the per-cache TTL from
 * {@code cacheConfigs} is used (falling back to
 * {@code defaultTtlSeconds}). Once a bucket exists,
 * this strategy does not mutate it.
 *
 * <p><b>Per-call TTL is silently ignored.</b> The {@code Duration ttl}
 * parameter on {@link #put(String, Object, Object, Duration)},
 * {@link #getOrCompute(String, Object, java.util.concurrent.Callable, Class, Duration)}
 * and {@link #putAll(String, java.util.Map, Duration)} is not honoured —
 * NATS JetStream KV does not support per-key TTLs, only bucket-level
 * {@code maxAge}. Configure per-cache retention via
 * {@code simplix.cache.cache-configs.<name>.ttl-seconds} instead.
 *
 * <p>This implementation is suitable for multi-instance deployments that
 * already use NATS for messaging and want to drop Redis from the runtime.
 */
@Slf4j
public class NatsCacheStrategy implements CacheStrategy {

    private final Connection connection;
    private final CacheProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentMap<String, KeyValue> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> hits = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> misses = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> puts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> removals = new ConcurrentHashMap<>();

    public NatsCacheStrategy(Connection connection, CacheProperties properties, ObjectMapper objectMapper) {
        this.connection = connection;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "NatsCacheStrategy";
    }

    @Override
    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        try {
            KeyValue kv = bucketFor(cacheName);
            KeyValueEntry entry = kv.get(toKey(key));
            if (entry == null || entry.getValue() == null) {
                counter(misses, cacheName).increment();
                log.trace("NATS cache miss for key {} in cache {}", key, cacheName);
                return Optional.empty();
            }
            counter(hits, cacheName).increment();
            T value = deserialize(entry.getValue(), type);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.error("Failed to get value from NATS KV for key {} in cache {}", key, cacheName, e);
            return Optional.empty();
        }
    }

    @Override
    public <T> void put(String cacheName, Object key, T value) {
        put(cacheName, key, value, ttlForCache(cacheName));
    }

    @Override
    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        if (value == null) {
            log.debug("Skipping null value for key {} in cache {}", key, cacheName);
            return;
        }

        try {
            KeyValue kv = bucketFor(cacheName);
            byte[] serialized = serialize(value);
            kv.put(toKey(key), serialized);
            counter(puts, cacheName).increment();
            warnPerCallTtlIgnored(cacheName, ttl);
            log.trace("Put key {} in NATS cache {}", key, cacheName);
        } catch (Exception e) {
            log.error("Failed to put value in NATS KV for key {} in cache {}", key, cacheName, e);
        }
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        return getOrCompute(cacheName, key, valueLoader, type, ttlForCache(cacheName));
    }

    @Override
    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader,
                               Class<T> type, Duration ttl) {
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
        try {
            KeyValue kv = bucketFor(cacheName);
            kv.delete(toKey(key));
            counter(removals, cacheName).increment();
            log.trace("Evicted key {} from NATS cache {}", key, cacheName);
        } catch (Exception e) {
            log.error("Failed to evict key {} from NATS cache {}", key, cacheName, e);
        }
    }

    @Override
    public void evictAll(String cacheName, Collection<?> keys) {
        try {
            KeyValue kv = bucketFor(cacheName);
            for (Object key : keys) {
                kv.delete(toKey(key));
                counter(removals, cacheName).increment();
            }
        } catch (Exception e) {
            log.error("Failed to evict keys from NATS cache {}", cacheName, e);
        }
    }

    @Override
    public void clear(String cacheName) {
        try {
            KeyValue kv = bucketFor(cacheName);
            List<String> keys = kv.keys();
            if (keys != null) {
                for (String key : keys) {
                    kv.delete(key);
                    counter(removals, cacheName).increment();
                }
                log.debug("Cleared {} entries from NATS cache {}", keys.size(), cacheName);
            }
        } catch (Exception e) {
            log.error("Failed to clear NATS cache {}", cacheName, e);
        }
    }

    @Override
    public void clearAll() {
        log.warn("Clearing all NATS caches managed by this strategy");
        for (String cacheName : buckets.keySet()) {
            clear(cacheName);
        }
    }

    @Override
    public boolean exists(String cacheName, Object key) {
        try {
            KeyValue kv = bucketFor(cacheName);
            KeyValueEntry entry = kv.get(toKey(key));
            return entry != null && entry.getValue() != null;
        } catch (Exception e) {
            log.error("Failed to check existence of key {} in NATS cache {}", key, cacheName, e);
            return false;
        }
    }

    @Override
    public Collection<Object> getKeys(String cacheName) {
        try {
            KeyValue kv = bucketFor(cacheName);
            List<String> keys = kv.keys();
            return keys == null ? Collections.emptyList() : List.copyOf(keys);
        } catch (Exception e) {
            log.error("Failed to list keys for NATS cache {}", cacheName, e);
            return Collections.emptyList();
        }
    }

    @Override
    public <T> Map<Object, T> getAll(String cacheName, Class<T> type) {
        Map<Object, T> result = new HashMap<>();
        try {
            KeyValue kv = bucketFor(cacheName);
            List<String> keys = kv.keys();
            if (keys == null) {
                return result;
            }
            for (String key : keys) {
                KeyValueEntry entry = kv.get(key);
                if (entry != null && entry.getValue() != null) {
                    try {
                        result.put(key, deserialize(entry.getValue(), type));
                    } catch (Exception e) {
                        log.error("Failed to deserialize value for key {} in NATS cache {}", key, cacheName, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read all entries from NATS cache {}", cacheName, e);
        }
        return result;
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries) {
        putAll(cacheName, entries, ttlForCache(cacheName));
    }

    @Override
    public <T> void putAll(String cacheName, Map<Object, T> entries, Duration ttl) {
        try {
            KeyValue kv = bucketFor(cacheName);
            int count = 0;
            for (Map.Entry<Object, T> e : entries.entrySet()) {
                if (e.getValue() == null) continue;
                try {
                    kv.put(toKey(e.getKey()), serialize(e.getValue()));
                    counter(puts, cacheName).increment();
                    count++;
                } catch (Exception ex) {
                    log.error("Failed to serialize/put key {} in NATS cache {}", e.getKey(), cacheName, ex);
                }
            }
            if (count > 0) {
                log.trace("Put {} entries in NATS cache {}", count, cacheName);
            }
        } catch (Exception e) {
            log.error("Failed putAll on NATS cache {}", cacheName, e);
        }
    }

    @Override
    public CacheStatistics getStatistics(String cacheName) {
        long h = sum(hits, cacheName);
        long m = sum(misses, cacheName);
        long p = sum(puts, cacheName);
        long r = sum(removals, cacheName);
        long size = 0;
        try {
            KeyValue kv = bucketFor(cacheName);
            List<String> keys = kv.keys();
            size = keys == null ? 0 : keys.size();
        } catch (Exception e) {
            log.trace("Failed to read size for NATS cache {}: {}", cacheName, e.getMessage());
        }
        double hitRate = (h + m) > 0 ? (double) h / (h + m) : 0.0;
        return new CacheStatistics(h, m, 0L, p, r, hitRate, size, 0L);
    }

    @Override
    public void initialize() {
        try {
            connection.flush(Duration.ofSeconds(2));
            log.info("NATS cache strategy initialized");
        } catch (Exception e) {
            log.warn("NATS cache strategy initialization completed with warning: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        buckets.clear();
        log.info("NATS cache strategy shutdown complete");
    }

    @Override
    public boolean isAvailable() {
        try {
            return connection.getStatus() == Connection.Status.CONNECTED;
        } catch (Exception e) {
            return false;
        }
    }

    private KeyValue bucketFor(String cacheName) throws IOException, JetStreamApiException {
        KeyValue existing = buckets.get(cacheName);
        if (existing != null) {
            return existing;
        }
        synchronized (buckets) {
            existing = buckets.get(cacheName);
            if (existing != null) {
                return existing;
            }
            String bucketName = bucketName(cacheName);
            ensureBucket(bucketName, ttlForCache(cacheName));
            KeyValue kv = connection.keyValue(bucketName);
            buckets.put(cacheName, kv);
            return kv;
        }
    }

    private void ensureBucket(String bucketName, Duration maxAge) throws IOException {
        try {
            KeyValueManagement mgmt = connection.keyValueManagement();
            KeyValueConfiguration config = KeyValueConfiguration.builder()
                    .name(bucketName)
                    .maxHistoryPerKey(1)
                    .ttl(maxAge)
                    .replicas(properties.getNats().getReplicas())
                    .build();
            mgmt.create(config);
            log.info("Created NATS KV bucket [name='{}', ttl={}]", bucketName, maxAge);
        } catch (JetStreamApiException e) {
            log.debug("NATS KV bucket '{}' already exists or could not be created: {}",
                    bucketName, e.getMessage());
        }
    }

    private String bucketName(String cacheName) {
        return properties.getNats().getBucketPrefix() + sanitize(cacheName);
    }

    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean valid = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            sb.append(valid ? c : '_');
        }
        return sb.toString();
    }

    private static String toKey(Object key) {
        return key == null ? "null" : sanitize(key.toString());
    }

    private Duration ttlForCache(String cacheName) {
        CacheProperties.CacheConfig cfg = properties.getCacheConfigs().get(cacheName);
        long seconds = cfg != null ? cfg.getTtlSeconds() : properties.getDefaultTtlSeconds();
        return Duration.ofSeconds(seconds);
    }

    private void warnPerCallTtlIgnored(String cacheName, Duration callerTtl) {
        if (callerTtl == null) return;
        Duration bucketTtl = ttlForCache(cacheName);
        if (!callerTtl.equals(bucketTtl)) {
            log.debug("NATS KV does not support per-key TTL — ignoring caller-supplied ttl={} "
                    + "for cache '{}' (bucket maxAge={}). Configure simplix.cache.cache-configs.{}.ttl-seconds instead.",
                    callerTtl, cacheName, bucketTtl, cacheName);
        }
    }

    private byte[] serialize(Object value) throws IOException {
        return objectMapper.writeValueAsBytes(value);
    }

    private <T> T deserialize(byte[] bytes, Class<T> type) throws IOException {
        if (bytes == null) return null;
        return objectMapper.readValue(bytes, type);
    }

    private static LongAdder counter(ConcurrentMap<String, LongAdder> map, String cacheName) {
        return map.computeIfAbsent(cacheName, k -> new LongAdder());
    }

    private static long sum(ConcurrentMap<String, LongAdder> map, String cacheName) {
        LongAdder adder = map.get(cacheName);
        return adder == null ? 0L : adder.sum();
    }
}
