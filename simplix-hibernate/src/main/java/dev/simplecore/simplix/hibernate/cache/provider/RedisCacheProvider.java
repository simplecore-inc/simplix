package dev.simplecore.simplix.hibernate.cache.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based distributed cache provider for Hibernate L2 cache eviction synchronization.
 * <p>
 * Uses Redis Pub/Sub for broadcasting cache eviction events across cluster nodes.
 * This provider is auto-configured by DistributedCacheAutoConfiguration
 * when spring-data-redis is available.
 */
@Slf4j
public class RedisCacheProvider implements CacheProvider {

    private static final String CHANNEL_NAME = "simplix:hibernate:cache:eviction";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    private final AtomicLong evictionsSent = new AtomicLong(0);
    private final AtomicLong evictionsReceived = new AtomicLong(0);

    // Volatile for thread visibility across Redis listener threads
    private volatile CacheEvictionEventListener eventListener;
    private volatile boolean initialized = false;

    // Track registered MessageListener for proper cleanup in shutdown()
    private volatile MessageListener registeredMessageListener;
    private final ChannelTopic channelTopic = new ChannelTopic(CHANNEL_NAME);

    // Event deduplication to prevent duplicate processing (7th review fix - parity with Infinispan)
    private static final int MAX_PROCESSED_EVENTS_SIZE = 10000;
    private static final long EVENT_EXPIRY_MS = 60000; // 1 minute
    private final Map<String, Long> processedEvents = new ConcurrentHashMap<>();

    public RedisCacheProvider(
            RedisTemplate<String, String> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public String getType() {
        return "REDIS";
    }

    @Override
    public boolean isAvailable() {
        var connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return false;
        }

        // Use try-with-resources to ensure connection is properly closed
        try (var connection = connectionFactory.getConnection()) {
            connection.ping();
            return true;
        } catch (Exception e) {
            log.debug("Redis not available: {}", e.getMessage());
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

        if (!isAvailable()) {
            // Throw exception so caller can handle retry/failover
            throw new IllegalStateException("Redis not available for eviction broadcast");
        }

        try {
            // Create copy with node ID (immutable event pattern - 7th review fix)
            CacheEvictionEvent eventWithNodeId = event.withNodeId(nodeId);
            String message = objectMapper.writeValueAsString(eventWithNodeId);
            redisTemplate.convertAndSend(CHANNEL_NAME, message);
            evictionsSent.incrementAndGet();

            // Track sent events for deduplication
            String eventKey = eventWithNodeId.getEventId();
            if (eventKey != null && processedEvents.size() < MAX_PROCESSED_EVENTS_SIZE) {
                processedEvents.put(eventKey, System.currentTimeMillis());
            }

            log.debug("Broadcast eviction event: entity={}, id={}",
                eventWithNodeId.getEntityClass(), eventWithNodeId.getEntityId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize eviction event", e);
        } catch (Exception e) {
            // Wrap any Redis communication exception for retry handling
            throw new IllegalStateException("Failed to broadcast eviction to Redis", e);
        }
    }

    @Override
    public synchronized void subscribeToEvictions(CacheEvictionEventListener listener) {
        // Synchronized to prevent race condition with initialize()/shutdown()
        this.eventListener = listener;

        if (!isAvailable()) {
            log.warn("⚠ Redis not available, cannot subscribe to evictions");
            return;
        }

        // Prevent duplicate listener registration
        if (registeredMessageListener != null) {
            log.debug("ℹ MessageListener already registered, skipping duplicate registration");
            return;
        }

        MessageListener messageListener = (message, pattern) -> {
            try {
                // Null check for message body to prevent NPE (8th review fix)
                byte[] body = message.getBody();
                if (body == null || body.length == 0) {
                    log.warn("⚠ Received empty or null message body, skipping");
                    return;
                }

                String json = new String(body);
                CacheEvictionEvent event = objectMapper.readValue(json, CacheEvictionEvent.class);

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
            } catch (Exception e) {
                log.error("Failed to process eviction event", e);
            }
        };

        listenerContainer.addMessageListener(messageListener, channelTopic);
        registeredMessageListener = messageListener;
        log.info("Subscribed to Redis eviction channel: {}", CHANNEL_NAME);
    }

    @Override
    public synchronized void initialize() {
        // Synchronized to prevent duplicate initialization in multi-threaded environment
        if (initialized) {
            return;
        }

        if (!isAvailable()) {
            log.warn("⚠ Redis not available during initialization");
            return;
        }

        initialized = true;
        log.info("✔ Redis cache provider initialized (nodeId: {})", nodeId);
    }

    @Override
    public synchronized void shutdown() {
        // Remove registered MessageListener from container to prevent resource leak
        // and stale callbacks after shutdown
        if (registeredMessageListener != null) {
            try {
                listenerContainer.removeMessageListener(registeredMessageListener, channelTopic);
                log.debug("✔ MessageListener removed from RedisMessageListenerContainer");
            } catch (Exception e) {
                log.warn("⚠ Failed to remove MessageListener: {}", e.getMessage());
            }
            registeredMessageListener = null;
        }

        // Clear event listener reference to prevent stale callbacks
        eventListener = null;

        // Clear deduplication cache
        processedEvents.clear();

        if (!initialized) {
            log.debug("✔ Redis cache provider cleanup completed (was not fully initialized)");
            return;
        }

        initialized = false;
        log.info("✔ Redis cache provider shutdown (nodeId: {})", nodeId);
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
        return new CacheProviderStats(
            evictionsSent.get(),
            evictionsReceived.get(),
            isAvailable(),
            nodeId
        );
    }
}
