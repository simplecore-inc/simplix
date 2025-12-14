package dev.simplecore.simplix.hibernate.cache.provider;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;
import com.hazelcast.topic.MessageListener;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hazelcast-based distributed cache provider for Hibernate L2 cache eviction synchronization.
 * <p>
 * Uses Hazelcast ITopic for broadcasting cache eviction events across cluster nodes.
 * Hazelcast provides automatic cluster discovery and partition-aware data distribution.
 * This provider is auto-configured by DistributedCacheAutoConfiguration
 * when Hazelcast is available.
 */
@Slf4j
public class HazelcastCacheProvider implements CacheProvider {

    private static final String TOPIC_NAME = "simplix-hibernate-cache-eviction";

    private final HazelcastInstance hazelcastInstance;
    private final String nodeId;

    private final AtomicLong evictionsSent = new AtomicLong(0);
    private final AtomicLong evictionsReceived = new AtomicLong(0);

    // Volatile for thread visibility across Hazelcast listener threads
    private volatile ITopic<CacheEvictionEvent> evictionTopic;
    private volatile CacheEvictionEventListener eventListener;
    private volatile boolean initialized = false;

    // Track all registered listener IDs for proper cleanup in shutdown()
    // Use ConcurrentHashMap.newKeySet() for thread-safe Set
    private final Set<UUID> listenerRegistrationIds = ConcurrentHashMap.newKeySet();

    // Event deduplication to prevent duplicate processing (7th review fix - parity with Infinispan)
    private static final int MAX_PROCESSED_EVENTS_SIZE = 10000;
    private static final long EVENT_EXPIRY_MS = 60000; // 1 minute
    private final Map<String, Long> processedEvents = new ConcurrentHashMap<>();

    public HazelcastCacheProvider(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.nodeId = extractNodeId(hazelcastInstance);
    }

    private static String extractNodeId(HazelcastInstance instance) {
        try {
            String uuid = instance.getCluster().getLocalMember().getUuid().toString();
            // UUID format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 chars)
            // Extract first 8 characters safely
            return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
        } catch (Exception e) {
            // Fallback if member info not available yet
            return "hz-" + UUID.randomUUID().toString().substring(0, 5);
        }
    }

    @Override
    public String getType() {
        return "HAZELCAST";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Also check evictionTopic initialization status
            return hazelcastInstance.getLifecycleService().isRunning() && evictionTopic != null;
        } catch (Exception e) {
            log.debug("Hazelcast not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if Hazelcast instance is running (without topic check).
     * Used during initialization when topic is not yet created.
     */
    private boolean isInstanceRunning() {
        try {
            return hazelcastInstance.getLifecycleService().isRunning();
        } catch (Exception e) {
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
        ITopic<CacheEvictionEvent> topic = evictionTopic;
        if (!isInstanceRunning() || topic == null) {
            // Throw exception so caller can handle retry/failover
            throw new IllegalStateException("Hazelcast not available for eviction broadcast");
        }

        try {
            // Create copy with node ID (immutable event pattern - 7th review fix)
            CacheEvictionEvent eventWithNodeId = event.withNodeId(nodeId);
            topic.publish(eventWithNodeId);
            evictionsSent.incrementAndGet();

            // Track sent events for deduplication
            String eventKey = eventWithNodeId.getEventId();
            if (eventKey != null && processedEvents.size() < MAX_PROCESSED_EVENTS_SIZE) {
                processedEvents.put(eventKey, System.currentTimeMillis());
            }

            log.debug("Broadcast eviction event: entity={}, id={}",
                eventWithNodeId.getEntityClass(), eventWithNodeId.getEntityId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to broadcast eviction via Hazelcast", e);
        }
    }

    @Override
    public synchronized void subscribeToEvictions(CacheEvictionEventListener listener) {
        // Synchronized to prevent race condition with initialize()/shutdown()
        this.eventListener = listener;

        if (!isInstanceRunning()) {
            log.warn("⚠ Hazelcast not available, cannot subscribe to evictions");
            return;
        }

        // Prevent duplicate listener registration
        if (!listenerRegistrationIds.isEmpty()) {
            log.debug("ℹ MessageListener already registered, skipping duplicate registration");
            return;
        }

        // Capture topic and check for null
        ITopic<CacheEvictionEvent> topic = hazelcastInstance.getTopic(TOPIC_NAME);
        if (topic == null) {
            log.warn("⚠ Failed to get Hazelcast topic: {}", TOPIC_NAME);
            return;
        }
        evictionTopic = topic;

        MessageListener<CacheEvictionEvent> messageListener = message -> {
            // Null check for message object to prevent NPE (8th review fix)
            CacheEvictionEvent event = message.getMessageObject();
            if (event == null) {
                log.warn("⚠ Received null message object, skipping");
                return;
            }

            // Ignore events from this node
            if (Objects.equals(nodeId, event.getNodeId())) {
                return;
            }

            // Deduplication check using eventId (7th review fix - parity with Infinispan)
            String eventKey = event.getEventId();
            if (eventKey != null) {
                if (processedEvents.containsKey(eventKey)) {
                    log.debug("Duplicate event {} ignored", eventKey);
                    return;
                }

                // Evict old entries if map is full
                if (processedEvents.size() >= MAX_PROCESSED_EVENTS_SIZE) {
                    evictOldestProcessedEvents();
                }

                // Atomic check-and-put for concurrent access
                if (processedEvents.putIfAbsent(eventKey, System.currentTimeMillis()) != null) {
                    return; // Another thread already processed this event
                }
            }

            evictionsReceived.incrementAndGet();
            log.debug("Received eviction event from node {}: entity={}, id={}",
                event.getNodeId(), event.getEntityClass(), event.getEntityId());

            // Capture to local variable for thread-safe access
            CacheEvictionEventListener currentListener = eventListener;
            if (currentListener != null) {
                currentListener.onEvictionEvent(event);
            }
        };

        UUID registrationId = evictionTopic.addMessageListener(messageListener);
        listenerRegistrationIds.add(registrationId);
        log.info("Subscribed to Hazelcast eviction topic: {}", TOPIC_NAME);
    }

    @Override
    public synchronized void initialize() {
        // Synchronized to prevent race condition with shutdown()
        if (initialized) {
            return;
        }

        if (!isInstanceRunning()) {
            log.warn("⚠ Hazelcast not available during initialization");
            return;
        }

        // Capture topic and check for null
        ITopic<CacheEvictionEvent> topic = hazelcastInstance.getTopic(TOPIC_NAME);
        if (topic == null) {
            log.warn("⚠ Failed to get Hazelcast topic during initialization: {}", TOPIC_NAME);
            return;
        }
        evictionTopic = topic;
        initialized = true;

        int clusterSize = hazelcastInstance.getCluster().getMembers().size();
        log.info("✔ Hazelcast cache provider initialized (nodeId: {}, clusterSize: {})",
            nodeId, clusterSize);
    }

    @Override
    public synchronized void shutdown() {
        // Synchronized to prevent race condition with initialize()/subscribeToEvictions()
        if (!initialized) {
            return; // Already shutdown
        }

        // Capture topic to local variable for thread-safe cleanup
        ITopic<CacheEvictionEvent> topic = evictionTopic;

        // Remove all registered message listeners
        if (topic != null && !listenerRegistrationIds.isEmpty()) {
            for (UUID registrationId : listenerRegistrationIds) {
                try {
                    topic.removeMessageListener(registrationId);
                } catch (Exception e) {
                    log.warn("⚠ Failed to remove Hazelcast message listener {}: {}",
                            registrationId, e.getMessage());
                }
            }
            listenerRegistrationIds.clear();
        }

        // Clear all references to prevent stale callbacks
        eventListener = null;
        evictionTopic = null;
        initialized = false;

        // Clear deduplication cache
        processedEvents.clear();

        log.info("✔ Hazelcast cache provider shutdown (nodeId: {})", nodeId);
    }

    /**
     * Evicts oldest entries from processedEvents map using LRU eviction strategy.
     * Evicts approximately 10% of entries to reduce eviction frequency.
     */
    private void evictOldestProcessedEvents() {
        int targetEvictions = MAX_PROCESSED_EVENTS_SIZE / 10;
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

        // If not enough expired entries, evict oldest by timestamp
        if (evicted < targetEvictions) {
            processedEvents.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(targetEvictions - evicted)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(processedEvents::remove);
        }
    }

    @Override
    public CacheProviderStats getStats() {
        int clusterSize = 0;
        if (isAvailable()) {
            clusterSize = hazelcastInstance.getCluster().getMembers().size();
        }

        return new CacheProviderStats(
            evictionsSent.get(),
            evictionsReceived.get(),
            isAvailable(),
            nodeId + " (cluster: " + clusterSize + " nodes)"
        );
    }
}
