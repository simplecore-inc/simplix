package dev.simplecore.simplix.hibernate.cache.provider;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import lombok.extern.slf4j.Slf4j;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.transport.Address;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Infinispan-based distributed cache provider for Hibernate L2 cache eviction synchronization.
 * <p>
 * Uses Infinispan's embedded cache manager with cluster-aware event distribution.
 * Supports both replicated and distributed cache configurations.
 * This provider is auto-configured by DistributedCacheAutoConfiguration
 * when Infinispan is available.
 */
@Slf4j
public class InfinispanCacheProvider implements CacheProvider {

    private static final String CACHE_NAME = "simplix-hibernate-eviction-cache";

    private final EmbeddedCacheManager cacheManager;
    private final String nodeId;

    private final AtomicLong evictionsSent = new AtomicLong(0);
    private final AtomicLong evictionsReceived = new AtomicLong(0);

    private volatile org.infinispan.Cache<String, CacheEvictionEvent> evictionCache;
    private volatile CacheEvictionEventListener eventListener;
    private volatile ClusterViewListener clusterViewListener;
    private volatile Thread cleanupThread;
    private volatile boolean initialized = false;
    private final AtomicBoolean shutdownSignal = new AtomicBoolean(false);

    // Track processed events to prevent duplicate processing
    // Limited size to prevent unbounded memory growth
    private static final int MAX_PROCESSED_EVENTS_SIZE = 10000;
    private final Map<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long EVENT_EXPIRY_MS = 60000; // 1 minute

    public InfinispanCacheProvider(EmbeddedCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        Address localAddress = cacheManager.getAddress();
        this.nodeId = extractNodeId(localAddress);
    }

    /**
     * Safely extract node ID from address, handling null and short strings.
     */
    private static String extractNodeId(Address address) {
        if (address == null) {
            return "embedded";
        }
        String addressStr = address.toString();
        if (addressStr == null || addressStr.isEmpty()) {
            return "embedded";
        }
        return addressStr.length() >= 8 ? addressStr.substring(0, 8) : addressStr;
    }

    @Override
    public String getType() {
        return "INFINISPAN";
    }

    @Override
    public boolean isAvailable() {
        try {
            return cacheManager.getStatus().allowInvocations();
        } catch (Exception e) {
            log.debug("Infinispan not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void broadcastEviction(CacheEvictionEvent event) {
        // Null check for event parameter
        if (event == null) {
            log.warn("⚠ Null eviction event received, skipping broadcast");
            return;
        }

        // Capture to local variable to prevent TOCTOU race condition
        var cache = evictionCache;
        if (!isAvailable() || cache == null) {
            // Throw exception so caller can handle retry/failover
            throw new IllegalStateException("Infinispan not available for eviction broadcast");
        }

        try {
            // Create copy with node ID (immutable event pattern - 7th review fix)
            CacheEvictionEvent eventWithNodeId = event.withNodeId(nodeId);

            // Use unique key for each event
            String eventKey = generateEventKey(eventWithNodeId);
            cache.put(eventKey, eventWithNodeId);
            evictionsSent.incrementAndGet();

            // Check size before adding to processedEvents
            if (processedEvents.size() < MAX_PROCESSED_EVENTS_SIZE) {
                processedEvents.put(eventKey, System.currentTimeMillis());
            }

            log.debug("Broadcast eviction event: entity={}, id={}",
                eventWithNodeId.getEntityClass(), eventWithNodeId.getEntityId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to broadcast eviction via Infinispan", e);
        }
    }

    @Override
    public synchronized void subscribeToEvictions(CacheEvictionEventListener listener) {
        // Synchronized to prevent race condition with initialize()/shutdown()
        this.eventListener = listener;

        // Capture to local variable for thread-safe access
        var cache = evictionCache;
        if (!isAvailable() || cache == null) {
            log.warn("⚠ Infinispan not available, cannot subscribe to evictions");
            return;
        }

        // Add cache listener for eviction events
        cache.addListener(new EvictionCacheListener(event -> {
            // Ignore events from this node
            if (Objects.equals(nodeId, event.getNodeId())) {
                return;
            }

            // Use putIfAbsent for atomic check-and-put to prevent race condition
            String eventKey = generateEventKey(event);

            // Check if already processed (duplicate event)
            if (processedEvents.containsKey(eventKey)) {
                return; // Already processed by another thread
            }

            // Evict oldest entries if map is full to maintain deduplication capability (C7 fix)
            if (processedEvents.size() >= MAX_PROCESSED_EVENTS_SIZE) {
                evictOldestProcessedEvents();
            }

            // Try to add - if already exists, it's a duplicate
            if (processedEvents.putIfAbsent(eventKey, System.currentTimeMillis()) != null) {
                return; // Duplicate detected during concurrent add
            }

            evictionsReceived.incrementAndGet();
            log.debug("Received eviction event from node {}: entity={}, id={}",
                event.getNodeId(), event.getEntityClass(), event.getEntityId());

            // Capture to local variable for thread-safe access
            CacheEvictionEventListener currentListener = eventListener;
            if (currentListener != null) {
                currentListener.onEvictionEvent(event);
            }
        }));

        log.info("Subscribed to Infinispan eviction cache: {}", CACHE_NAME);
    }

    @Override
    public synchronized void initialize() {
        // Synchronized to prevent race condition with shutdown()
        if (initialized) {
            return;
        }

        if (!isAvailable()) {
            log.warn("⚠ Infinispan not available during initialization");
            return;
        }

        // Get or create the eviction cache (M5: check existing cache first)
        evictionCache = cacheManager.getCache(CACHE_NAME);
        if (evictionCache == null) {
            // Define cache programmatically if not configured
            evictionCache = cacheManager.createCache(CACHE_NAME,
                new org.infinispan.configuration.cache.ConfigurationBuilder()
                    .clustering()
                        .cacheMode(org.infinispan.configuration.cache.CacheMode.REPL_ASYNC)
                    .expiration()
                        .lifespan(EVENT_EXPIRY_MS)
                    .build());
        }

        // Add cluster view listener
        clusterViewListener = new ClusterViewListener();
        cacheManager.addListener(clusterViewListener);

        initialized = true;

        int clusterSize = cacheManager.getMembers() != null ? cacheManager.getMembers().size() : 1;
        log.info("✔ Infinispan cache provider initialized (nodeId: {}, clusterSize: {})",
            nodeId, clusterSize);

        // Start cleanup task for processed events
        startCleanupTask();
    }

    @Override
    public synchronized void shutdown() {
        // Synchronized to prevent race condition with initialize()
        if (!initialized) {
            return; // Already shutdown
        }

        initialized = false;
        shutdownSignal.set(true); // Signal cleanup thread to stop

        // Wait for cleanup thread to terminate before clearing processedEvents
        Thread thread = cleanupThread;
        if (thread != null) {
            thread.interrupt();
            try {
                // Wait for cleanup thread to terminate gracefully
                thread.join(5000);
                if (thread.isAlive()) {
                    // First attempt timed out - try interrupting again more forcefully
                    log.warn("⚠ Cleanup thread did not terminate within 5s, attempting second interrupt");
                    thread.interrupt();
                    thread.join(2000); // Give it 2 more seconds

                    if (thread.isAlive()) {
                        // Thread is stuck - log error but continue cleanup
                        // The thread is daemon so will be killed when JVM exits
                        log.error("✖ Cleanup thread failed to terminate after 7s - may cause resource leak");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠ Interrupted while waiting for cleanup thread");
            }
            cleanupThread = null;
        }

        if (clusterViewListener != null) {
            try {
                cacheManager.removeListener(clusterViewListener);
            } catch (Exception e) {
                // Log but continue cleanup - listener may still receive events (H6 fix)
                log.warn("⚠ Failed to remove ClusterViewListener: {}", e.getMessage());
            }
            clusterViewListener = null;
        }

        // Clear all references to prevent stale callbacks
        eventListener = null;

        // Clear after cleanup thread has terminated to prevent race condition
        processedEvents.clear();
        evictionCache = null;
        log.info("✔ Infinispan cache provider shutdown (nodeId: {})", nodeId);
    }

    @Override
    public CacheProviderStats getStats() {
        int clusterSize = 1;
        if (isAvailable() && cacheManager.getMembers() != null) {
            clusterSize = cacheManager.getMembers().size();
        }

        return new CacheProviderStats(
            evictionsSent.get(),
            evictionsReceived.get(),
            isAvailable(),
            nodeId + " (cluster: " + clusterSize + " nodes)"
        );
    }

    private String generateEventKey(CacheEvictionEvent event) {
        // Use eventId for deduplication if available (C12 idempotency fix)
        // This ensures retried events with the same eventId are properly deduplicated
        if (event.getEventId() != null && !event.getEventId().isEmpty()) {
            return event.getEventId();
        }

        // Fallback to composite key for legacy events without eventId (H5 fix)
        String nodeId = event.getNodeId() != null ? event.getNodeId() : "_UNKNOWN_NODE_";
        String entityClass = event.getEntityClass() != null ? event.getEntityClass() : "_UNKNOWN_CLASS_";
        String entityId = event.getEntityId() != null ? event.getEntityId() : "_BULK_EVICTION_";
        long timestamp = event.getTimestamp() != null ? event.getTimestamp() : System.currentTimeMillis();

        return String.format("%s:%s:%s:%d", nodeId, entityClass, entityId, timestamp);
    }

    /**
     * Evicts oldest entries from processedEvents map to make room for new entries.
     * This ensures deduplication always works even under high load (C7 fix).
     * Evicts approximately 10% of entries to reduce eviction frequency.
     */
    private void evictOldestProcessedEvents() {
        int targetEvictions = MAX_PROCESSED_EVENTS_SIZE / 10; // Evict 10%
        long now = System.currentTimeMillis();

        // First try to evict expired entries
        int evicted = 0;
        var iterator = processedEvents.entrySet().iterator();
        while (iterator.hasNext() && evicted < targetEvictions) {
            var entry = iterator.next();
            if (now - entry.getValue() > EVENT_EXPIRY_MS) {
                iterator.remove();
                evicted++;
            }
        }

        // If not enough expired entries, evict oldest entries
        if (evicted < targetEvictions) {
            // Find and remove oldest entries by timestamp
            processedEvents.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(targetEvictions - evicted)
                    .map(Map.Entry::getKey)
                    .toList() // Create snapshot to avoid ConcurrentModificationException
                    .forEach(processedEvents::remove);
        }

        log.debug("✔ Evicted {} old entries from processedEvents map", evicted);
    }

    private void startCleanupTask() {
        // Guard against multiple cleanup threads
        Thread existingThread = cleanupThread;
        if (existingThread != null && existingThread.isAlive()) {
            log.warn("⚠ Cleanup thread already running, skipping creation");
            return;
        }

        shutdownSignal.set(false); // Reset shutdown signal on start
        cleanupThread = new Thread(() -> {
            while (!shutdownSignal.get()) {
                try {
                    // Use smaller sleep intervals for faster shutdown response
                    for (int i = 0; i < 30 && !shutdownSignal.get(); i++) {
                        Thread.sleep(1000);
                    }

                    if (shutdownSignal.get()) {
                        break;
                    }

                    long now = System.currentTimeMillis();
                    processedEvents.entrySet().removeIf(entry ->
                        now - entry.getValue() > EVENT_EXPIRY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "infinispan-event-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * Listener for cache entry events
     */
    @Listener(clustered = true)
    public static class EvictionCacheListener {

        private final Consumer<CacheEvictionEvent> eventConsumer;

        public EvictionCacheListener(Consumer<CacheEvictionEvent> eventConsumer) {
            this.eventConsumer = eventConsumer;
        }

        @org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated
        public void onCacheEntryCreated(
                org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent<String, CacheEvictionEvent> event) {
            if (!event.isPre() && event.getValue() != null) {
                eventConsumer.accept(event.getValue());
            }
        }
    }

    /**
     * Listener for cluster view changes
     */
    @Listener
    public class ClusterViewListener {

        @ViewChanged
        public void onViewChanged(ViewChangedEvent event) {
            int oldSize = event.getOldMembers().size();
            int newSize = event.getNewMembers().size();

            if (newSize > oldSize) {
                log.info("ℹ Infinispan cluster member joined: {} -> {} nodes", oldSize, newSize);
            } else if (newSize < oldSize) {
                log.warn("⚠ Infinispan cluster member left: {} -> {} nodes", oldSize, newSize);
            }
        }
    }
}
