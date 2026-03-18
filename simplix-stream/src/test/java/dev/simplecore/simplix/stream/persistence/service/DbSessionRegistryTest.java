package dev.simplecore.simplix.stream.persistence.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.persistence.entity.StreamSessionEntity;
import dev.simplecore.simplix.stream.persistence.entity.StreamSubscriptionEntity;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DbSessionRegistry.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DbSessionRegistry")
class DbSessionRegistryTest {

    @Mock
    private StreamSessionRepository sessionRepository;

    @Mock
    private StreamSubscriptionRepository subscriptionRepository;

    private StreamProperties properties;
    private ObjectMapper objectMapper;
    private DbSessionRegistry registry;

    private static final String INSTANCE_ID = "test-instance";

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        registry = new DbSessionRegistry(
                sessionRepository, subscriptionRepository, properties, objectMapper, INSTANCE_ID);
    }

    @Nested
    @DisplayName("initialize()")
    class Initialize {

        @Test
        @DisplayName("should set available to true")
        void shouldSetAvailableToTrue() {
            registry.initialize();

            assertThat(registry.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class Shutdown {

        @Test
        @DisplayName("should set available to false and clear local sessions")
        void shouldSetAvailableToFalseAndClearSessions() {
            registry.initialize();
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);

            registry.shutdown();

            assertThat(registry.isAvailable()).isFalse();
            assertThat(registry.count()).isZero();
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should store session locally and persist to database")
        void shouldStoreLocallyAndPersist() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);

            registry.register(session);

            assertThat(registry.findById(session.getId())).isPresent();
            verify(sessionRepository).save(any(StreamSessionEntity.class));
        }

        @Test
        @DisplayName("should persist entity with correct fields")
        void shouldPersistEntityWithCorrectFields() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);

            registry.register(session);

            ArgumentCaptor<StreamSessionEntity> captor = ArgumentCaptor.forClass(StreamSessionEntity.class);
            verify(sessionRepository).save(captor.capture());
            StreamSessionEntity entity = captor.getValue();

            assertThat(entity.getId()).isEqualTo(session.getId());
            assertThat(entity.getUserId()).isEqualTo("user-1");
            assertThat(entity.getTransportType()).isEqualTo(TransportType.SSE);
            assertThat(entity.getInstanceId()).isEqualTo(INSTANCE_ID);
        }

        @Test
        @DisplayName("should serialize metadata when present")
        void shouldSerializeMetadataWhenPresent() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            session.getMetadata().put("timezone", "UTC");

            registry.register(session);

            ArgumentCaptor<StreamSessionEntity> captor = ArgumentCaptor.forClass(StreamSessionEntity.class);
            verify(sessionRepository).save(captor.capture());
            assertThat(captor.getValue().getMetadataJson()).isNotNull();
            assertThat(captor.getValue().getMetadataJson()).contains("timezone");
        }

        @Test
        @DisplayName("should set null metadata when empty")
        void shouldSetNullMetadataWhenEmpty() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);

            registry.register(session);

            ArgumentCaptor<StreamSessionEntity> captor = ArgumentCaptor.forClass(StreamSessionEntity.class);
            verify(sessionRepository).save(captor.capture());
            assertThat(captor.getValue().getMetadataJson()).isNull();
        }
    }

    @Nested
    @DisplayName("unregister()")
    class Unregister {

        @Test
        @DisplayName("should remove session from local cache and mark terminated in DB")
        void shouldRemoveFromCacheAndMarkTerminated() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);

            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id(session.getId())
                    .state(SessionState.CONNECTED)
                    .build();
            when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(entity));

            registry.unregister(session.getId());

            assertThat(registry.findById(session.getId())).isEmpty();
            verify(sessionRepository).save(entity);
            assertThat(entity.getState()).isEqualTo(SessionState.TERMINATED);
        }

        @Test
        @DisplayName("should handle unregistering a session not in database")
        void shouldHandleSessionNotInDatabase() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);
            when(sessionRepository.findById(session.getId())).thenReturn(Optional.empty());

            registry.unregister(session.getId());

            assertThat(registry.findById(session.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("should return session from local cache")
        void shouldReturnFromLocalCache() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);

            Optional<StreamSession> found = registry.findById(session.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("should return empty for session not in local cache")
        void shouldReturnEmptyForMissingSession() {
            Optional<StreamSession> found = registry.findById("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserId {

        @Test
        @DisplayName("should return sessions for the specified user")
        void shouldReturnSessionsForUser() {
            StreamSession s1 = StreamSession.create("user-1", TransportType.SSE);
            StreamSession s2 = StreamSession.create("user-1", TransportType.SSE);
            StreamSession s3 = StreamSession.create("user-2", TransportType.SSE);
            registry.register(s1);
            registry.register(s2);
            registry.register(s3);

            Collection<StreamSession> sessions = registry.findByUserId("user-1");

            assertThat(sessions).hasSize(2);
        }

        @Test
        @DisplayName("should return empty collection when no sessions match")
        void shouldReturnEmptyWhenNoMatch() {
            Collection<StreamSession> sessions = registry.findByUserId("nonexistent");

            assertThat(sessions).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("should return all local sessions")
        void shouldReturnAllSessions() {
            StreamSession s1 = StreamSession.create("user-1", TransportType.SSE);
            StreamSession s2 = StreamSession.create("user-2", TransportType.SSE);
            registry.register(s1);
            registry.register(s2);

            Collection<StreamSession> all = registry.findAll();

            assertThat(all).hasSize(2);
        }
    }

    @Nested
    @DisplayName("count()")
    class Count {

        @Test
        @DisplayName("should return local session count")
        void shouldReturnLocalCount() {
            assertThat(registry.count()).isZero();

            registry.register(StreamSession.create("user-1", TransportType.SSE));
            assertThat(registry.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("countByUserId()")
    class CountByUserId {

        @Test
        @DisplayName("should return count of sessions for a user")
        void shouldReturnUserCount() {
            registry.register(StreamSession.create("user-1", TransportType.SSE));
            registry.register(StreamSession.create("user-1", TransportType.SSE));
            registry.register(StreamSession.create("user-2", TransportType.SSE));

            assertThat(registry.countByUserId("user-1")).isEqualTo(2);
            assertThat(registry.countByUserId("user-2")).isEqualTo(1);
            assertThat(registry.countByUserId("user-3")).isZero();
        }
    }

    @Nested
    @DisplayName("restoreSession()")
    class RestoreSession {

        @Test
        @DisplayName("should return local session if already cached")
        void shouldReturnLocalIfCached() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);

            Optional<StreamSession> restored = registry.restoreSession(session.getId(), "user-1");

            assertThat(restored).isPresent();
            assertThat(restored.get().getId()).isEqualTo(session.getId());
        }

        @Test
        @DisplayName("should return empty on ownership mismatch for local session")
        void shouldReturnEmptyOnLocalOwnershipMismatch() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            registry.register(session);

            Optional<StreamSession> restored = registry.restoreSession(session.getId(), "user-2");

            assertThat(restored).isEmpty();
        }

        @Test
        @DisplayName("should return empty when session not in database")
        void shouldReturnEmptyWhenNotInDatabase() {
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.empty());

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isEmpty();
        }

        @Test
        @DisplayName("should return empty on ownership mismatch for database session")
        void shouldReturnEmptyOnDbOwnershipMismatch() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-2");

            assertThat(restored).isEmpty();
        }

        @Test
        @DisplayName("should return empty when session state is not DISCONNECTED")
        void shouldReturnEmptyWhenStateNotDisconnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.TERMINATED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isEmpty();
        }

        @Test
        @DisplayName("should restore session from database with subscriptions")
        void shouldRestoreFromDatabaseWithSubscriptions() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .instanceId("old-instance")
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            StreamSubscriptionEntity sub = StreamSubscriptionEntity.builder()
                    .sessionId("sess-1")
                    .subscriptionKey("stock:{\"symbol\":\"AAPL\"}")
                    .resource("stock")
                    .paramsJson("{\"symbol\":\"AAPL\"}")
                    .subscribedAt(Instant.now())
                    .intervalMs(1000L)
                    .active(true)
                    .build();
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of(sub));

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isPresent();
            assertThat(restored.get().getId()).isEqualTo("sess-1");
            assertThat(restored.get().getSubscriptions()).hasSize(1);

            // Verify entity was updated
            assertThat(entity.getInstanceId()).isEqualTo(INSTANCE_ID);
            assertThat(entity.getState()).isEqualTo(SessionState.CONNECTED);
            verify(sessionRepository).save(entity);
        }

        @Test
        @DisplayName("should handle invalid subscription params gracefully")
        void shouldHandleInvalidSubscriptionParams() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .instanceId("old-instance")
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            StreamSubscriptionEntity sub = StreamSubscriptionEntity.builder()
                    .sessionId("sess-1")
                    .subscriptionKey("stock:invalid")
                    .resource("stock")
                    .paramsJson("invalid-json{{{")
                    .subscribedAt(Instant.now())
                    .intervalMs(1000L)
                    .active(true)
                    .build();
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of(sub));

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isPresent();
            // Invalid subscription should be skipped
            assertThat(restored.get().getSubscriptions()).isEmpty();
        }

        @Test
        @DisplayName("should restore session with null metadata JSON")
        void shouldRestoreWithNullMetadata() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .metadataJson(null)
                    .instanceId("old-instance")
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of());

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isPresent();
        }

        @Test
        @DisplayName("should restore session with valid metadata JSON")
        void shouldRestoreWithValidMetadata() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .metadataJson("{\"timezone\":\"UTC\"}")
                    .instanceId("old-instance")
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of());

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isPresent();
            assertThat(restored.get().getMetadata()).containsEntry("timezone", "UTC");
        }

        @Test
        @DisplayName("should handle subscription with null params JSON")
        void shouldHandleSubscriptionWithNullParams() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .state(SessionState.DISCONNECTED)
                    .transportType(TransportType.SSE)
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .instanceId("old-instance")
                    .build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            StreamSubscriptionEntity sub = StreamSubscriptionEntity.builder()
                    .sessionId("sess-1")
                    .subscriptionKey("stock")
                    .resource("stock")
                    .paramsJson(null)
                    .subscribedAt(Instant.now())
                    .intervalMs(1000L)
                    .active(true)
                    .build();
            when(subscriptionRepository.findBySessionIdAndActiveTrue("sess-1"))
                    .thenReturn(List.of(sub));

            Optional<StreamSession> restored = registry.restoreSession("sess-1", "user-1");

            assertThat(restored).isPresent();
            assertThat(restored.get().getSubscriptions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update session entity in database")
        void shouldUpdateSessionEntity() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id(session.getId())
                    .state(SessionState.CONNECTED)
                    .lastActiveAt(Instant.now().minusSeconds(60))
                    .build();
            when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(entity));

            registry.update(session);

            verify(sessionRepository).save(entity);
            assertThat(entity.getLastActiveAt()).isEqualTo(session.getLastActiveAt());
        }

        @Test
        @DisplayName("should set disconnectedAt when state is DISCONNECTED")
        void shouldSetDisconnectedAtWhenDisconnected() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            session.markDisconnected();

            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id(session.getId())
                    .state(SessionState.CONNECTED)
                    .lastActiveAt(Instant.now().minusSeconds(60))
                    .build();
            when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(entity));

            registry.update(session);

            assertThat(entity.getDisconnectedAt()).isNotNull();
            verify(sessionRepository).save(entity);
        }

        @Test
        @DisplayName("should handle session not found in database")
        void shouldHandleSessionNotFound() {
            StreamSession session = StreamSession.create("user-1", TransportType.SSE);
            when(sessionRepository.findById(session.getId())).thenReturn(Optional.empty());

            registry.update(session);

            verify(sessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("touch()")
    class Touch {

        @Test
        @DisplayName("should update last active timestamp")
        void shouldUpdateLastActive() {
            registry.touch("sess-1");

            verify(sessionRepository).updateLastActiveAt(eq("sess-1"), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("countByState()")
    class CountByState {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(sessionRepository.countByState(SessionState.CONNECTED)).thenReturn(5L);

            long count = registry.countByState(SessionState.CONNECTED);

            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("countActiveByUserIdGlobal()")
    class CountActiveByUserIdGlobal {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(sessionRepository.countActiveByUserId("user-1")).thenReturn(3L);

            long count = registry.countActiveByUserIdGlobal("user-1");

            assertThat(count).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findEntityById()")
    class FindEntityById {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            StreamSessionEntity entity = StreamSessionEntity.builder().id("sess-1").build();
            when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(entity));

            Optional<StreamSessionEntity> result = registry.findEntityById("sess-1");

            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("findEntitiesByState()")
    class FindEntitiesByState {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(sessionRepository.findByState(SessionState.CONNECTED)).thenReturn(List.of());

            List<StreamSessionEntity> result = registry.findEntitiesByState(SessionState.CONNECTED);

            assertThat(result).isEmpty();
            verify(sessionRepository).findByState(SessionState.CONNECTED);
        }
    }

    @Nested
    @DisplayName("findEntitiesByInstanceId()")
    class FindEntitiesByInstanceId {

        @Test
        @DisplayName("should delegate to repository")
        void shouldDelegateToRepository() {
            when(sessionRepository.findByInstanceId("inst-1")).thenReturn(List.of());

            List<StreamSessionEntity> result = registry.findEntitiesByInstanceId("inst-1");

            assertThat(result).isEmpty();
            verify(sessionRepository).findByInstanceId("inst-1");
        }
    }
}
