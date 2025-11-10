package dev.simplecore.simplix.hibernate.cache.strategy;

import dev.simplecore.simplix.hibernate.cache.core.CacheMode;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

/**
 * Strategy for cache eviction based on cache mode and available providers
 */
@Slf4j
@RequiredArgsConstructor
public class CacheEvictionStrategy implements CacheProvider.CacheEvictionEventListener {

    private final HibernateCacheManager cacheManager;
    private final ApplicationEventPublisher eventPublisher;
    private final CacheProviderFactory providerFactory;

    @Value("${hibernate.cache.mode:AUTO}")
    private String cacheMode;

    @Value("${hibernate.cache.provider.type:AUTO}")
    private String providerType;

    @Value("${hibernate.cache.node.id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String nodeId;

    private CacheProvider activeProvider;

    @PostConstruct
    public void initialize() {
        // Select provider based on configuration
        if ("AUTO".equalsIgnoreCase(providerType)) {
            activeProvider = providerFactory.selectBestAvailable();
        } else {
            activeProvider = providerFactory.getProvider(providerType);
        }

        // Initialize and subscribe to evictions
        activeProvider.initialize();
        activeProvider.subscribeToEvictions(this);

        log.info("✔ Cache eviction strategy initialized with provider: {}", activeProvider.getType());
    }

    @PreDestroy
    public void shutdown() {
        if (activeProvider != null) {
            activeProvider.shutdown();
        }
    }

    /**
     * Handle cache eviction based on configured mode
     */
    public void evict(Class<?> entityClass, Object entityId) {
        CacheMode mode = determineCacheMode();

        switch (mode) {
            case LOCAL -> evictLocal(entityClass, entityId);
            case DISTRIBUTED -> evictDistributed(entityClass, entityId);
            case HYBRID -> evictHybrid(entityClass, entityId);
            case DISABLED -> log.debug("Cache disabled, skipping eviction");
        }
    }

    private void evictLocal(Class<?> entityClass, Object entityId) {
        if (entityId != null) {
            cacheManager.evictEntity(entityClass, entityId);
        }
        cacheManager.evictEntityCache(entityClass);
    }

    private void evictDistributed(Class<?> entityClass, Object entityId) {
        // Evict local cache first
        evictLocal(entityClass, entityId);

        // Broadcast eviction to other nodes
        broadcastEviction(entityClass, entityId);
    }

    private void evictHybrid(Class<?> entityClass, Object entityId) {
        // Same as distributed for now
        evictDistributed(entityClass, entityId);
    }

    private void broadcastEviction(Class<?> entityClass, Object entityId) {
        CacheEvictionEvent event = CacheEvictionEvent.builder()
                .entityClass(entityClass.getName())
                .entityId(entityId != null ? entityId.toString() : null)
                .nodeId(nodeId)
                .timestamp(System.currentTimeMillis())
                .build();

        activeProvider.broadcastEviction(event);
    }

    /**
     * Handle remote cache eviction events from other nodes
     */
    @Override
    public void onEvictionEvent(CacheEvictionEvent event) {
        // Only process events from other nodes
        if (nodeId.equals(event.getNodeId())) {
            return;
        }

        try {
            Class<?> entityClass = Class.forName(event.getEntityClass());

            if (event.getEntityId() != null) {
                cacheManager.evictEntity(entityClass, event.getEntityId());
            } else {
                cacheManager.evictEntityCache(entityClass);
            }

            if (event.getRegion() != null) {
                cacheManager.evictRegion(event.getRegion());
            }

            log.debug("✔ Processed remote cache eviction from node {} via {}",
                    event.getNodeId(), activeProvider.getType());

        } catch (Exception e) {
            log.error("✖ Failed to process remote cache eviction", e);
        }
    }

    /**
     * Listen for local cache eviction events
     */
    @EventListener
    public void handleLocalCacheEviction(CacheEvictionEvent event) {
        // Broadcast local events to other nodes if in distributed mode
        if (determineCacheMode() != CacheMode.LOCAL && nodeId.equals(event.getNodeId())) {
            activeProvider.broadcastEviction(event);
        }
    }

    private CacheMode determineCacheMode() {
        // Parse configured mode
        if ("AUTO".equalsIgnoreCase(cacheMode)) {
            // Auto-detect based on provider
            String providerType = activeProvider.getType();
            if ("LOCAL".equals(providerType)) {
                return CacheMode.LOCAL;
            } else {
                return CacheMode.DISTRIBUTED;
            }
        }

        try {
            return CacheMode.valueOf(cacheMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("⚠ Invalid cache mode: {}, defaulting to LOCAL", cacheMode);
            return CacheMode.LOCAL;
        }
    }

    /**
     * Get current provider information
     */
    public String getActiveProviderInfo() {
        if (activeProvider != null) {
            CacheProvider.CacheProviderStats stats = activeProvider.getStats();
            return String.format("Provider: %s, Connected: %s, Sent: %d, Received: %d",
                    activeProvider.getType(),
                    stats.connected(),
                    stats.evictionsSent(),
                    stats.evictionsReceived());
        }
        return "No active provider";
    }
}