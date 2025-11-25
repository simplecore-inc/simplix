package dev.simplecore.simplix.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

/**
 * Core Cache Manager
 * Manages cache providers using Service Provider Interface (SPI)
 * This allows modules to use caching without depending on cache implementation
 *
 * <h3>Usage Examples:</h3>
 *
 * <h4>1. Basic Usage - Get Instance and Store/Retrieve Values:</h4>
 * <pre>{@code
 * CacheManager cacheManager = CacheManager.getInstance();
 *
 * // Store a value in cache
 * String key = "user:123";
 * User user = new User("John", "Doe");
 * cacheManager.put("userCache", key, user);
 *
 * // Retrieve value from cache
 * Optional<User> cachedUser = cacheManager.get("userCache", key, User.class);
 * if (cachedUser.isPresent()) {
 *     System.out.println("Found user: " + cachedUser.get().getName());
 * }
 * }</pre>
 *
 * <h4>2. Store with TTL (Time To Live):</h4>
 * <pre>{@code
 * CacheManager cacheManager = CacheManager.getInstance();
 *
 * // Store value with 1 hour TTL
 * String sessionKey = "session:abc123";
 * SessionData session = new SessionData();
 * cacheManager.put("sessionCache", sessionKey, session, Duration.ofHours(1));
 *
 * // Store value with 30 minutes TTL
 * String tempKey = "temp:data";
 * cacheManager.put("tempCache", tempKey, "temporary data", Duration.ofMinutes(30));
 * }</pre>
 *
 * <h4>3. Get or Compute Pattern (Cache-aside):</h4>
 * <pre>{@code
 * CacheManager cacheManager = CacheManager.getInstance();
 *
 * // Automatically load from database if not in cache
 * String userId = "user:456";
 * User user = cacheManager.getOrCompute(
 *     "userCache",           // Cache name
 *     userId,                // Cache key
 *     () -> {                // Value loader (called if not in cache)
 *         return userRepository.findById("456");
 *     },
 *     User.class             // Return type
 * );
 * }</pre>
 *
 * <h4>4. Cache Eviction:</h4>
 * <pre>{@code
 * CacheManager cacheManager = CacheManager.getInstance();
 *
 * // Remove single entry from cache
 * cacheManager.evict("userCache", "user:123");
 *
 * // Clear entire cache
 * cacheManager.clear("userCache");
 * }</pre>
 *
 * <h4>5. Check Cache Availability:</h4>
 * <pre>{@code
 * CacheManager cacheManager = CacheManager.getInstance();
 *
 * // Check if cache provider is available
 * if (cacheManager.isAvailable()) {
 *     System.out.println("Using cache provider: " + cacheManager.getProviderName());
 * } else {
 *     System.out.println("Cache is not available");
 * }
 *
 * // Check if specific key exists in cache
 * boolean exists = cacheManager.exists("userCache", "user:123");
 * }</pre>
 *
 * <h4>6. Real-world Service Example:</h4>
 * <pre>{@code
 * public class UserService {
 *     private final CacheManager cacheManager = CacheManager.getInstance();
 *     private static final String CACHE_NAME = "users";
 *     private static final Duration CACHE_TTL = Duration.ofHours(2);
 *
 *     public User getUser(String userId) {
 *         String cacheKey = "user:" + userId;
 *
 *         // Try to get from cache first, otherwise load from database
 *         return cacheManager.getOrCompute(
 *             CACHE_NAME,
 *             cacheKey,
 *             () -> loadUserFromDatabase(userId),
 *             User.class
 *         );
 *     }
 *
 *     public void updateUser(User user) {
 *         // Update in database
 *         saveUserToDatabase(user);
 *
 *         // Update cache with new data
 *         String cacheKey = "user:" + user.getId();
 *         cacheManager.put(CACHE_NAME, cacheKey, user, CACHE_TTL);
 *     }
 *
 *     public void deleteUser(String userId) {
 *         // Delete from database
 *         deleteUserFromDatabase(userId);
 *
 *         // Remove from cache
 *         String cacheKey = "user:" + userId;
 *         cacheManager.evict(CACHE_NAME, cacheKey);
 *     }
 * }
 * }</pre>
 *
 * <h3>Configuration:</h3>
 * <p>The CacheManager uses SPI (Service Provider Interface) to discover cache providers.
 * To add a cache provider implementation:</p>
 * <ol>
 *   <li>Implement the {@link CacheProvider} interface</li>
 *   <li>Add your implementation class to META-INF/services/dev.simplecore.simplix.core.cache.CacheProvider</li>
 *   <li>The provider with highest priority will be selected automatically</li>
 * </ol>
 *
 * <h3>Thread Safety:</h3>
 * <p>CacheManager is thread-safe and uses a singleton pattern. All methods handle exceptions
 * gracefully and will fall back to direct computation if cache operations fail.</p>
 *
 * @see CacheProvider
 * @since 1.0
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);
    private static final CacheManager INSTANCE = new CacheManager();
    private final CacheProvider provider;
    private static final String NO_CACHE_PROVIDER = "NoOpCacheProvider";

    private CacheManager() {
        this.provider = loadProvider();
        log.info("CacheManager initialized with provider: {}", provider.getName());
    }

    public static CacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load cache provider using SPI
     */
    private CacheProvider loadProvider() {
        ServiceLoader<CacheProvider> loader = ServiceLoader.load(CacheProvider.class);
        
        List<CacheProvider> providers = new ArrayList<>();
        for (CacheProvider provider : loader) {
            providers.add(provider);
            log.debug("Found cache provider: {} with priority: {}", 
                provider.getName(), provider.getPriority());
        }

        if (providers.isEmpty()) {
            log.warn("No cache provider found via SPI, using NoOp provider");
            return new NoOpCacheProvider();
        }

        // Sort by priority (highest first)
        providers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        CacheProvider selected = providers.get(0);
        log.info("Selected cache provider: {} with priority: {}", 
            selected.getName(), selected.getPriority());
        
        return selected;
    }

    public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
        try {
            return provider.get(cacheName, key, type);
        } catch (Exception e) {
            log.trace("Cache get failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
            return Optional.empty();
        }
    }

    public <T> void put(String cacheName, Object key, T value) {
        try {
            provider.put(cacheName, key, value);
        } catch (Exception e) {
            log.trace("Cache put failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
        }
    }

    public <T> void put(String cacheName, Object key, T value, Duration ttl) {
        try {
            provider.put(cacheName, key, value, ttl);
        } catch (Exception e) {
            log.trace("Cache put with TTL failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
        }
    }

    public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
        try {
            return provider.getOrCompute(cacheName, key, valueLoader, type);
        } catch (Exception e) {
            log.trace("Cache getOrCompute failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
            try {
                return valueLoader.call();
            } catch (Exception ex) {
                log.error("Value loader failed: {}", ex.getMessage(), ex);
                return null;
            }
        }
    }

    public void evict(String cacheName, Object key) {
        try {
            provider.evict(cacheName, key);
        } catch (Exception e) {
            log.trace("Cache evict failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
        }
    }

    public void clear(String cacheName) {
        try {
            provider.clear(cacheName);
        } catch (Exception e) {
            log.trace("Cache clear failed for cache {}: {}", cacheName, e.getMessage());
        }
    }

    public boolean exists(String cacheName, Object key) {
        try {
            return provider.exists(cacheName, key);
        } catch (Exception e) {
            log.trace("Cache exists check failed for key {} in cache {}: {}", 
                key, cacheName, e.getMessage());
            return false;
        }
    }

    public boolean isAvailable() {
        return provider.isAvailable();
    }

    public String getProviderName() {
        return provider.getName();
    }

    /**
     * Get the underlying cache provider (for testing purposes)
     */
    protected CacheProvider getProvider() {
        return provider;
    }

    /**
     * No-operation cache provider (used when no real provider is available)
     */
    private static class NoOpCacheProvider implements CacheProvider {

        @Override
        public <T> Optional<T> get(String cacheName, Object key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public <T> void put(String cacheName, Object key, T value) {
            // No-op
        }

        @Override
        public <T> void put(String cacheName, Object key, T value, Duration ttl) {
            // No-op
        }

        @Override
        public <T> T getOrCompute(String cacheName, Object key, Callable<T> valueLoader, Class<T> type) {
            try {
                return valueLoader.call();
            } catch (Exception e) {
                log.error("Value loader failed in NoOp provider: {}", e.getMessage());
                return null;
            }
        }

        @Override
        public void evict(String cacheName, Object key) {
            // No-op
        }

        @Override
        public void clear(String cacheName) {
            // No-op
        }

        @Override
        public boolean exists(String cacheName, Object key) {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getName() {
            return NO_CACHE_PROVIDER;
        }
    }
}