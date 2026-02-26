package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
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
    private ScheduledFuture<?> scheduledFuture;

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

        controller = new SseStreamController(
                sessionManager,
                subscriptionManager,
                collectorRegistry,
                broadcaster,
                authorizationService,
                properties,
                objectMapper,
                scheduledExecutor
        );

        // Set up authentication for tests
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user123", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
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

            SseEmitter emitter = controller.connect();

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

            controller.connect();

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

            controller.connect();

            assertEquals(1, controller.getActiveSessionCount());
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
