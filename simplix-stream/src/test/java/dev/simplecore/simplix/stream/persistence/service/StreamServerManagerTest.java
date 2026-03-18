package dev.simplecore.simplix.stream.persistence.service;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.persistence.entity.StreamServerInstanceEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamServerInstanceRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StreamServerManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamServerManager")
class StreamServerManagerTest {

    @Mock
    private StreamServerInstanceRepository serverRepository;

    @Mock
    private StreamSessionRepository sessionRepository;

    private StreamProperties properties;
    private StreamServerManager manager;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setServer(new StreamProperties.ServerConfig());
        properties.getServer().setInstanceId("test-instance");
        properties.getServer().setHeartbeatInterval(Duration.ofSeconds(30));
        properties.getServer().setDeadThreshold(Duration.ofMinutes(2));

        manager = new StreamServerManager(serverRepository, sessionRepository, properties);
    }

    @Nested
    @DisplayName("initialize()")
    class Initialize {

        @Test
        @DisplayName("should register instance and set running to true")
        void shouldRegisterAndSetRunning() {
            manager.initialize();

            assertThat(manager.isRunning()).isTrue();
            verify(serverRepository).save(any(StreamServerInstanceEntity.class));
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class Shutdown {

        @Test
        @DisplayName("should unregister instance and set running to false")
        void shouldUnregisterAndStopRunning() {
            manager.initialize();

            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId(manager.getInstanceId())
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .build();
            when(serverRepository.findById(manager.getInstanceId())).thenReturn(Optional.of(entity));

            manager.shutdown();

            assertThat(manager.isRunning()).isFalse();
            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.DEAD);
        }

        @Test
        @DisplayName("should handle instance not found in database during shutdown")
        void shouldHandleInstanceNotFoundOnShutdown() {
            manager.initialize();
            when(serverRepository.findById(manager.getInstanceId())).thenReturn(Optional.empty());

            manager.shutdown();

            assertThat(manager.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("registerInstance()")
    class RegisterInstance {

        @Test
        @DisplayName("should persist server entity with correct fields")
        void shouldPersistEntityWithCorrectFields() {
            manager.registerInstance();

            ArgumentCaptor<StreamServerInstanceEntity> captor =
                    ArgumentCaptor.forClass(StreamServerInstanceEntity.class);
            verify(serverRepository).save(captor.capture());
            StreamServerInstanceEntity entity = captor.getValue();

            assertThat(entity.getInstanceId()).isEqualTo(manager.getInstanceId());
            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.ACTIVE);
            assertThat(entity.getActiveSessions()).isZero();
            assertThat(entity.getActiveSchedulers()).isZero();
            assertThat(entity.getHostname()).isNotNull();
        }
    }

    @Nested
    @DisplayName("heartbeat()")
    class Heartbeat {

        @Test
        @DisplayName("should update heartbeat when running")
        void shouldUpdateHeartbeatWhenRunning() {
            manager.initialize();

            manager.heartbeat();

            verify(serverRepository).updateHeartbeat(eq(manager.getInstanceId()), any(Instant.class));
        }

        @Test
        @DisplayName("should skip heartbeat when not running")
        void shouldSkipWhenNotRunning() {
            // Not initialized, so not running
            manager.heartbeat();

            verify(serverRepository, never()).updateHeartbeat(anyString(), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("updateStats()")
    class UpdateStats {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            manager.updateStats(10, 5);

            verify(serverRepository).updateStats(manager.getInstanceId(), 10, 5);
        }
    }

    @Nested
    @DisplayName("cleanupDeadServers()")
    class CleanupDeadServers {

        @Test
        @DisplayName("should skip cleanup when not running")
        void shouldSkipWhenNotRunning() {
            manager.cleanupDeadServers();

            verify(serverRepository, never()).markSuspected(any(Instant.class));
        }

        @Test
        @DisplayName("should mark suspected and dead servers")
        void shouldMarkSuspectedAndDeadServers() {
            manager.initialize();
            when(serverRepository.markSuspected(any(Instant.class))).thenReturn(0);
            when(serverRepository.findDeadServers(any(Instant.class))).thenReturn(List.of());
            when(serverRepository.markDead(any(Instant.class))).thenReturn(0);

            manager.cleanupDeadServers();

            verify(serverRepository).markSuspected(any(Instant.class));
            verify(serverRepository).findDeadServers(any(Instant.class));
            verify(serverRepository).markDead(any(Instant.class));
        }

        @Test
        @DisplayName("should handle dead servers and terminate orphan sessions")
        void shouldHandleDeadServersAndTerminateOrphans() {
            manager.initialize();
            when(serverRepository.markSuspected(any(Instant.class))).thenReturn(1);

            StreamServerInstanceEntity deadServer = StreamServerInstanceEntity.builder()
                    .instanceId("dead-inst")
                    .lastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(10)))
                    .build();
            when(serverRepository.findDeadServers(any(Instant.class))).thenReturn(List.of(deadServer));
            when(sessionRepository.terminateSessionsByInstanceId("dead-inst")).thenReturn(5);
            when(serverRepository.markDead(any(Instant.class))).thenReturn(1);

            manager.cleanupDeadServers();

            verify(sessionRepository).terminateSessionsByInstanceId("dead-inst");
        }
    }

    @Nested
    @DisplayName("handleDeadServer()")
    class HandleDeadServer {

        @Test
        @DisplayName("should terminate orphan sessions")
        void shouldTerminateOrphanSessions() {
            StreamServerInstanceEntity server = StreamServerInstanceEntity.builder()
                    .instanceId("dead-inst")
                    .lastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(10)))
                    .build();
            when(sessionRepository.terminateSessionsByInstanceId("dead-inst")).thenReturn(3);

            manager.handleDeadServer(server);

            verify(sessionRepository).terminateSessionsByInstanceId("dead-inst");
        }

        @Test
        @DisplayName("should invoke callback on dead server detection")
        void shouldInvokeCallback() {
            AtomicReference<String> detectedId = new AtomicReference<>();
            manager.setOnDeadServerDetected(detectedId::set);

            StreamServerInstanceEntity server = StreamServerInstanceEntity.builder()
                    .instanceId("dead-inst")
                    .lastHeartbeatAt(Instant.now().minus(Duration.ofMinutes(10)))
                    .build();
            when(sessionRepository.terminateSessionsByInstanceId("dead-inst")).thenReturn(0);

            manager.handleDeadServer(server);

            assertThat(detectedId.get()).isEqualTo("dead-inst");
        }
    }

    @Nested
    @DisplayName("getActiveServers()")
    class GetActiveServers {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(serverRepository.findActive()).thenReturn(List.of());

            List<StreamServerInstanceEntity> result = manager.getActiveServers();

            assertThat(result).isEmpty();
            verify(serverRepository).findActive();
        }
    }

    @Nested
    @DisplayName("getServer()")
    class GetServer {

        @Test
        @DisplayName("should return server when found")
        void shouldReturnWhenFound() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .build();
            when(serverRepository.findById("inst-1")).thenReturn(Optional.of(entity));

            StreamServerInstanceEntity result = manager.getServer("inst-1");

            assertThat(result).isNotNull();
            assertThat(result.getInstanceId()).isEqualTo("inst-1");
        }

        @Test
        @DisplayName("should return null when not found")
        void shouldReturnNullWhenNotFound() {
            when(serverRepository.findById("nonexistent")).thenReturn(Optional.empty());

            StreamServerInstanceEntity result = manager.getServer("nonexistent");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getActiveServerCount()")
    class GetActiveServerCount {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(serverRepository.countByStatus(StreamServerInstanceEntity.Status.ACTIVE)).thenReturn(3L);

            long count = manager.getActiveServerCount();

            assertThat(count).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getInstanceId()")
    class GetInstanceId {

        @Test
        @DisplayName("should return configured instance ID")
        void shouldReturnConfiguredInstanceId() {
            assertThat(manager.getInstanceId()).isEqualTo("test-instance");
        }

        @Test
        @DisplayName("should generate instance ID when not configured")
        void shouldGenerateWhenNotConfigured() {
            properties.getServer().setInstanceId(null);
            StreamServerManager unconfigured = new StreamServerManager(
                    serverRepository, sessionRepository, properties);

            assertThat(unconfigured.getInstanceId()).isNotNull();
            assertThat(unconfigured.getInstanceId()).isNotEmpty();
        }

        @Test
        @DisplayName("should generate instance ID when configured as blank")
        void shouldGenerateWhenBlank() {
            properties.getServer().setInstanceId("  ");
            StreamServerManager blankConfigured = new StreamServerManager(
                    serverRepository, sessionRepository, properties);

            assertThat(blankConfigured.getInstanceId()).isNotNull();
            assertThat(blankConfigured.getInstanceId()).doesNotContain(" ");
        }
    }
}
