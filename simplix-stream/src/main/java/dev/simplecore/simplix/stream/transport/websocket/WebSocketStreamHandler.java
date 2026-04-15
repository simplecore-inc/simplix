package dev.simplecore.simplix.stream.transport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.Subscription;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionRequest;
import dev.simplecore.simplix.stream.transport.dto.SubscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * WebSocket STOMP message handler for stream operations.
 * <p>
 * Handles WebSocket connections, subscriptions, and message routing.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketStreamHandler {

    private final SessionManager sessionManager;
    private final SubscriptionManager subscriptionManager;
    private final SimpliXStreamDataCollectorRegistry collectorRegistry;
    private final BroadcastService broadcastService;
    private final StreamAuthorizationService authorizationService;
    private final StreamProperties properties;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final SessionValidator sessionValidator;
    private final ExecutorService sessionValidationExecutor;
    private final ScheduledExecutorService streamScheduledExecutor;

    private final Map<String, WebSocketStreamSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, String> simpSessionToStreamSession = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    /**
     * Handle WebSocket connection events.
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = headers.getSessionId();
        Principal user = headers.getUser();
        String userId = user != null ? user.getName() : null;

        // Create stream session
        StreamSession streamSession = sessionManager.createSession(userId, TransportType.WEBSOCKET);
        String streamSessionId = streamSession.getId();

        // Extract bearer token from handshake headers
        Map<String, List<String>> nativeHeaders = headers.toNativeHeaderMap();
        if (nativeHeaders != null) {
            List<String> authHeaders = nativeHeaders.get("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    streamSession.addTransientMetadata("bearerToken", authHeader.substring(7));
                }
            }
        }

        // Create WebSocket session wrapper
        WebSocketStreamSession wsSession = new WebSocketStreamSession(
                streamSession, messagingTemplate, objectMapper);
        activeSessions.put(streamSessionId, wsSession);
        simpSessionToStreamSession.put(simpSessionId, streamSessionId);

        // Register sender for broadcasting
        if (broadcastService instanceof dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster localBroadcaster) {
            localBroadcaster.registerSender(streamSessionId, wsSession);
        }

        // Start heartbeat with async validation
        startHeartbeat(streamSessionId, wsSession);

        log.info("WebSocket session connected: simpSession={}, streamSession={}, user={}",
                simpSessionId, streamSessionId, userId);

        // Send connection acknowledgment
        StreamMessage connectedMsg = StreamMessage.connected(streamSessionId);
        wsSession.send(connectedMsg);
    }

    /**
     * Handle WebSocket disconnection events.
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String simpSessionId = headers.getSessionId();

        String streamSessionId = simpSessionToStreamSession.remove(simpSessionId);
        if (streamSessionId == null) {
            return;
        }

        log.info("WebSocket session disconnected: simpSession={}, streamSession={}",
                simpSessionId, streamSessionId);

        // Cancel heartbeat
        cancelHeartbeat(streamSessionId);

        // Start grace period (session might reconnect)
        sessionManager.markDisconnected(streamSessionId);

        // Remove from active sessions and close
        WebSocketStreamSession wsSession = activeSessions.remove(streamSessionId);
        if (wsSession != null) {
            wsSession.close();
        }

        // Unregister sender from broadcaster
        if (broadcastService instanceof dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster localBroadcaster) {
            localBroadcaster.unregisterSender(streamSessionId);
        }
    }

    /**
     * Handle subscription update requests via WebSocket.
     *
     * @param request the subscription request
     * @param headerAccessor message headers
     * @return subscription response
     */
    @MessageMapping("/stream/subscribe")
    @SendToUser("/queue/stream/subscriptions")
    public SubscriptionResponse handleSubscribe(
            @Payload SubscriptionRequest request,
            SimpMessageHeaderAccessor headerAccessor) {

        String simpSessionId = headerAccessor.getSessionId();
        String streamSessionId = simpSessionToStreamSession.get(simpSessionId);

        if (streamSessionId == null) {
            return SubscriptionResponse.builder()
                    .success(false)
                    .failed(List.of(SubscriptionResponse.FailedSubscription.builder()
                            .resource("*")
                            .reason("Session not found")
                            .build()))
                    .build();
        }

        WebSocketStreamSession wsSession = activeSessions.get(streamSessionId);
        if (wsSession == null) {
            return SubscriptionResponse.builder()
                    .success(false)
                    .failed(List.of(SubscriptionResponse.FailedSubscription.builder()
                            .resource("*")
                            .reason("Session not active")
                            .build()))
                    .build();
        }

        String userId = wsSession.getUserId();

        // Convert and validate subscriptions
        List<Subscription> newSubscriptions = convertToSubscriptions(request);
        List<SubscriptionResponse.FailedSubscription> failed = new ArrayList<>();
        List<Subscription> validSubscriptions = new ArrayList<>();

        for (Subscription sub : newSubscriptions) {
            SubscriptionKey key = sub.getKey();

            // Check if resource exists
            if (!collectorRegistry.hasCollector(key.getResource())) {
                failed.add(SubscriptionResponse.FailedSubscription.builder()
                        .resource(key.getResource())
                        .params(key.getParams())
                        .reason("Resource not found")
                        .build());
                continue;
            }

            // Check authorization
            StreamAuthorizationService.AuthorizationResult authResult =
                    authorizationService.checkAuthorization(userId, key);

            if (authResult.isDenied()) {
                failed.add(SubscriptionResponse.FailedSubscription.builder()
                        .resource(key.getResource())
                        .params(key.getParams())
                        .reason(authResult.getReason())
                        .build());
                continue;
            }

            validSubscriptions.add(sub);
        }

        // Update subscriptions
        subscriptionManager.updateSubscriptions(streamSessionId, validSubscriptions);

        // Build response
        List<SubscriptionResponse.SubscribedResource> subscribed = validSubscriptions.stream()
                .map(sub -> SubscriptionResponse.SubscribedResource.builder()
                        .resource(sub.getKey().getResource())
                        .params(sub.getKey().getParams())
                        .subscriptionKey(sub.getKey().toKeyString())
                        .intervalMs(sub.getIntervalMs())
                        .build())
                .toList();

        boolean success = failed.isEmpty() || properties.getSubscription().isPartialSuccess();

        log.debug("WebSocket subscription update: session={}, subscribed={}, failed={}",
                streamSessionId, subscribed.size(), failed.size());

        return SubscriptionResponse.builder()
                .success(success)
                .subscribed(subscribed)
                .failed(failed)
                .totalCount(validSubscriptions.size())
                .build();
    }

    /**
     * Handle unsubscribe all request.
     *
     * @param headerAccessor message headers
     */
    @MessageMapping("/stream/unsubscribe-all")
    public void handleUnsubscribeAll(SimpMessageHeaderAccessor headerAccessor) {
        String simpSessionId = headerAccessor.getSessionId();
        String streamSessionId = simpSessionToStreamSession.get(simpSessionId);

        if (streamSessionId != null) {
            subscriptionManager.clearSubscriptions(streamSessionId);
            log.debug("Cleared all subscriptions for WebSocket session: {}", streamSessionId);
        }
    }

    private void startHeartbeat(String sessionId, WebSocketStreamSession wsSession) {
        long intervalMs = properties.getSession().getHeartbeatInterval().toMillis();
        long validationTimeoutMs = properties.getSession().getValidationTimeout().toMillis();

        ScheduledFuture<?> heartbeatTask = streamScheduledExecutor.scheduleAtFixedRate(() -> {
            if (!wsSession.isActive()) {
                cancelHeartbeat(sessionId);
                return;
            }

            var streamSession = sessionManager.findSession(sessionId);
            if (streamSession.isEmpty()) {
                cancelHeartbeat(sessionId);
                return;
            }

            CompletableFuture.supplyAsync(
                            () -> sessionValidator.validate(streamSession.get()), sessionValidationExecutor)
                    .orTimeout(validationTimeoutMs, TimeUnit.MILLISECONDS)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.debug("Session validation timed out, skipping heartbeat: {}", sessionId);
                            return;
                        }
                        if (!result.valid()) {
                            wsSession.send(StreamMessage.sessionTerminated(result.reason()));
                            cleanupSession(sessionId);
                            return;
                        }
                        StreamMessage heartbeat = StreamMessage.heartbeat();
                        boolean sent = wsSession.send(heartbeat);
                        if (!sent) {
                            cancelHeartbeat(sessionId);
                        }
                    });
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);

        heartbeatTasks.put(sessionId, heartbeatTask);
    }

    private void cancelHeartbeat(String sessionId) {
        ScheduledFuture<?> task = heartbeatTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void cleanupSession(String sessionId) {
        WebSocketStreamSession wsSession = activeSessions.remove(sessionId);
        if (wsSession == null) {
            return; // Already cleaned up — idempotent guard
        }

        cancelHeartbeat(sessionId);
        wsSession.close();

        if (broadcastService instanceof dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster localBroadcaster) {
            localBroadcaster.unregisterSender(sessionId);
        }
        subscriptionManager.clearSubscriptions(sessionId);
        sessionManager.terminateSession(sessionId);

        log.info("Session cleaned up: {}", sessionId);
    }

    private List<Subscription> convertToSubscriptions(SubscriptionRequest request) {
        Duration defaultInterval = properties.getScheduler().getDefaultInterval();
        Duration minInterval = properties.getScheduler().getMinInterval();

        return request.getSubscriptions().stream()
                .map(item -> {
                    // Get interval from DataCollector (server-side only for security)
                    Duration interval = collectorRegistry.findCollector(item.getResource())
                            .map(collector -> {
                                long collectorInterval = collector.getDefaultIntervalMs();
                                long collectorMinInterval = collector.getMinIntervalMs();
                                // Use the larger of collector min and global min for security
                                long effectiveMin = Math.max(collectorMinInterval, minInterval.toMillis());
                                return Duration.ofMillis(Math.max(collectorInterval, effectiveMin));
                            })
                            .orElse(defaultInterval);

                    SubscriptionKey key = SubscriptionKey.of(item.getResource(), item.getParams());
                    return Subscription.of(key, interval);
                })
                .toList();
    }

    /**
     * Get the count of active WebSocket sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Get a WebSocket session by stream session ID.
     *
     * @param streamSessionId the stream session ID
     * @return the session, or null if not found
     */
    public WebSocketStreamSession getSession(String streamSessionId) {
        return activeSessions.get(streamSessionId);
    }
}
