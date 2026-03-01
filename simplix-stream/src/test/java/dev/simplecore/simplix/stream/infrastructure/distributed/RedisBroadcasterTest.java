package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisBroadcaster.
 * <p>
 * Tests the cross-instance broadcast delivery logic, subscriber lookup resolution,
 * and instance ID filtering without requiring a running Redis server.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisBroadcaster")
class RedisBroadcasterTest {

    private static final String INSTANCE_A = "instance-a";
    private static final String INSTANCE_B = "instance-b";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageSender senderA1;

    @Mock
    private MessageSender senderA2;

    @Mock
    private MessageSender senderB1;

    @Mock
    private MessageSender senderB2;

    private ObjectMapper objectMapper;
    private StreamProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        properties = new StreamProperties();
        properties.setDistributed(new StreamProperties.DistributedConfig());
    }

    private RedisBroadcaster createBroadcaster(String instanceId, List<SubscriberLookup> lookups) {
        return new RedisBroadcaster(redisTemplate, objectMapper, properties, instanceId, lookups);
    }

    // ============================================================
    // handleBroadcastMessage — Source Instance Filtering
    // ============================================================

    @Nested
    @DisplayName("handleBroadcastMessage() - source instance filtering")
    class SourceInstanceFiltering {

        @Test
        @DisplayName("should skip message from same instance")
        void shouldSkipMessageFromSameInstance() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            broadcaster.registerSender("session-1", senderA1);

            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));
            StreamMessage message = StreamMessage.data(key, Map.of("status", "ok"));

            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-1"), message);

            broadcaster.handleBroadcastMessage(broadcastMsg);

            verify(senderA1, never()).send(any());
        }

        @Test
        @DisplayName("should process message from different instance")
        void shouldProcessMessageFromDifferentInstance() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_B, List.of());
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            broadcaster.registerSender("session-b1", senderB1);

            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));
            StreamMessage message = StreamMessage.data(key, Map.of("status", "ok"));

            // Message from instance A, contains session-b1 (which happens to be local on B)
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-b1"), message);

            broadcaster.handleBroadcastMessage(broadcastMsg);

            verify(senderB1).send(message);
        }
    }

    // ============================================================
    // handleBroadcastMessage — Subscriber Lookup Resolution
    // ============================================================

    @Nested
    @DisplayName("handleBroadcastMessage() - subscriber lookup resolution")
    class SubscriberLookupResolution {

        @Test
        @DisplayName("should resolve local subscribers via SubscriberLookup instead of source session IDs")
        void shouldResolveLocalSubscribersViaLookup() {
            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));

            // Instance B has local subscribers {session-b1, session-b2}
            SubscriberLookup eventLookup = k -> {
                if (k.toKeyString().equals(key.toKeyString())) {
                    return Set.of("session-b1", "session-b2");
                }
                return Set.of();
            };

            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(eventLookup));
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            when(senderB2.isActive()).thenReturn(true);
            when(senderB2.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);
            broadcasterB.registerSender("session-b2", senderB2);

            StreamMessage message = StreamMessage.data(key, Map.of("event", "door_open"));

            // Source instance A broadcasts with its OWN session IDs {session-a1}
            // Instance B should NOT use {session-a1} but resolve its own {session-b1, session-b2}
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-a1"), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            // session-b1 and session-b2 should receive the message
            verify(senderB1).send(message);
            verify(senderB2).send(message);
        }

        @Test
        @DisplayName("should not deliver to source instance's sessions that are not local")
        void shouldNotDeliverToRemoteSessions() {
            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));

            // Instance B has ONE local subscriber
            SubscriberLookup lookup = k -> Set.of("session-b1");

            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(lookup));
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);

            StreamMessage message = StreamMessage.data(key, Map.of("event", "access"));

            // Source has {session-a1, session-a2} — these don't exist on instance B
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-a1", "session-a2"), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            // Only session-b1 receives, NOT session-a1/session-a2
            verify(senderB1).send(message);
        }

        @Test
        @DisplayName("should aggregate subscribers from multiple lookups")
        void shouldAggregateFromMultipleLookups() {
            SubscriptionKey key = SubscriptionKey.of("controller-status", Map.of("id", "c1"));

            // Event-based lookup finds session-b1
            SubscriberLookup eventLookup = k -> {
                if (k.toKeyString().equals(key.toKeyString())) return Set.of("session-b1");
                return Set.of();
            };
            // Scheduler-based lookup finds session-b2
            SubscriberLookup schedulerLookup = k -> {
                if (k.toKeyString().equals(key.toKeyString())) return Set.of("session-b2");
                return Set.of();
            };

            RedisBroadcaster broadcasterB = createBroadcaster(
                    INSTANCE_B, List.of(eventLookup, schedulerLookup));
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            when(senderB2.isActive()).thenReturn(true);
            when(senderB2.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);
            broadcasterB.registerSender("session-b2", senderB2);

            StreamMessage message = StreamMessage.data(key, Map.of("data", "snapshot"));

            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-a1"), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            verify(senderB1).send(message);
            verify(senderB2).send(message);
        }

        @Test
        @DisplayName("should deliver nothing when no local subscribers exist")
        void shouldDeliverNothingWhenNoLocalSubscribers() {
            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));

            // Lookup returns empty — no local subscribers for this key
            SubscriberLookup emptyLookup = k -> Set.of();

            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(emptyLookup));

            StreamMessage message = StreamMessage.data(key, Map.of("event", "test"));

            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-a1"), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            // No senders registered, no delivery attempted
            verifyNoInteractions(senderB1, senderB2);
        }

        @Test
        @DisplayName("should fall back to source session IDs when no lookups configured")
        void shouldFallBackToSourceSessionIdsWithNoLookups() {
            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of());
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);

            SubscriptionKey key = SubscriptionKey.of("resource", Map.of("x", "1"));
            StreamMessage message = StreamMessage.data(key, "payload");

            // Source's session IDs include session-b1 (which happens to be local)
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of("session-b1"), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            verify(senderB1).send(message);
        }

        @Test
        @DisplayName("should skip inactive senders among resolved subscribers")
        void shouldSkipInactiveSendersAmongResolvedSubscribers() {
            SubscriptionKey key = SubscriptionKey.of("events", Map.of("id", "1"));
            SubscriberLookup lookup = k -> Set.of("active-session", "inactive-session");

            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(lookup));

            MessageSender activeSender = mock(MessageSender.class);
            MessageSender inactiveSender = mock(MessageSender.class);
            when(activeSender.isActive()).thenReturn(true);
            when(activeSender.send(any())).thenReturn(true);
            when(inactiveSender.isActive()).thenReturn(false);

            broadcasterB.registerSender("active-session", activeSender);
            broadcasterB.registerSender("inactive-session", inactiveSender);

            StreamMessage message = StreamMessage.data(key, "data");
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A, key.toKeyString(), Set.of(), message);

            broadcasterB.handleBroadcastMessage(broadcastMsg);

            verify(activeSender).send(message);
            verify(inactiveSender, never()).send(any());
        }
    }

    // ============================================================
    // handleDirectMessage
    // ============================================================

    @Nested
    @DisplayName("handleDirectMessage()")
    class HandleDirectMessage {

        @Test
        @DisplayName("should skip direct message from same instance")
        void shouldSkipDirectMessageFromSameInstance() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            broadcaster.registerSender("session-1", senderA1);

            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("res", Map.of()), "data");

            RedisBroadcaster.DirectMessage directMsg = new RedisBroadcaster.DirectMessage(
                    INSTANCE_A, "session-1", message);

            broadcaster.handleDirectMessage(directMsg);

            verify(senderA1, never()).send(any());
        }

        @Test
        @DisplayName("should deliver direct message from different instance")
        void shouldDeliverDirectMessageFromDifferentInstance() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_B, List.of());
            when(senderB1.isActive()).thenReturn(true);
            broadcaster.registerSender("session-b1", senderB1);

            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("res", Map.of()), "data");

            RedisBroadcaster.DirectMessage directMsg = new RedisBroadcaster.DirectMessage(
                    INSTANCE_A, "session-b1", message);

            broadcaster.handleDirectMessage(directMsg);

            verify(senderB1).send(message);
        }

        @Test
        @DisplayName("should ignore direct message for unknown session")
        void shouldIgnoreDirectMessageForUnknownSession() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_B, List.of());

            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("res", Map.of()), "data");

            RedisBroadcaster.DirectMessage directMsg = new RedisBroadcaster.DirectMessage(
                    INSTANCE_A, "unknown-session", message);

            assertDoesNotThrow(() -> broadcaster.handleDirectMessage(directMsg));
        }
    }

    // ============================================================
    // broadcast() — Redis Pub/Sub Publishing
    // ============================================================

    @Nested
    @DisplayName("broadcast()")
    class BroadcastMethod {

        @Test
        @DisplayName("should deliver locally AND publish to Redis")
        void shouldDeliverLocallyAndPublishToRedis() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            when(senderA1.isActive()).thenReturn(true);
            when(senderA1.send(any())).thenReturn(true);
            broadcaster.registerSender("session-a1", senderA1);

            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));
            StreamMessage message = StreamMessage.data(key, Map.of("status", "online"));

            broadcaster.broadcast(key, message, Set.of("session-a1"));

            // Local delivery
            verify(senderA1).send(message);
            // Redis Pub/Sub publish
            verify(redisTemplate).convertAndSend(
                    eq("stream:data:" + key.toKeyString()),
                    anyString());
        }

        @Test
        @DisplayName("should publish to Redis even when no local sender is registered")
        void shouldPublishToRedisEvenWhenNoLocalSender() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());

            SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));
            StreamMessage message = StreamMessage.data(key, Map.of("status", "online"));

            broadcaster.broadcast(key, message, Set.of("session-a1"));

            verify(redisTemplate).convertAndSend(
                    eq("stream:data:" + key.toKeyString()),
                    anyString());
        }

        @Test
        @DisplayName("should still deliver locally when Redis publish fails")
        void shouldStillDeliverLocallyWhenRedisPublishFails() {
            doThrow(new RuntimeException("Redis down")).when(redisTemplate)
                    .convertAndSend(anyString(), anyString());

            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            when(senderA1.isActive()).thenReturn(true);
            when(senderA1.send(any())).thenReturn(true);
            broadcaster.registerSender("session-a1", senderA1);

            SubscriptionKey key = SubscriptionKey.of("resource", Map.of());
            StreamMessage message = StreamMessage.data(key, "data");

            assertDoesNotThrow(() -> broadcaster.broadcast(key, message, Set.of("session-a1")));

            // Local delivery should have succeeded before Redis failure
            verify(senderA1).send(message);
        }
    }

    // ============================================================
    // sendToSession() — Local-first with Redis Fallback
    // ============================================================

    @Nested
    @DisplayName("sendToSession()")
    class SendToSessionMethod {

        @Test
        @DisplayName("should deliver locally when sender exists")
        void shouldDeliverLocallyWhenSenderExists() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            when(senderA1.isActive()).thenReturn(true);
            when(senderA1.send(any())).thenReturn(true);
            broadcaster.registerSender("session-a1", senderA1);

            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("res", Map.of()), "data");

            boolean result = broadcaster.sendToSession("session-a1", message);

            assertTrue(result);
            verify(senderA1).send(message);
            verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        }

        @Test
        @DisplayName("should fall back to Redis when session is not local")
        void shouldFallBackToRedisWhenSessionIsNotLocal() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());

            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("res", Map.of()), "data");

            boolean result = broadcaster.sendToSession("remote-session", message);

            assertTrue(result);
            verify(redisTemplate).convertAndSend(
                    eq("stream:data:direct:remote-session"),
                    anyString());
        }
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("initialize should set available when Redis is reachable")
        void initializeShouldSetAvailableWhenRedisReachable() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("test")).thenReturn(null);

            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            broadcaster.initialize();

            assertTrue(broadcaster.isAvailable());
        }

        @Test
        @DisplayName("initialize should set unavailable when Redis is not reachable")
        void initializeShouldSetUnavailableWhenRedisNotReachable() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Connection refused"));

            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            broadcaster.initialize();

            assertFalse(broadcaster.isAvailable());
        }

        @Test
        @DisplayName("shutdown should close all senders and clear registry")
        void shutdownShouldCloseAllSendersAndClearRegistry() {
            RedisBroadcaster broadcaster = createBroadcaster(INSTANCE_A, List.of());
            broadcaster.registerSender("s1", senderA1);
            broadcaster.registerSender("s2", senderA2);

            broadcaster.shutdown();

            assertFalse(broadcaster.isAvailable());
            assertEquals(0, broadcaster.getLocalSenderCount());
            verify(senderA1).close();
            verify(senderA2).close();
        }
    }

    // ============================================================
    // Cross-Instance Scenario (End-to-End Simulation)
    // ============================================================

    @Nested
    @DisplayName("cross-instance scenario simulation")
    class CrossInstanceScenario {

        @Test
        @DisplayName("full scenario: event consumed on A, subscribers on A and B both receive")
        void fullScenarioEventConsumedOnASubscribersOnBothReceive() {
            SubscriptionKey key = SubscriptionKey.of("controller-events",
                    Map.of("controllerId", "ctrl-001"));
            StreamMessage message = StreamMessage.data(key, Map.of("event", "door_forced_open"));

            // --- Instance A setup ---
            // Instance A has session-a1 subscribed
            SubscriberLookup lookupA = k ->
                    k.toKeyString().equals(key.toKeyString()) ? Set.of("session-a1") : Set.of();
            RedisBroadcaster broadcasterA = createBroadcaster(INSTANCE_A, List.of(lookupA));
            // Note: no stubs on senderA1 — Instance A skips its own broadcast message
            broadcasterA.registerSender("session-a1", senderA1);

            // --- Instance B setup ---
            // Instance B has session-b1, session-b2 subscribed
            SubscriberLookup lookupB = k ->
                    k.toKeyString().equals(key.toKeyString()) ? Set.of("session-b1", "session-b2") : Set.of();
            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(lookupB));
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            when(senderB2.isActive()).thenReturn(true);
            when(senderB2.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);
            broadcasterB.registerSender("session-b2", senderB2);

            // --- Simulate: Instance A consumes TransactionLog and broadcasts ---
            // Step 1: Instance A calls broadcast() → publishes to Redis
            // (In real Redis, this goes through Pub/Sub)
            // We simulate the BroadcastMessage that would be published:
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A,
                    key.toKeyString(),
                    Set.of("session-a1"),  // A's local subscribers
                    message
            );

            // Step 2: Instance A receives its own message → skips
            broadcasterA.handleBroadcastMessage(broadcastMsg);
            verify(senderA1, never()).send(any());  // skipped (same instance)

            // Step 3: Instance B receives the message → resolves own subscribers → delivers
            broadcasterB.handleBroadcastMessage(broadcastMsg);
            verify(senderB1).send(message);
            verify(senderB2).send(message);
        }

        @Test
        @DisplayName("scheduler leader on A, follower B subscribers receive via broadcast")
        void schedulerLeaderOnAFollowerBSubscribersReceiveViaBroadcast() {
            SubscriptionKey key = SubscriptionKey.of("controller-status",
                    Map.of("controllerId", "ctrl-002"));
            StreamMessage message = StreamMessage.data(key, Map.of("acr_mode", "card_only"));

            // Instance B (follower) has local subscribers for this scheduler key
            SubscriberLookup schedulerLookupB = k ->
                    k.toKeyString().equals(key.toKeyString()) ? Set.of("session-b1") : Set.of();
            RedisBroadcaster broadcasterB = createBroadcaster(INSTANCE_B, List.of(schedulerLookupB));
            when(senderB1.isActive()).thenReturn(true);
            when(senderB1.send(any())).thenReturn(true);
            broadcasterB.registerSender("session-b1", senderB1);

            // Leader (Instance A) broadcasts collected data with its own subscribers
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    INSTANCE_A,
                    key.toKeyString(),
                    Set.of("session-a1", "session-a2"),  // Leader's sessions
                    message
            );

            // Instance B receives and uses its own lookup
            broadcasterB.handleBroadcastMessage(broadcastMsg);
            verify(senderB1).send(message);
        }
    }
}
