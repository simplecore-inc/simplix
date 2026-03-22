package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.exception.SessionNotFoundException;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionRequest;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SseStreamController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SseStreamController")
class SseStreamControllerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private SimpliXStreamDataCollectorRegistry collectorRegistry;

    @Mock
    private LocalBroadcaster broadcaster;

    @Mock
    private StreamAuthorizationService authorizationService;

    @Mock
    private ScheduledExecutorService scheduledExecutor;

    @Mock
    private SessionValidator sessionValidator;

    @Mock
    private ExecutorService sessionValidationExecutor;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    @Mock
    private HttpServletRequest mockRequest;

    private StreamProperties properties;
    private ObjectMapper objectMapper;
    private SseStreamController controller;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setSession(new StreamProperties.SessionConfig());
        properties.getSession().setTimeout(Duration.ofMinutes(5));
        properties.getSession().setHeartbeatInterval(Duration.ofSeconds(30));
        properties.setScheduler(new StreamProperties.SchedulerConfig());
        properties.getScheduler().setDefaultInterval(Duration.ofSeconds(1));
        properties.getScheduler().setMinInterval(Duration.ofMillis(100));
        properties.getScheduler().setMaxInterval(Duration.ofMinutes(1));
        properties.setSubscription(new StreamProperties.SubscriptionConfig());
        properties.getSubscription().setPartialSuccess(true);

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        controller = new SseStreamController(
                sessionManager,
                subscriptionManager,
                collectorRegistry,
                broadcaster,
                authorizationService,
                properties,
                objectMapper,
                scheduledExecutor,
                sessionValidator,
                sessionValidationExecutor
        );

        // Set up authentication for tests
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user123", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("connect()")
    class ConnectMethod {

        @Test
        @DisplayName("should establish SSE connection and return emitter")
        void shouldEstablishSseConnection() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SseEmitter emitter = controller.connect(null, mockRequest);

            assertNotNull(emitter);
            verify(sessionManager).createSession("user123", TransportType.SSE);
            verify(broadcaster).registerSender(eq(session.getId()), any(SseStreamSession.class));
        }

        @Test
        @DisplayName("should start heartbeat task")
        void shouldStartHeartbeatTask() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            verify(scheduledExecutor).scheduleAtFixedRate(
                    any(Runnable.class),
                    eq(30000L),
                    eq(30000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("should increment active session count")
        void shouldIncrementActiveSessionCount() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            assertEquals(0, controller.getActiveSessionCount());

            controller.connect(null, mockRequest);

            assertEquals(1, controller.getActiveSessionCount());
        }

        @Test
        @DisplayName("should store connect params as session metadata")
        void shouldStoreConnectParamsAsMetadata() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            Map<String, String> connectParams = Map.of("timezone", "UTC", "locale", "en");
            controller.connect(connectParams, mockRequest);

            assertEquals("UTC", session.getMetadata().get("timezone"));
            assertEquals("en", session.getMetadata().get("locale"));
        }

        @Test
        @DisplayName("should handle null userId for anonymous connection")
        void shouldHandleAnonymousConnection() {
            SecurityContextHolder.clearContext();

            StreamSession session = StreamSession.create(null, TransportType.SSE);
            when(sessionManager.createSession(isNull(), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SseEmitter emitter = controller.connect(null, mockRequest);

            assertNotNull(emitter);
            verify(sessionManager).createSession(null, TransportType.SSE);
        }
    }

    @Nested
    @DisplayName("updateSubscriptions()")
    class UpdateSubscriptionsMethod {

        @Test
        @DisplayName("should throw SessionNotFoundException for unknown session")
        void shouldThrowSessionNotFoundForUnknownSession() {
            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of());

            assertThrows(SessionNotFoundException.class,
                    () -> controller.updateSubscriptions("unknown-session", request));
        }

        @Test
        @DisplayName("should update subscriptions for valid session")
        void shouldUpdateSubscriptionsForValidSession() {
            // First connect to create a session
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            // Now update subscriptions
            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("stock");
            item.setParams(Map.of("symbol", "AAPL"));

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("stock")).thenReturn(true);
            SimpliXStreamDataCollector collector = mock(SimpliXStreamDataCollector.class);
            when(collector.getDefaultIntervalMs()).thenReturn(1000L);
            when(collector.getMinIntervalMs()).thenReturn(100L);
            when(collectorRegistry.findCollector("stock")).thenReturn(Optional.of(collector));
            when(authorizationService.checkAuthorization(eq("user123"), any()))
                    .thenReturn(StreamAuthorizationService.AuthorizationResult.allow());

            ResponseEntity<SubscriptionResponse> response =
                    controller.updateSubscriptions(session.getId(), request);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(1, response.getBody().getSubscribed().size());
        }

        @Test
        @DisplayName("should report failed subscriptions for unknown resources")
        void shouldReportFailedSubscriptionsForUnknownResources() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("nonexistent-resource");

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("nonexistent-resource")).thenReturn(false);

            ResponseEntity<SubscriptionResponse> response =
                    controller.updateSubscriptions(session.getId(), request);

            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getFailed().size());
            assertEquals("Resource not found", response.getBody().getFailed().get(0).getReason());
        }

        @Test
        @DisplayName("should report failed subscriptions for denied authorization")
        void shouldReportFailedSubscriptionsForDeniedAuthorization() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("restricted");

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("restricted")).thenReturn(true);
            when(authorizationService.checkAuthorization(eq("user123"), any()))
                    .thenReturn(StreamAuthorizationService.AuthorizationResult.deny("Not authorized"));

            ResponseEntity<SubscriptionResponse> response =
                    controller.updateSubscriptions(session.getId(), request);

            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().getFailed().size());
            assertEquals("Not authorized", response.getBody().getFailed().get(0).getReason());
        }

        @Test
        @DisplayName("should throw SecurityException when user does not own session")
        void shouldThrowSecurityExceptionWhenUserDoesNotOwnSession() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            // Change authenticated user
            UsernamePasswordAuthenticationToken otherAuth =
                    new UsernamePasswordAuthenticationToken("other-user", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(otherAuth);

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of());

            assertThrows(SecurityException.class,
                    () -> controller.updateSubscriptions(session.getId(), request));
        }
    }

    @Nested
    @DisplayName("getSubscriptions()")
    class GetSubscriptionsMethod {

        @Test
        @DisplayName("should throw SessionNotFoundException for unknown session")
        void shouldThrowSessionNotFoundForUnknownSession() {
            assertThrows(SessionNotFoundException.class,
                    () -> controller.getSubscriptions("unknown-session"));
        }

        @Test
        @DisplayName("should return current subscriptions for valid session")
        void shouldReturnCurrentSubscriptions() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            session.addSubscription(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")));
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            ResponseEntity<List<SubscriptionResponse.SubscribedResource>> response =
                    controller.getSubscriptions(session.getId());

            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals("stock", response.getBody().get(0).getResource());
        }
    }

    @Nested
    @DisplayName("disconnect()")
    class DisconnectMethod {

        @Test
        @DisplayName("should throw SessionNotFoundException for unknown session")
        void shouldThrowSessionNotFoundForUnknownSession() {
            assertThrows(SessionNotFoundException.class,
                    () -> controller.disconnect("unknown-session"));
        }

        @Test
        @DisplayName("should cleanup session on disconnect")
        void shouldCleanupSessionOnDisconnect() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            when(sessionManager.createSession(eq("user123"), eq(TransportType.SSE)))
                    .thenReturn(session);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            controller.connect(null, mockRequest);

            ResponseEntity<Void> response = controller.disconnect(session.getId());

            assertEquals(204, response.getStatusCode().value());
            verify(sessionManager).terminateSession(session.getId());
            verify(subscriptionManager).clearSubscriptions(session.getId());
            verify(broadcaster).unregisterSender(session.getId());
            assertEquals(0, controller.getActiveSessionCount());
        }
    }

    @Nested
    @DisplayName("reconnect()")
    class ReconnectMethod {

        @Test
        @DisplayName("should throw SessionNotFoundException when restore fails")
        void shouldThrowSessionNotFoundWhenRestoreFails() {
            when(sessionManager.restoreSession("sess-1", "user123")).thenReturn(Optional.empty());
            when(sessionManager.reconnect("sess-1")).thenReturn(false);

            assertThrows(SessionNotFoundException.class,
                    () -> controller.reconnect("sess-1", mockRequest));
        }

        @Test
        @DisplayName("should reconnect session with restore")
        void shouldReconnectSessionWithRestore() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);
            session.addSubscription(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")));

            when(sessionManager.restoreSession("sess-1", "user123"))
                    .thenReturn(Optional.of(session));
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SseEmitter emitter = controller.reconnect("sess-1", mockRequest);

            assertNotNull(emitter);
            verify(broadcaster).registerSender(eq("sess-1"), any(SseStreamSession.class));
        }

        @Test
        @DisplayName("should fallback to same-server reconnection")
        void shouldFallbackToSameServerReconnection() {
            StreamSession session = StreamSession.create("user123", TransportType.SSE);

            when(sessionManager.restoreSession("sess-1", "user123"))
                    .thenReturn(Optional.empty());
            when(sessionManager.reconnect("sess-1")).thenReturn(true);
            when(sessionManager.findSession("sess-1")).thenReturn(Optional.of(session));
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SseEmitter emitter = controller.reconnect("sess-1", mockRequest);

            assertNotNull(emitter);
        }
    }

    @Nested
    @DisplayName("getActiveSessionCount()")
    class GetActiveSessionCountMethod {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertEquals(0, controller.getActiveSessionCount());
        }
    }
}
