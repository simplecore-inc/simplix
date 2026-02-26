package dev.simplecore.simplix.stream.infrastructure.local;

import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LocalSessionRegistry.
 */
@DisplayName("LocalSessionRegistry")
class LocalSessionRegistryTest {

    private LocalSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LocalSessionRegistry();
        registry.initialize();
    }

    @Nested
    @DisplayName("register()")
    class RegisterMethod {

        @Test
        @DisplayName("should register session")
        void shouldRegisterSession() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);

            registry.register(session);

            assertEquals(1, registry.count());
            assertTrue(registry.findById(session.getId()).isPresent());
        }

        @Test
        @DisplayName("should overwrite existing session with same ID")
        void shouldOverwriteExistingSessionWithSameId() {
            StreamSession session1 = StreamSession.builder()
                    .id("same-id")
                    .userId("user1")
                    .transportType(TransportType.SSE)
                    .build();

            StreamSession session2 = StreamSession.builder()
                    .id("same-id")
                    .userId("user2")
                    .transportType(TransportType.WEBSOCKET)
                    .build();

            registry.register(session1);
            registry.register(session2);

            assertEquals(1, registry.count());
            assertEquals("user2", registry.findById("same-id").get().getUserId());
        }
    }

    @Nested
    @DisplayName("unregister()")
    class UnregisterMethod {

        @Test
        @DisplayName("should remove registered session")
        void shouldRemoveRegisteredSession() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            registry.register(session);

            registry.unregister(session.getId());

            assertEquals(0, registry.count());
            assertFalse(registry.findById(session.getId()).isPresent());
        }

        @Test
        @DisplayName("should do nothing for non-existing session")
        void shouldDoNothingForNonExistingSession() {
            assertDoesNotThrow(() -> registry.unregister("nonexistent"));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdMethod {

        @Test
        @DisplayName("should return session when found")
        void shouldReturnSessionWhenFound() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            registry.register(session);

            Optional<StreamSession> result = registry.findById(session.getId());

            assertTrue(result.isPresent());
            assertEquals(session, result.get());
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<StreamSession> result = registry.findById("nonexistent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserIdMethod {

        @Test
        @DisplayName("should return all sessions for user")
        void shouldReturnAllSessionsForUser() {
            StreamSession session1 = StreamSession.create("user123", TransportType.SSE);
            StreamSession session2 = StreamSession.create("user123", TransportType.WEBSOCKET);
            StreamSession session3 = StreamSession.create("other", TransportType.SSE);

            registry.register(session1);
            registry.register(session2);
            registry.register(session3);

            Collection<StreamSession> result = registry.findByUserId("user123");

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty collection for unknown user")
        void shouldReturnEmptyCollectionForUnknownUser() {
            Collection<StreamSession> result = registry.findByUserId("unknown");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAllMethod {

        @Test
        @DisplayName("should return all sessions")
        void shouldReturnAllSessions() {
            StreamSession session1 = StreamSession.create("user1", TransportType.SSE);
            StreamSession session2 = StreamSession.create("user2", TransportType.SSE);

            registry.register(session1);
            registry.register(session2);

            Collection<StreamSession> result = registry.findAll();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty collection when no sessions")
        void shouldReturnEmptyCollectionWhenNoSessions() {
            Collection<StreamSession> result = registry.findAll();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("count()")
    class CountMethod {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertEquals(0, registry.count());

            registry.register(StreamSession.create("user1", TransportType.SSE));
            assertEquals(1, registry.count());

            registry.register(StreamSession.create("user2", TransportType.SSE));
            assertEquals(2, registry.count());
        }
    }

    @Nested
    @DisplayName("countByUserId()")
    class CountByUserIdMethod {

        @Test
        @DisplayName("should return count for specific user")
        void shouldReturnCountForSpecificUser() {
            registry.register(StreamSession.create("user1", TransportType.SSE));
            registry.register(StreamSession.create("user1", TransportType.WEBSOCKET));
            registry.register(StreamSession.create("user2", TransportType.SSE));

            assertEquals(2, registry.countByUserId("user1"));
            assertEquals(1, registry.countByUserId("user2"));
            assertEquals(0, registry.countByUserId("unknown"));
        }
    }

    @Nested
    @DisplayName("lifecycle methods")
    class LifecycleMethods {

        @Test
        @DisplayName("initialize should set available to true")
        void initializeShouldSetAvailableToTrue() {
            LocalSessionRegistry newRegistry = new LocalSessionRegistry();
            assertFalse(newRegistry.isAvailable());

            newRegistry.initialize();

            assertTrue(newRegistry.isAvailable());
        }

        @Test
        @DisplayName("shutdown should set available to false and clear sessions")
        void shutdownShouldSetAvailableToFalseAndClearSessions() {
            registry.register(StreamSession.create("user1", TransportType.SSE));
            assertTrue(registry.isAvailable());
            assertEquals(1, registry.count());

            registry.shutdown();

            assertFalse(registry.isAvailable());
            assertEquals(0, registry.count());
        }
    }
}
