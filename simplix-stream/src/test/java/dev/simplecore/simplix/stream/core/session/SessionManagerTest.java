package dev.simplecore.simplix.stream.core.session;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.exception.SessionExpiredException;
import dev.simplecore.simplix.stream.exception.SessionNotFoundException;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for SessionManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager")
class SessionManagerTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private ScheduledExecutorService scheduledExecutor;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private StreamProperties properties;
    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setSession(new StreamProperties.SessionConfig());
        properties.getSession().setMaxPerUser(5);
        properties.getSession().setGracePeriod(Duration.ofSeconds(30));
        properties.getSession().setTimeout(Duration.ofMinutes(5));

        sessionManager = new SessionManager(sessionRegistry, properties, scheduledExecutor);
    }

    @Nested
    @DisplayName("createSession()")
    class CreateSessionMethod {

        @Test
        @DisplayName("should create and register new session")
        void shouldCreateAndRegisterNewSession() {
            when(sessionRegistry.countByUserId("user123")).thenReturn(0L);

            StreamSession session = sessionManager.createSession("user123", TransportType.SSE);

            assertNotNull(session);
            assertEquals("user123", session.getUserId());
            assertEquals(TransportType.SSE, session.getTransportType());
            assertEquals(SessionState.CONNECTED, session.getState());
            verify(sessionRegistry).register(session);
        }

        @Test
        @DisplayName("should generate unique session ID")
        void shouldGenerateUniqueSessionId() {
            when(sessionRegistry.countByUserId("user123")).thenReturn(0L);

            StreamSession session1 = sessionManager.createSession("user123", TransportType.SSE);
            StreamSession session2 = sessionManager.createSession("user123", TransportType.WEBSOCKET);

            assertNotEquals(session1.getId(), session2.getId());
        }

        @Test
        @DisplayName("should terminate oldest session when limit exceeded")
        void shouldTerminateOldestSessionWhenLimitExceeded() throws InterruptedException {
            properties.getSession().setMaxPerUser(2);

            // Create sessions with slight time difference
            StreamSession oldest = StreamSession.builder()
                    .id("oldest")
                    .userId("user123")
                    .transportType(TransportType.SSE)
                    .build();

            Thread.sleep(10); // Ensure time difference

            StreamSession newer = StreamSession.builder()
                    .id("newer")
                    .userId("user123")
                    .transportType(TransportType.SSE)
                    .build();

            when(sessionRegistry.countByUserId("user123")).thenReturn(2L);
            when(sessionRegistry.findByUserId("user123")).thenReturn(List.of(oldest, newer));
            when(sessionRegistry.findById("oldest")).thenReturn(Optional.of(oldest));

            sessionManager.createSession("user123", TransportType.SSE);

            verify(sessionRegistry).unregister("oldest");
        }

        @Test
        @DisplayName("should not limit sessions when maxPerUser is 0")
        void shouldNotLimitSessionsWhenMaxPerUserIsZero() {
            properties.getSession().setMaxPerUser(0);

            // No stubbing needed - maxPerUser is 0 so countByUserId won't be called

            assertDoesNotThrow(() -> sessionManager.createSession("user123", TransportType.SSE));
        }
    }

    @Nested
    @DisplayName("getSession()")
    class GetSessionMethod {

        @Test
        @DisplayName("should return session when found")
        void shouldReturnSessionWhenFound() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            StreamSession result = sessionManager.getSession(session.getId());

            assertEquals(session, result);
        }

        @Test
        @DisplayName("should throw SessionNotFoundException when not found")
        void shouldThrowSessionNotFoundExceptionWhenNotFound() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            assertThrows(SessionNotFoundException.class,
                    () -> sessionManager.getSession("nonexistent"));
        }
    }

    @Nested
    @DisplayName("findSession()")
    class FindSessionMethod {

        @Test
        @DisplayName("should return optional with session when found")
        void shouldReturnOptionalWithSessionWhenFound() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            Optional<StreamSession> result = sessionManager.findSession(session.getId());

            assertTrue(result.isPresent());
            assertEquals(session, result.get());
        }

        @Test
        @DisplayName("should return empty optional when not found")
        void shouldReturnEmptyOptionalWhenNotFound() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            Optional<StreamSession> result = sessionManager.findSession("nonexistent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getSessionForUser()")
    class GetSessionForUserMethod {

        @Test
        @DisplayName("should return session when user owns it")
        void shouldReturnSessionWhenUserOwnsIt() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            StreamSession result = sessionManager.getSessionForUser(session.getId(), "user123");

            assertEquals(session, result);
        }

        @Test
        @DisplayName("should throw SecurityException when user does not own session")
        void shouldThrowSecurityExceptionWhenUserDoesNotOwnSession() {
            StreamSession session = StreamSession.create("owner", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            assertThrows(SecurityException.class,
                    () -> sessionManager.getSessionForUser(session.getId(), "intruder"));
        }

        @Test
        @DisplayName("should throw SessionExpiredException when session is terminated")
        void shouldThrowSessionExpiredExceptionWhenSessionIsTerminated() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            session.markTerminated();
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            assertThrows(SessionExpiredException.class,
                    () -> sessionManager.getSessionForUser(session.getId(), "user123"));
        }

        @Test
        @DisplayName("should throw SessionNotFoundException when session not found")
        void shouldThrowSessionNotFoundExceptionWhenSessionNotFound() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            assertThrows(SessionNotFoundException.class,
                    () -> sessionManager.getSessionForUser("nonexistent", "user123"));
        }
    }

    @Nested
    @DisplayName("markDisconnected()")
    class MarkDisconnectedMethod {

        @Test
        @DisplayName("should change state to DISCONNECTED")
        void shouldChangeStateToDisconnected() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

            sessionManager.markDisconnected(session.getId());

            assertEquals(SessionState.DISCONNECTED, session.getState());
        }

        @Test
        @DisplayName("should start grace period timer")
        void shouldStartGracePeriodTimer() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

            sessionManager.markDisconnected(session.getId());

            verify(scheduledExecutor).schedule(
                    any(Runnable.class),
                    eq(30000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("should not change already disconnected session")
        void shouldNotChangeAlreadyDisconnectedSession() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            session.markDisconnected();
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            sessionManager.markDisconnected(session.getId());

            verify(scheduledExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("reconnect()")
    class ReconnectMethod {

        @Test
        @DisplayName("should restore CONNECTED state")
        void shouldRestoreConnectedState() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            session.markDisconnected();
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            boolean result = sessionManager.reconnect(session.getId());

            assertTrue(result);
            assertEquals(SessionState.CONNECTED, session.getState());
        }

        @Test
        @DisplayName("should return false for non-existing session")
        void shouldReturnFalseForNonExistingSession() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            boolean result = sessionManager.reconnect("nonexistent");

            assertFalse(result);
        }

        @Test
        @DisplayName("should return true for already connected session")
        void shouldReturnTrueForAlreadyConnectedSession() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            boolean result = sessionManager.reconnect(session.getId());

            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("terminateSession()")
    class TerminateSessionMethod {

        @Test
        @DisplayName("should mark session as terminated")
        void shouldMarkSessionAsTerminated() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            sessionManager.terminateSession(session.getId());

            assertEquals(SessionState.TERMINATED, session.getState());
        }

        @Test
        @DisplayName("should unregister session from registry")
        void shouldUnregisterSessionFromRegistry() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            sessionManager.terminateSession(session.getId());

            verify(sessionRegistry).unregister(session.getId());
        }

        @Test
        @DisplayName("should return cleared subscriptions")
        void shouldReturnClearedSubscriptions() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            session.addSubscription(key);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            Set<SubscriptionKey> cleared = sessionManager.terminateSession(session.getId());

            assertEquals(1, cleared.size());
            assertTrue(cleared.contains(key));
        }

        @Test
        @DisplayName("should trigger onSessionTerminated callback")
        void shouldTriggerOnSessionTerminatedCallback() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            AtomicReference<StreamSession> terminatedSession = new AtomicReference<>();
            sessionManager.setOnSessionTerminated(terminatedSession::set);

            sessionManager.terminateSession(session.getId());

            assertEquals(session, terminatedSession.get());
        }

        @Test
        @DisplayName("should return empty set for non-existing session")
        void shouldReturnEmptySetForNonExistingSession() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            Set<SubscriptionKey> cleared = sessionManager.terminateSession("nonexistent");

            assertTrue(cleared.isEmpty());
        }
    }

    @Nested
    @DisplayName("touch()")
    class TouchMethod {

        @Test
        @DisplayName("should update lastActiveAt")
        void shouldUpdateLastActiveAt() throws InterruptedException {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            Instant before = session.getLastActiveAt();
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            Thread.sleep(10);
            sessionManager.touch(session.getId());

            assertTrue(session.getLastActiveAt().isAfter(before));
        }

        @Test
        @DisplayName("should do nothing for non-existing session")
        void shouldDoNothingForNonExistingSession() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> sessionManager.touch("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getSessionsByUser()")
    class GetSessionsByUserMethod {

        @Test
        @DisplayName("should return all sessions for user")
        void shouldReturnAllSessionsForUser() {
            StreamSession session1 = StreamSession.create("user123", TransportType.SSE);
            StreamSession session2 = StreamSession.create("user123", TransportType.WEBSOCKET);
            when(sessionRegistry.findByUserId("user123")).thenReturn(List.of(session1, session2));

            Collection<StreamSession> sessions = sessionManager.getSessionsByUser("user123");

            assertEquals(2, sessions.size());
        }
    }

    @Nested
    @DisplayName("getAllSessions()")
    class GetAllSessionsMethod {

        @Test
        @DisplayName("should return all sessions")
        void shouldReturnAllSessions() {
            StreamSession session1 = StreamSession.create("user1", TransportType.SSE);
            StreamSession session2 = StreamSession.create("user2", TransportType.SSE);
            when(sessionRegistry.findAll()).thenReturn(List.of(session1, session2));

            Collection<StreamSession> sessions = sessionManager.getAllSessions();

            assertEquals(2, sessions.size());
        }
    }

    @Nested
    @DisplayName("getSessionCount()")
    class GetSessionCountMethod {

        @Test
        @DisplayName("should return total session count")
        void shouldReturnTotalSessionCount() {
            when(sessionRegistry.count()).thenReturn(10L);

            long count = sessionManager.getSessionCount();

            assertEquals(10L, count);
        }
    }

    @Nested
    @DisplayName("cleanupInactiveSessions()")
    class CleanupInactiveSessionsMethod {

        @Test
        @DisplayName("should not mark recently active sessions as disconnected")
        void shouldNotMarkRecentlyActiveSessionsAsDisconnected() {
            // A newly created session should not be marked as inactive
            StreamSession activeSession = StreamSession.create("user123", TransportType.SSE);
            when(sessionRegistry.findAll()).thenReturn(List.of(activeSession));

            sessionManager.cleanupInactiveSessions();

            // Session should remain connected because it was just created
            assertEquals(SessionState.CONNECTED, activeSession.getState());
        }

        @Test
        @DisplayName("should skip cleanup when timeout is zero")
        void shouldSkipCleanupWhenTimeoutIsZero() {
            properties.getSession().setTimeout(Duration.ZERO);

            sessionManager.cleanupInactiveSessions();

            verify(sessionRegistry, never()).findAll();
        }
    }
}
