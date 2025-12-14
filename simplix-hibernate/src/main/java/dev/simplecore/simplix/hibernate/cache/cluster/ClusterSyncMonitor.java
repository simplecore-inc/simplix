package dev.simplecore.simplix.hibernate.cache.cluster;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitor cluster synchronization health
 */
@Slf4j
public class ClusterSyncMonitor implements HealthIndicator {

    private volatile CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final Map<String, NodeStatus> clusterNodes = new ConcurrentHashMap<>();
    private final AtomicLong heartbeatsSent = new AtomicLong();
    private final AtomicLong heartbeatsReceived = new AtomicLong();

    /**
     * Cached node ID to ensure consistency across all method calls.
     * Generated once at construction time (10th review fix - critical bug).
     * Without caching, getNodeId() would return different UUIDs on each call,
     * causing own heartbeat filtering to fail.
     */
    private final String nodeId;

    public ClusterSyncMonitor(CacheProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
        // Generate stable node ID at construction time (10th review fix)
        this.nodeId = System.getProperty("node.id", "node-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.cacheProvider = providerFactory.selectBestAvailable();
        if (this.cacheProvider == null) {
            log.warn("⚠ No cache provider available for cluster sync monitor");
        }
    }

    /**
     * Clean up on shutdown to prevent scheduled tasks from running after context close (M8 fix).
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        this.cacheProvider = null;
        clusterNodes.clear();
        log.info("✔ ClusterSyncMonitor shutdown complete");
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        // Capture to local variable to prevent TOCTOU race condition
        CacheProvider provider = cacheProvider;
        if (provider == null || !provider.isAvailable()) {
            return;
        }

        try {
            String heartbeatId = UUID.randomUUID().toString();
            CacheEvictionEvent heartbeat = CacheEvictionEvent.builder()
                    .entityClass("HEARTBEAT")
                    .entityId(heartbeatId)
                    .nodeId(getNodeId())
                    .timestamp(System.currentTimeMillis())
                    .operation("PING")
                    .build();

            provider.broadcastEviction(heartbeat);
            heartbeatsSent.incrementAndGet();
        } catch (Exception e) {
            // Log but don't propagate - heartbeat failure shouldn't crash scheduled task (10th review fix)
            log.warn("⚠ Failed to send heartbeat: {}", e.getMessage());
        }

        // Clean up old nodes (outside try block - should run even if heartbeat fails)
        cleanupInactiveNodes();
    }

    public void receiveHeartbeat(CacheEvictionEvent event) {
        // Null safety check (10th review fix)
        if (event == null) {
            return;
        }

        if (!"HEARTBEAT".equals(event.getEntityClass())) {
            return;
        }

        String nodeId = event.getNodeId();
        // Null nodeId check - cannot track node without identifier
        if (nodeId == null || nodeId.isEmpty()) {
            log.debug("Received heartbeat with null/empty nodeId, ignoring");
            return;
        }
        if (Objects.equals(nodeId, getNodeId())) {
            return; // Own heartbeat
        }

        // Use compute() for atomic insert-or-update to ensure consistent state
        // This prevents race condition where another thread reads incomplete NodeStatus
        clusterNodes.compute(nodeId, (key, existingStatus) -> {
            if (existingStatus == null) {
                // New node - create and initialize atomically
                NodeStatus newStatus = new NodeStatus(key);
                newStatus.updateHeartbeat();
                return newStatus;
            } else {
                // Existing node - update atomically
                existingStatus.updateHeartbeat();
                return existingStatus;
            }
        });

        heartbeatsReceived.incrementAndGet();
        log.debug("✔ Heartbeat received from node: {}", nodeId);
    }

    private void cleanupInactiveNodes() {
        Instant timeout = Instant.now().minusSeconds(30); // 30 seconds timeout

        // Create snapshot of keys to avoid ConcurrentModificationException
        // and race conditions during iteration with concurrent heartbeat updates
        List<String> nodeIds = List.copyOf(clusterNodes.keySet());

        for (String nodeId : nodeIds) {
            // Use computeIfPresent for atomic check-and-remove
            // The snapshot ensures we don't miss nodes added during iteration
            // and computeIfPresent ensures atomic check within the operation
            clusterNodes.computeIfPresent(nodeId, (id, currentStatus) -> {
                // Re-check lastSeen inside atomic operation
                if (currentStatus.getLastSeen().isBefore(timeout)) {
                    log.warn("⚠ Node {} is inactive (last seen: {})", id, currentStatus.getLastSeen());
                    return null; // Remove entry by returning null
                }
                return currentStatus; // Keep entry
            });
        }
    }

    public Map<String, Object> getClusterStatus() {
        return Map.of(
                "activeNodes", clusterNodes.size() + 1, // +1 for self
                "nodes", clusterNodes,
                "heartbeatsSent", heartbeatsSent.get(),
                "heartbeatsReceived", heartbeatsReceived.get(),
                "syncHealth", calculateSyncHealth()
        );
    }

    private String calculateSyncHealth() {
        if (clusterNodes.isEmpty()) {
            return "STANDALONE";
        }

        long activeNodes = clusterNodes.values().stream()
                .filter(NodeStatus::isActive)
                .count();

        if (activeNodes == clusterNodes.size()) {
            return "HEALTHY";
        } else if (activeNodes > clusterNodes.size() / 2) {
            return "DEGRADED";
        } else {
            return "CRITICAL";
        }
    }

    @Override
    public Health health() {
        String syncHealth = calculateSyncHealth();

        Health.Builder builder = "HEALTHY".equals(syncHealth) || "STANDALONE".equals(syncHealth)
                ? Health.up()
                : Health.down();

        Health.Builder healthBuilder = builder
                .withDetail("syncStatus", syncHealth)
                .withDetail("activeNodes", clusterNodes.size() + 1);

        if (cacheProvider != null) {
            healthBuilder
                .withDetail("provider", cacheProvider.getType())
                .withDetail("providerConnected", cacheProvider.isAvailable());
        } else {
            healthBuilder
                .withDetail("provider", "NONE")
                .withDetail("providerConnected", false);
        }

        return healthBuilder.build();
    }

    /**
     * Returns the cached node ID.
     * Uses pre-computed nodeId field instead of generating new UUID on each call (10th review fix).
     *
     * @return the stable node identifier
     */
    private String getNodeId() {
        return nodeId;
    }

    /**
     * Fully synchronized NodeStatus to ensure atomic updates across all fields.
     * All state modifications are synchronized to prevent inconsistent reads.
     */
    public static class NodeStatus {
        private final String nodeId;
        private Instant lastSeen = Instant.now();
        private boolean active = true;
        private long heartbeatCount = 0;

        public NodeStatus(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public synchronized Instant getLastSeen() {
            return lastSeen;
        }

        public synchronized void setLastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
        }

        public synchronized boolean isActive() {
            return active;
        }

        public synchronized void setActive(boolean active) {
            this.active = active;
        }

        public synchronized long getHeartbeatCount() {
            return heartbeatCount;
        }

        public synchronized void incrementHeartbeatCount() {
            this.heartbeatCount++;
        }

        /**
         * Atomic update of all heartbeat-related fields together.
         */
        public synchronized void updateHeartbeat() {
            this.lastSeen = Instant.now();
            this.active = true;
            this.heartbeatCount++;
        }
    }
}