package dev.simplecore.simplix.stream.core.model;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamSession.
 */
@DisplayName("StreamSession")
class StreamSessionTest {

    private StreamSession session;

    @BeforeEach
    void setUp() {
        session = StreamSession.create("user123", TransportType.SSE);
    }

    @Nested
    @DisplayName("create()")
    class CreateMethod {

        @Test
        @DisplayName("should create session with user ID and transport type")
        void shouldCreateSessionWithUserIdAndTransportType() {
            StreamSession newSession = StreamSession.create("testUser", TransportType.WEBSOCKET);

            assertNotNull(newSession.getId());
            assertEquals("testUser", newSession.getUserId());
            assertEquals(TransportType.WEBSOCKET, newSession.getTransportType());
            assertEquals(SessionState.CONNECTED, newSession.getState());
            assertNotNull(newSession.getConnectedAt());
            assertNotNull(newSession.getLastActiveAt());
        }

        @Test
        @DisplayName("should generate unique session ID")
        void shouldGenerateUniqueSessionId() {
            StreamSession session1 = StreamSession.create("user", TransportType.SSE);
            StreamSession session2 = StreamSession.create("user", TransportType.SSE);

            assertNotEquals(session1.getId(), session2.getId());
        }
    }

    @Nested
    @DisplayName("builder()")
    class BuilderMethod {

        @Test
        @DisplayName("should create session with custom ID")
        void shouldCreateSessionWithCustomId() {
            StreamSession customSession = StreamSession.builder()
                    .id("custom-id")
                    .userId("user")
                    .transportType(TransportType.SSE)
                    .build();

            assertEquals("custom-id", customSession.getId());
        }

        @Test
        @DisplayName("should create session with metadata")
        void shouldCreateSessionWithMetadata() {
            StreamSession customSession = StreamSession.builder()
                    .userId("user")
                    .transportType(TransportType.SSE)
                    .metadata(Map.of("clientIp", "127.0.0.1"))
                    .build();

            assertEquals("127.0.0.1", customSession.getMetadata("clientIp"));
        }
    }

    @Nested
    @DisplayName("lifecycle methods")
    class LifecycleMethods {

        @Test
        @DisplayName("touch() should update last active time")
        void touchShouldUpdateLastActiveTime() throws InterruptedException {
            Instant before = session.getLastActiveAt();
            Thread.sleep(10);

            session.touch();

            assertTrue(session.getLastActiveAt().isAfter(before));
        }

        @Test
        @DisplayName("markDisconnected() should change state to DISCONNECTED")
        void markDisconnectedShouldChangeState() {
            assertTrue(session.isConnected());

            session.markDisconnected();

            assertEquals(SessionState.DISCONNECTED, session.getState());
            assertFalse(session.isConnected());
            assertNotNull(session.getDisconnectedAt());
        }

        @Test
        @DisplayName("markDisconnected() should not affect terminated session")
        void markDisconnectedShouldNotAffectTerminatedSession() {
            session.markTerminated();

            session.markDisconnected();

            assertEquals(SessionState.TERMINATED, session.getState());
        }

        @Test
        @DisplayName("markReconnected() should restore CONNECTED state")
        void markReconnectedShouldRestoreConnectedState() {
            session.markDisconnected();
            Instant lastActive = session.getLastActiveAt();

            session.markReconnected();

            assertEquals(SessionState.CONNECTED, session.getState());
            assertTrue(session.isConnected());
            assertNull(session.getDisconnectedAt());
            assertTrue(session.getLastActiveAt().isAfter(lastActive) ||
                       session.getLastActiveAt().equals(lastActive));
        }

        @Test
        @DisplayName("markReconnected() should not affect connected session")
        void markReconnectedShouldNotAffectConnectedSession() {
            session.markReconnected();

            assertEquals(SessionState.CONNECTED, session.getState());
        }

        @Test
        @DisplayName("markTerminated() should change state to TERMINATED")
        void markTerminatedShouldChangeState() {
            session.markTerminated();

            assertEquals(SessionState.TERMINATED, session.getState());
            assertTrue(session.isTerminated());
            assertFalse(session.isConnected());
        }

        @Test
        @DisplayName("markTerminated() should work from any state")
        void markTerminatedShouldWorkFromAnyState() {
            session.markDisconnected();

            session.markTerminated();

            assertEquals(SessionState.TERMINATED, session.getState());
        }
    }

    @Nested
    @DisplayName("subscription management")
    class SubscriptionManagement {

        private SubscriptionKey key1;
        private SubscriptionKey key2;

        @BeforeEach
        void setUp() {
            key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));
        }

        @Test
        @DisplayName("addSubscription() should add new subscription")
        void addSubscriptionShouldAddNewSubscription() {
            boolean added = session.addSubscription(key1);

            assertTrue(added);
            assertEquals(1, session.getSubscriptionCount());
            assertTrue(session.hasSubscription(key1));
        }

        @Test
        @DisplayName("addSubscription() should return false for duplicate")
        void addSubscriptionShouldReturnFalseForDuplicate() {
            session.addSubscription(key1);

            boolean added = session.addSubscription(key1);

            assertFalse(added);
            assertEquals(1, session.getSubscriptionCount());
        }

        @Test
        @DisplayName("removeSubscription() should remove existing subscription")
        void removeSubscriptionShouldRemoveExisting() {
            session.addSubscription(key1);

            boolean removed = session.removeSubscription(key1);

            assertTrue(removed);
            assertEquals(0, session.getSubscriptionCount());
            assertFalse(session.hasSubscription(key1));
        }

        @Test
        @DisplayName("removeSubscription() should return false for non-existing")
        void removeSubscriptionShouldReturnFalseForNonExisting() {
            boolean removed = session.removeSubscription(key1);

            assertFalse(removed);
        }

        @Test
        @DisplayName("clearSubscriptions() should remove all subscriptions")
        void clearSubscriptionsShouldRemoveAll() {
            session.addSubscription(key1);
            session.addSubscription(key2);

            Set<SubscriptionKey> removed = session.clearSubscriptions();

            assertEquals(2, removed.size());
            assertTrue(removed.contains(key1));
            assertTrue(removed.contains(key2));
            assertEquals(0, session.getSubscriptionCount());
        }

        @Test
        @DisplayName("getSubscriptions() should return unmodifiable set")
        void getSubscriptionsShouldReturnUnmodifiableSet() {
            session.addSubscription(key1);

            Set<SubscriptionKey> subscriptions = session.getSubscriptions();

            assertThrows(UnsupportedOperationException.class,
                () -> subscriptions.add(key2));
        }

        @Test
        @DisplayName("hasSubscription() should return correct result")
        void hasSubscriptionShouldReturnCorrectResult() {
            session.addSubscription(key1);

            assertTrue(session.hasSubscription(key1));
            assertFalse(session.hasSubscription(key2));
        }
    }

    @Nested
    @DisplayName("metadata management")
    class MetadataManagement {

        @Test
        @DisplayName("addMetadata() should add metadata entry")
        void addMetadataShouldAddEntry() {
            session.addMetadata("clientIp", "192.168.1.1");

            assertEquals("192.168.1.1", session.getMetadata("clientIp"));
        }

        @Test
        @DisplayName("addMetadata() should overwrite existing entry")
        void addMetadataShouldOverwriteExisting() {
            session.addMetadata("key", "value1");
            session.addMetadata("key", "value2");

            assertEquals("value2", session.getMetadata("key"));
        }

        @Test
        @DisplayName("getMetadata() should return null for non-existing key")
        void getMetadataShouldReturnNullForNonExisting() {
            assertNull(session.getMetadata("nonExisting"));
        }

        @Test
        @DisplayName("getMetadata() should cast to correct type")
        void getMetadataShouldCastToCorrectType() {
            session.addMetadata("count", 42);

            Integer count = session.getMetadata("count");

            assertEquals(42, count);
        }
    }
}
