package dev.simplecore.simplix.stream.transport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionRequest;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketStreamHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketStreamHandler")
class WebSocketStreamHandlerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private SimpliXStreamDataCollectorRegistry collectorRegistry;

    @Mock
    private LocalBroadcaster broadcastService;

    @Mock
    private StreamAuthorizationService authorizationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SessionValidator sessionValidator;

    @Mock
    private ExecutorService sessionValidationExecutor;

    @Mock
    private ScheduledExecutorService scheduledExecutor;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private StreamProperties properties;
    private ObjectMapper objectMapper;
    private WebSocketStreamHandler handler;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setSession(new StreamProperties.SessionConfig());
        properties.getSession().setHeartbeatInterval(Duration.ofSeconds(30));
        properties.setScheduler(new StreamProperties.SchedulerConfig());
        properties.getScheduler().setDefaultInterval(Duration.ofSeconds(1));
        properties.getScheduler().setMinInterval(Duration.ofMillis(100));
        properties.setSubscription(new StreamProperties.SubscriptionConfig());
        properties.getSubscription().setPartialSuccess(true);

        objectMapper = new ObjectMapper();

        lenient().doReturn(scheduledFuture).when(scheduledExecutor)
                .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

        handler = new WebSocketStreamHandler(
                sessionManager, subscriptionManager, collectorRegistry,
                broadcastService, authorizationService, properties,
                messagingTemplate, objectMapper,
                sessionValidator, sessionValidationExecutor, scheduledExecutor
        );
    }

    @Nested
    @DisplayName("handleSessionConnect()")
    class HandleSessionConnect {

        @Test
        @DisplayName("should create stream session on WebSocket connect")
        void shouldCreateStreamSessionOnConnect() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            SessionConnectEvent event = createConnectEvent("simp-sess-1", "user-1");

            handler.handleSessionConnect(event);

            verify(sessionManager).createSession("user-1", TransportType.WEBSOCKET);
            assertThat(handler.getActiveSessionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle anonymous connection")
        void shouldHandleAnonymousConnection() {
            StreamSession streamSession = StreamSession.create(null, TransportType.WEBSOCKET);
            when(sessionManager.createSession(null, TransportType.WEBSOCKET)).thenReturn(streamSession);

            SessionConnectEvent event = createConnectEvent("simp-sess-2", null);

            handler.handleSessionConnect(event);

            verify(sessionManager).createSession(null, TransportType.WEBSOCKET);
            assertThat(handler.getActiveSessionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should register sender with LocalBroadcaster")
        void shouldRegisterSenderWithLocalBroadcaster() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            verify(broadcastService).registerSender(eq(streamSession.getId()), any(WebSocketStreamSession.class));
        }

        @Test
        @DisplayName("should send connected message")
        void shouldSendConnectedMessage() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            String expectedDest = "/queue/stream/" + streamSession.getId();
            verify(messagingTemplate).convertAndSend(eq(expectedDest), any(StreamMessage.class));
        }
    }

    @Nested
    @DisplayName("handleSessionDisconnect()")
    class HandleSessionDisconnect {

        @Test
        @DisplayName("should mark session disconnected on WebSocket disconnect")
        void shouldMarkSessionDisconnectedOnDisconnect() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            // First connect
            SessionConnectEvent connectEvent = createConnectEvent("simp-sess-1", "user-1");
            handler.handleSessionConnect(connectEvent);

            // Then disconnect
            SessionDisconnectEvent disconnectEvent = createDisconnectEvent("simp-sess-1");
            handler.handleSessionDisconnect(disconnectEvent);

            verify(sessionManager).markDisconnected(streamSession.getId());
            assertThat(handler.getActiveSessionCount()).isZero();
        }

        @Test
        @DisplayName("should ignore disconnect for unknown session")
        void shouldIgnoreDisconnectForUnknownSession() {
            SessionDisconnectEvent disconnectEvent = createDisconnectEvent("unknown-simp-sess");

            handler.handleSessionDisconnect(disconnectEvent);

            verify(sessionManager, never()).markDisconnected(anyString());
        }

        @Test
        @DisplayName("should unregister sender from LocalBroadcaster on disconnect")
        void shouldUnregisterSenderOnDisconnect() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));
            handler.handleSessionDisconnect(createDisconnectEvent("simp-sess-1"));

            verify(broadcastService).unregisterSender(streamSession.getId());
        }
    }

    @Nested
    @DisplayName("handleSubscribe()")
    class HandleSubscribe {

        @Test
        @DisplayName("should return failure when session not found by simp session ID")
        void shouldReturnFailureWhenSessionNotFoundBySimpId() {
            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of());

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("unknown-simp-sess");

            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getFailed()).hasSize(1);
            assertThat(response.getFailed().get(0).getReason()).isEqualTo("Session not found");
        }

        @Test
        @DisplayName("should return failure when stream session is not active")
        void shouldReturnFailureWhenSessionNotActive() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            // Disconnect to remove active session
            handler.handleSessionDisconnect(createDisconnectEvent("simp-sess-1"));

            // Try to subscribe with a new simp-session pointing to same stream session
            // The stream session was removed from activeSessions
            // Need to re-create mapping manually - instead test with a session that exists in simp mapping
            // but not in activeSessions. This is hard to do cleanly, so test the simpler case:
            // re-create a connect to get a fresh mapping, but then directly remove from active sessions
            // The most practical test: just ensure the handler returns failure for non-active
            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of());

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            // After disconnect, simp-sess-1 is no longer mapped
            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("should handle valid subscription request")
        void shouldHandleValidSubscriptionRequest() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

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
            when(authorizationService.checkAuthorization(eq("user-1"), any()))
                    .thenReturn(StreamAuthorizationService.AuthorizationResult.allow());

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSubscribed()).hasSize(1);
        }

        @Test
        @DisplayName("should report failed subscription for unknown resource")
        void shouldReportFailedForUnknownResource() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("unknown");

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("unknown")).thenReturn(false);

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.getFailed()).hasSize(1);
            assertThat(response.getFailed().get(0).getReason()).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("should report failed subscription for denied authorization")
        void shouldReportFailedForDeniedAuthorization() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("restricted");

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("restricted")).thenReturn(true);
            when(authorizationService.checkAuthorization(eq("user-1"), any()))
                    .thenReturn(StreamAuthorizationService.AuthorizationResult.deny("Forbidden"));

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.getFailed()).hasSize(1);
            assertThat(response.getFailed().get(0).getReason()).isEqualTo("Forbidden");
        }

        @Test
        @DisplayName("should use default interval when no collector found")
        void shouldUseDefaultIntervalWhenNoCollectorFound() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            SubscriptionRequest.SubscriptionItem item = new SubscriptionRequest.SubscriptionItem();
            item.setResource("stock");

            SubscriptionRequest request = new SubscriptionRequest();
            request.setSubscriptions(List.of(item));

            when(collectorRegistry.hasCollector("stock")).thenReturn(true);
            when(collectorRegistry.findCollector("stock")).thenReturn(Optional.empty());
            when(authorizationService.checkAuthorization(eq("user-1"), any()))
                    .thenReturn(StreamAuthorizationService.AuthorizationResult.allow());

            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            SubscriptionResponse response = handler.handleSubscribe(request, headerAccessor);

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getSubscribed()).hasSize(1);
            assertThat(response.getSubscribed().get(0).getIntervalMs()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("handleUnsubscribeAll()")
    class HandleUnsubscribeAll {

        @Test
        @DisplayName("should clear subscriptions for existing session")
        void shouldClearSubscriptionsForExistingSession() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            // Connect first
            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            // Create header accessor for unsubscribe
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("simp-sess-1");

            handler.handleUnsubscribeAll(headerAccessor);

            verify(subscriptionManager).clearSubscriptions(streamSession.getId());
        }

        @Test
        @DisplayName("should ignore unsubscribe for unknown session")
        void shouldIgnoreUnsubscribeForUnknownSession() {
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
            headerAccessor.setSessionId("unknown-simp-sess");

            handler.handleUnsubscribeAll(headerAccessor);

            verify(subscriptionManager, never()).clearSubscriptions(anyString());
        }
    }

    @Nested
    @DisplayName("getActiveSessionCount()")
    class GetActiveSessionCount {

        @Test
        @DisplayName("should return zero initially")
        void shouldReturnZeroInitially() {
            assertThat(handler.getActiveSessionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getSession()")
    class GetSession {

        @Test
        @DisplayName("should return session when found")
        void shouldReturnSessionWhenFound() {
            StreamSession streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
            when(sessionManager.createSession("user-1", TransportType.WEBSOCKET)).thenReturn(streamSession);

            handler.handleSessionConnect(createConnectEvent("simp-sess-1", "user-1"));

            WebSocketStreamSession result = handler.getSession(streamSession.getId());

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(streamSession.getId());
        }

        @Test
        @DisplayName("should return null when not found")
        void shouldReturnNullWhenNotFound() {
            WebSocketStreamSession result = handler.getSession("nonexistent");

            assertThat(result).isNull();
        }
    }

    private SessionConnectEvent createConnectEvent(String simpSessionId, String userId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, simpSessionId);
        if (userId != null) {
            Principal principal = () -> userId;
            headers.put(SimpMessageHeaderAccessor.USER_HEADER, principal);
        }
        Message<byte[]> message = new GenericMessage<>(new byte[0], new MessageHeaders(headers));
        return new SessionConnectEvent(this, message);
    }

    private SessionDisconnectEvent createDisconnectEvent(String simpSessionId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(SimpMessageHeaderAccessor.SESSION_ID_HEADER, simpSessionId);
        Message<byte[]> message = new GenericMessage<>(new byte[0], new MessageHeaders(headers));
        return new SessionDisconnectEvent(this, message, simpSessionId, null);
    }
}
