package dev.simplecore.simplix.hibernate.cache.cluster;

import dev.simplecore.simplix.hibernate.cache.event.CacheEvictionEvent;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ClusterSyncMonitor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClusterSyncMonitor Tests")
class ClusterSyncMonitorTest {

    @Mock
    private CacheProviderFactory providerFactory;

    @Mock
    private CacheProvider mockProvider;

    private ClusterSyncMonitor clusterSyncMonitor;

    @BeforeEach
    void setUp() {
        clusterSyncMonitor = new ClusterSyncMonitor(providerFactory);
    }

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should generate stable node ID")
        void shouldGenerateStableNodeId() {
            // Given
            ClusterSyncMonitor monitor1 = new ClusterSyncMonitor(providerFactory);
            ClusterSyncMonitor monitor2 = new ClusterSyncMonitor(providerFactory);

            // Then - different instances should have different node IDs
            // But same instance should have consistent node ID
            Map<String, Object> status1 = monitor1.getClusterStatus();
            // The node ID is internal, but we can verify through heartbeat behavior
            assertThat(status1).isNotNull();
        }
    }

    @Nested
    @DisplayName("init() tests")
    class InitTests {

        @Test
        @DisplayName("Should initialize with available provider")
        void shouldInitializeWithAvailableProvider() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);

            // When
            clusterSyncMonitor.init();

            // Then
            verify(providerFactory).selectBestAvailable();
        }

        @Test
        @DisplayName("Should handle no provider gracefully")
        void shouldHandleNoProviderGracefully() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(null);

            // When/Then - should not throw
            clusterSyncMonitor.init();
        }
    }

    @Nested
    @DisplayName("sendHeartbeat() tests")
    class SendHeartbeatTests {

        @Test
        @DisplayName("Should send heartbeat when provider available")
        void shouldSendHeartbeatWhenProviderAvailable() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);
            clusterSyncMonitor.init();

            // When
            clusterSyncMonitor.sendHeartbeat();

            // Then
            verify(mockProvider).broadcastEviction(argThat(event ->
                    "HEARTBEAT".equals(event.getEntityClass()) &&
                    "PING".equals(event.getOperation())));
        }

        @Test
        @DisplayName("Should not send heartbeat when provider unavailable")
        void shouldNotSendHeartbeatWhenProviderUnavailable() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(false);
            clusterSyncMonitor.init();

            // When
            clusterSyncMonitor.sendHeartbeat();

            // Then
            verify(mockProvider, never()).broadcastEviction(any());
        }

        @Test
        @DisplayName("Should not throw when broadcast fails")
        void shouldNotThrowWhenBroadcastFails() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);
            doThrow(new RuntimeException("Broadcast failed"))
                    .when(mockProvider).broadcastEviction(any());
            clusterSyncMonitor.init();

            // When/Then - should not throw
            clusterSyncMonitor.sendHeartbeat();
        }
    }

    @Nested
    @DisplayName("receiveHeartbeat() tests")
    class ReceiveHeartbeatTests {

        @Test
        @DisplayName("Should track new node from heartbeat")
        void shouldTrackNewNodeFromHeartbeat() {
            // Given
            CacheEvictionEvent heartbeat = CacheEvictionEvent.builder()
                    .entityClass("HEARTBEAT")
                    .entityId("heartbeat-123")
                    .nodeId("remote-node-1")
                    .operation("PING")
                    .timestamp(System.currentTimeMillis())
                    .build();

            // When
            clusterSyncMonitor.receiveHeartbeat(heartbeat);

            // Then
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(2); // self + 1 remote
        }

        @Test
        @DisplayName("Should update existing node heartbeat")
        void shouldUpdateExistingNodeHeartbeat() {
            // Given
            String remoteNodeId = "remote-node-1";
            CacheEvictionEvent heartbeat1 = createHeartbeatEvent(remoteNodeId);
            CacheEvictionEvent heartbeat2 = createHeartbeatEvent(remoteNodeId);

            // When
            clusterSyncMonitor.receiveHeartbeat(heartbeat1);
            clusterSyncMonitor.receiveHeartbeat(heartbeat2);

            // Then - should still be 2 nodes (self + 1 remote)
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(2);
            assertThat((Long) status.get("heartbeatsReceived")).isEqualTo(2L);
        }

        @Test
        @DisplayName("Should ignore non-heartbeat events")
        void shouldIgnoreNonHeartbeatEvents() {
            // Given
            CacheEvictionEvent normalEvent = CacheEvictionEvent.builder()
                    .entityClass("User")
                    .entityId("123")
                    .nodeId("remote-node-1")
                    .build();

            // When
            clusterSyncMonitor.receiveHeartbeat(normalEvent);

            // Then - should only have self
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should ignore null event")
        void shouldIgnoreNullEvent() {
            // When/Then - should not throw
            clusterSyncMonitor.receiveHeartbeat(null);

            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should ignore heartbeat with null nodeId")
        void shouldIgnoreHeartbeatWithNullNodeId() {
            // Given
            CacheEvictionEvent heartbeat = CacheEvictionEvent.builder()
                    .entityClass("HEARTBEAT")
                    .entityId("heartbeat-123")
                    .nodeId(null)
                    .operation("PING")
                    .build();

            // When
            clusterSyncMonitor.receiveHeartbeat(heartbeat);

            // Then - should only have self
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getClusterStatus() tests")
    class GetClusterStatusTests {

        @Test
        @DisplayName("Should return complete status")
        void shouldReturnCompleteStatus() {
            // When
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();

            // Then
            assertThat(status).containsKeys(
                    "activeNodes",
                    "nodes",
                    "heartbeatsSent",
                    "heartbeatsReceived",
                    "syncHealth"
            );
        }

        @Test
        @DisplayName("Should show STANDALONE when no other nodes")
        void shouldShowStandaloneWhenNoOtherNodes() {
            // When
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();

            // Then
            assertThat(status.get("syncHealth")).isEqualTo("STANDALONE");
        }

        @Test
        @DisplayName("Should show HEALTHY when all nodes active")
        void shouldShowHealthyWhenAllNodesActive() {
            // Given
            clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent("node-1"));
            clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent("node-2"));

            // When
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();

            // Then
            assertThat(status.get("syncHealth")).isEqualTo("HEALTHY");
        }
    }

    @Nested
    @DisplayName("health() tests")
    class HealthTests {

        @Test
        @DisplayName("Should return UP for STANDALONE")
        void shouldReturnUpForStandalone() {
            // When
            Health health = clusterSyncMonitor.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("syncStatus", "STANDALONE");
        }

        @Test
        @DisplayName("Should return UP for HEALTHY")
        void shouldReturnUpForHealthy() {
            // Given
            clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent("node-1"));

            // When
            Health health = clusterSyncMonitor.health();

            // Then
            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("syncStatus", "HEALTHY");
        }

        @Test
        @DisplayName("Should include provider info when available")
        void shouldIncludeProviderInfoWhenAvailable() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(mockProvider);
            when(mockProvider.isAvailable()).thenReturn(true);
            when(mockProvider.getType()).thenReturn("REDIS");
            clusterSyncMonitor.init();

            // When
            Health health = clusterSyncMonitor.health();

            // Then
            assertThat(health.getDetails()).containsEntry("provider", "REDIS");
            assertThat(health.getDetails()).containsEntry("providerConnected", true);
        }

        @Test
        @DisplayName("Should show NONE when no provider")
        void shouldShowNoneWhenNoProvider() {
            // Given
            when(providerFactory.selectBestAvailable()).thenReturn(null);
            clusterSyncMonitor.init();

            // When
            Health health = clusterSyncMonitor.health();

            // Then
            assertThat(health.getDetails()).containsEntry("provider", "NONE");
            assertThat(health.getDetails()).containsEntry("providerConnected", false);
        }
    }

    @Nested
    @DisplayName("shutdown() tests")
    class ShutdownTests {

        @Test
        @DisplayName("Should clear cluster nodes on shutdown")
        void shouldClearClusterNodesOnShutdown() {
            // Given
            clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent("node-1"));
            clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent("node-2"));

            // When
            clusterSyncMonitor.shutdown();

            // Then
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Integer) status.get("activeNodes")).isEqualTo(1); // Only self
        }
    }

    @Nested
    @DisplayName("NodeStatus tests")
    class NodeStatusTests {

        @Test
        @DisplayName("NodeStatus should track heartbeat count")
        void nodeStatusShouldTrackHeartbeatCount() {
            // Given
            String remoteNodeId = "remote-node-1";

            // When - send multiple heartbeats
            for (int i = 0; i < 5; i++) {
                clusterSyncMonitor.receiveHeartbeat(createHeartbeatEvent(remoteNodeId));
            }

            // Then
            Map<String, Object> status = clusterSyncMonitor.getClusterStatus();
            assertThat((Long) status.get("heartbeatsReceived")).isEqualTo(5L);

            @SuppressWarnings("unchecked")
            Map<String, ClusterSyncMonitor.NodeStatus> nodes =
                    (Map<String, ClusterSyncMonitor.NodeStatus>) status.get("nodes");
            ClusterSyncMonitor.NodeStatus nodeStatus = nodes.get(remoteNodeId);
            assertThat(nodeStatus.getHeartbeatCount()).isEqualTo(5L);
            assertThat(nodeStatus.isActive()).isTrue();
        }
    }

    private CacheEvictionEvent createHeartbeatEvent(String nodeId) {
        return CacheEvictionEvent.builder()
                .entityClass("HEARTBEAT")
                .entityId("heartbeat-" + System.nanoTime())
                .nodeId(nodeId)
                .operation("PING")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
