package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.eventsource.EventStreamHandler;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.Subscription;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * REST controller for SSE streaming connections.
 * <p>
 * Provides endpoints for establishing SSE connections and managing subscriptions.
 */
@Slf4j
@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class SseStreamController {

    private final SessionManager sessionManager;
    private final SubscriptionManager subscriptionManager;
    private final SimpliXStreamDataCollectorRegistry collectorRegistry;
    private final LocalBroadcaster broadcaster;
    private final StreamAuthorizationService authorizationService;
    private final StreamProperties properties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService streamScheduledExecutor;
    private final SessionValidator sessionValidator;
    private final ExecutorService sessionValidationExecutor;

    /** Optional — only present when simplix.stream.event-source.enabled=true. */
    @Autowired(required = false)
    private EventStreamHandler eventStreamHandler;

    private final Map<String, SseStreamSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    /**
     * Establish an SSE connection.
     * <p>
     * Creates a new streaming session and returns an SSE emitter for real-time data.
     *
     * @return SSE emitter for the connection
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam(required = false) Map<String, String> connectParams,
            HttpServletRequest request) {
        String userId = extractUserId();

        // Create SSE emitter with configured timeout
        long timeoutMs = properties.getSession().getTimeout().toMillis();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // Create stream session via manager
        StreamSession streamSession = sessionManager.createSession(userId, TransportType.SSE);
        String sessionId = streamSession.getId();

        // Store connect query params as session metadata
        if (connectParams != null) {
            connectParams.forEach(streamSession::addMetadata);
        }

        // Store bearer token as transient metadata (not persisted)
        extractAndStoreBearerToken(request, streamSession);

        // Create SSE session wrapper
        SseStreamSession sseSession = new SseStreamSession(streamSession, emitter, objectMapper);
        activeSessions.put(sessionId, sseSession);

        // Register sender for broadcasting
        broadcaster.registerSender(sessionId, sseSession);

        // Setup cleanup on connection close
        setupCleanupCallbacks(emitter, sessionId);

        // Start heartbeat
        startHeartbeat(sessionId, sseSession);

        // Send initial connection message
        sendConnectionMessage(sseSession, sessionId);

        log.info("SSE connection established: sessionId={}, userId={}, connectParams={}",
                sessionId, userId, connectParams != null ? connectParams.keySet() : Set.of());
        return emitter;
    }

    /**
     * Update subscriptions for a session.
     * <p>
     * Replaces current subscriptions with the provided list.
     * Calculates diff and only adds/removes changed subscriptions.
     *
     * @param sessionId the session ID
     * @param request   the subscription request
     * @return subscription response with results
     */
    @PutMapping("/sessions/{sessionId}/subscriptions")
    public ResponseEntity<SubscriptionResponse> updateSubscriptions(
            @PathVariable String sessionId,
            @Valid @RequestBody SubscriptionRequest request) {

        SseStreamSession sseSession = activeSessions.get(sessionId);
        if (sseSession == null) {
            throw new SessionNotFoundException(sessionId);
        }

        // Validate user authorization
        validateSessionOwnership(sseSession);

        // Convert request to subscriptions (merge session metadata into params)
        List<Subscription> newSubscriptions = convertToSubscriptions(request, sseSession.getSession());

        // Validate resources exist and check authorization
        String userId = sseSession.getUserId();
        List<SubscriptionResponse.FailedSubscription> failed = new ArrayList<>();
        List<Subscription> validSubscriptions = new ArrayList<>();

        for (Subscription sub : newSubscriptions) {
            SubscriptionKey key = sub.getKey();

            // Check if resource exists (data collector or event source)
            boolean resourceExists = collectorRegistry.hasCollector(key.getResource())
                    || (eventStreamHandler != null && eventStreamHandler.isEventBasedResource(key.getResource()));
            if (!resourceExists) {
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

        // Update subscriptions via manager (handles data collector scheduling)
        subscriptionManager.updateSubscriptions(sessionId, validSubscriptions);

        // Register event-source subscribers for push-based resources
        if (eventStreamHandler != null) {
            for (Subscription sub : validSubscriptions) {
                if (eventStreamHandler.isEventBasedResource(sub.getKey().getResource())) {
                    eventStreamHandler.addSubscriber(sub.getKey(), sessionId);
                }
            }
        }

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

        SubscriptionResponse response = SubscriptionResponse.builder()
                .success(success)
                .subscribed(subscribed)
                .failed(failed)
                .totalCount(validSubscriptions.size())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get current subscriptions for a session.
     *
     * @param sessionId the session ID
     * @return list of current subscriptions
     */
    @GetMapping("/sessions/{sessionId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponse.SubscribedResource>> getSubscriptions(
            @PathVariable String sessionId) {

        SseStreamSession sseSession = activeSessions.get(sessionId);
        if (sseSession == null) {
            throw new SessionNotFoundException(sessionId);
        }

        validateSessionOwnership(sseSession);

        StreamSession session = sseSession.getSession();
        List<SubscriptionResponse.SubscribedResource> resources = session.getSubscriptions().stream()
                .map(key -> SubscriptionResponse.SubscribedResource.builder()
                        .resource(key.getResource())
                        .params(key.getParams())
                        .subscriptionKey(key.toKeyString())
                        .intervalMs(properties.getScheduler().getDefaultInterval().toMillis())
                        .build())
                .toList();

        return ResponseEntity.ok(resources);
    }

    /**
     * Disconnect a session.
     *
     * @param sessionId the session ID
     * @return no content response
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> disconnect(@PathVariable String sessionId) {
        SseStreamSession sseSession = activeSessions.get(sessionId);
        if (sseSession == null) {
            throw new SessionNotFoundException(sessionId);
        }

        validateSessionOwnership(sseSession);

        cleanupSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reconnect to an existing session.
     * <p>
     * Supports both same-server reconnection (within grace period) and
     * cross-server reconnection (session restore from database).
     *
     * @param sessionId the session ID to reconnect to
     * @return SSE emitter for the reconnected session
     */
    @GetMapping(value = "/reconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reconnect(@RequestParam String sessionId, HttpServletRequest request) {
        String userId = extractUserId();

        // Try to restore/reconnect the session
        var restoredSession = sessionManager.restoreSession(sessionId, userId);

        if (restoredSession.isEmpty()) {
            // Fallback: try same-server reconnection
            if (sessionManager.reconnect(sessionId)) {
                restoredSession = sessionManager.findSession(sessionId);
            }
        }

        if (restoredSession.isEmpty()) {
            throw new SessionNotFoundException(sessionId);
        }

        StreamSession streamSession = restoredSession.get();

        // Store bearer token as transient metadata (not persisted)
        extractAndStoreBearerToken(request, streamSession);

        // Create SSE emitter
        long timeoutMs = properties.getSession().getTimeout().toMillis();
        SseEmitter emitter = new SseEmitter(timeoutMs);

        // Create SSE session wrapper
        SseStreamSession sseSession = new SseStreamSession(streamSession, emitter, objectMapper);
        activeSessions.put(sessionId, sseSession);

        // Register sender for broadcasting
        broadcaster.registerSender(sessionId, sseSession);

        // Setup cleanup on connection close
        setupCleanupCallbacks(emitter, sessionId);

        // Start heartbeat
        startHeartbeat(sessionId, sseSession);

        // Send reconnection message with restored subscriptions
        sendReconnectionMessage(sseSession, streamSession);

        log.info("SSE reconnection established: sessionId={}, userId={}, subscriptions={}",
                sessionId, userId, streamSession.getSubscriptionCount());

        return emitter;
    }

    private void sendReconnectionMessage(SseStreamSession sseSession, StreamSession streamSession) {
        var subscriptionKeys = streamSession.getSubscriptions().stream()
                .map(key -> key.toKeyString())
                .toList();

        StreamMessage message = StreamMessage.reconnected(streamSession.getId(), subscriptionKeys);
        sseSession.send(message);
    }

    private void setupCleanupCallbacks(SseEmitter emitter, String sessionId) {
        Runnable cleanup = () -> {
            log.debug("SSE connection closed, starting grace period for session: {}", sessionId);
            SseStreamSession sseSession = activeSessions.get(sessionId);
            if (sseSession != null) {
                sessionManager.markDisconnected(sessionId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
    }

    private void startHeartbeat(String sessionId, SseStreamSession sseSession) {
        long intervalMs = properties.getSession().getHeartbeatInterval().toMillis();

        ScheduledFuture<?> heartbeatTask = streamScheduledExecutor.scheduleAtFixedRate(() -> {
            if (!sseSession.isActive()) {
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
                    .orTimeout(2, TimeUnit.SECONDS)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            sseSession.send(StreamMessage.sessionTerminated("Session validation timeout"));
                            cleanupSession(sessionId);
                            return;
                        }
                        if (!result.valid()) {
                            sseSession.send(StreamMessage.sessionTerminated(result.reason()));
                            cleanupSession(sessionId);
                            return;
                        }
                        StreamMessage heartbeat = StreamMessage.heartbeat();
                        boolean sent = sseSession.send(heartbeat);
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

    private void sendConnectionMessage(SseStreamSession sseSession, String sessionId) {
        StreamMessage message = StreamMessage.connected(sessionId);
        sseSession.send(message);
    }

    private void cleanupSession(String sessionId) {
        SseStreamSession sseSession = activeSessions.remove(sessionId);
        if (sseSession == null) {
            return; // Already cleaned up — idempotent guard
        }

        cancelHeartbeat(sessionId);
        sseSession.close();

        broadcaster.unregisterSender(sessionId);
        subscriptionManager.clearSubscriptions(sessionId);
        sessionManager.terminateSession(sessionId);
        if (eventStreamHandler != null) {
            eventStreamHandler.removeSubscriberFromAll(sessionId);
        }

        log.info("Session cleaned up: {}", sessionId);
    }

    private List<Subscription> convertToSubscriptions(SubscriptionRequest request, StreamSession session) {
        Duration defaultInterval = properties.getScheduler().getDefaultInterval();
        Duration minInterval = properties.getScheduler().getMinInterval();
        Map<String, Object> sessionMetadata = session.getMetadata();

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

                    // Merge session metadata (connect params) with subscription params.
                    // Subscription-level params take precedence over session metadata.
                    Map<String, Object> mergedParams = new LinkedHashMap<>(sessionMetadata);
                    if (item.getParams() != null) {
                        mergedParams.putAll(item.getParams());
                    }

                    SubscriptionKey key = SubscriptionKey.of(item.getResource(), mergedParams);
                    return Subscription.of(key, interval);
                })
                .toList();
    }

    private void extractAndStoreBearerToken(HttpServletRequest request, StreamSession streamSession) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            streamSession.addTransientMetadata("bearerToken", authHeader.substring(7));
        }
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    private void validateSessionOwnership(SseStreamSession sseSession) {
        String currentUserId = extractUserId();
        String sessionUserId = sseSession.getUserId();

        // Allow if session has no user (anonymous) or user matches
        if (sessionUserId != null && !sessionUserId.equals(currentUserId)) {
            throw new SecurityException("Session does not belong to current user");
        }
    }

    /**
     * Get the count of active sessions.
     *
     * @return active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
