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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitor cluster synchronization health
 */
@Slf4j
public class ClusterSyncMonitor implements HealthIndicator {

    private CacheProvider cacheProvider;
    private final CacheProviderFactory providerFactory;
    private final Map<String, NodeStatus> clusterNodes = new ConcurrentHashMap<>();
    private final AtomicLong heartbeatsSent = new AtomicLong();
    private final AtomicLong heartbeatsReceived = new AtomicLong();

    public ClusterSyncMonitor(CacheProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.cacheProvider = providerFactory.selectBestAvailable();
    }

    @Scheduled(fixedDelay = 10000) // Every 10 seconds
    public void sendHeartbeat() {
        if (!cacheProvider.isAvailable()) {
            return;
        }

        String heartbeatId = UUID.randomUUID().toString();
        CacheEvictionEvent heartbeat = CacheEvictionEvent.builder()
                .entityClass("HEARTBEAT")
                .entityId(heartbeatId)
                .nodeId(getNodeId())
                .timestamp(System.currentTimeMillis())
                .operation("PING")
                .build();

        cacheProvider.broadcastEviction(heartbeat);
        heartbeatsSent.incrementAndGet();

        // Clean up old nodes
        cleanupInactiveNodes();
    }

    public void receiveHeartbeat(CacheEvictionEvent event) {
        if (!"HEARTBEAT".equals(event.getEntityClass())) {
            return;
        }

        String nodeId = event.getNodeId();
        if (nodeId.equals(getNodeId())) {
            return; // Own heartbeat
        }

        NodeStatus status = clusterNodes.computeIfAbsent(nodeId, k -> new NodeStatus(k));
        status.setLastSeen(Instant.now());
        status.setActive(true);
        status.incrementHeartbeatCount();

        heartbeatsReceived.incrementAndGet();
        log.debug("✔ Heartbeat received from node: {}", nodeId);
    }

    private void cleanupInactiveNodes() {
        Instant timeout = Instant.now().minusSeconds(30); // 30 seconds timeout

        clusterNodes.entrySet().removeIf(entry -> {
            NodeStatus status = entry.getValue();
            if (status.getLastSeen().isBefore(timeout)) {
                log.warn("⚠ Node {} is inactive (last seen: {})",
                        entry.getKey(), status.getLastSeen());
                return true;
            }
            return false;
        });
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

        return builder
                .withDetail("syncStatus", syncHealth)
                .withDetail("activeNodes", clusterNodes.size() + 1)
                .withDetail("provider", cacheProvider.getType())
                .withDetail("providerConnected", cacheProvider.isAvailable())
                .build();
    }

    private String getNodeId() {
        return System.getProperty("node.id", "node-" + UUID.randomUUID());
    }

    @Data
    public static class NodeStatus {
        private final String nodeId;
        private Instant lastSeen = Instant.now();
        private boolean active = true;
        private long heartbeatCount = 0;

        public void incrementHeartbeatCount() {
            this.heartbeatCount++;
        }
    }
}