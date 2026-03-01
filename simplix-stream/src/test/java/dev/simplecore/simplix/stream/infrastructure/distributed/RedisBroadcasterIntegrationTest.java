package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.autoconfigure.SimpliXStreamDistributedConfiguration;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for RedisBroadcaster using real Redis.
 * <p>
 * These tests verify the full Pub/Sub round-trip: publish on one instance,
 * receive on another, resolve local subscribers, and deliver to local sessions.
 * <p>
 * Tests are automatically skipped when Redis is not available (localhost:6379).
 */
@DisplayName("RedisBroadcaster Integration (Redis)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisBroadcasterIntegrationTest {

    private static final String INSTANCE_A = "integration-instance-a";
    private static final String INSTANCE_B = "integration-instance-b";
    private static final String CHANNEL_PREFIX = "test:stream:data:";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static ObjectMapper objectMapper;
    private static boolean redisAvailable;

    @BeforeAll
    static void setupRedis() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 6379);
            connectionFactory = new LettuceConnectionFactory(config);
            connectionFactory.afterPropertiesSet();

            redisTemplate = new StringRedisTemplate(connectionFactory);
            redisTemplate.afterPropertiesSet();

            // Test connectivity
            redisTemplate.opsForValue().get("ping-test");
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
        }
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            try {
                connectionFactory.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    @BeforeEach
    void checkRedis() {
        assumeTrue(redisAvailable, "Redis is not available on localhost:6379 - skipping integration test");
    }

    private StreamProperties createProperties() {
        StreamProperties props = new StreamProperties();
        StreamProperties.DistributedConfig distributed = new StreamProperties.DistributedConfig();
        StreamProperties.PubSubConfig pubsub = new StreamProperties.PubSubConfig();
        pubsub.setChannelPrefix(CHANNEL_PREFIX);
        distributed.setPubsub(pubsub);
        props.setDistributed(distributed);
        return props;
    }

    @Test
    @Order(1)
    @DisplayName("cross-instance broadcast should deliver to receiver's local subscribers")
    void crossInstanceBroadcastShouldDeliverToReceiverLocalSubscribers() throws Exception {
        StreamProperties props = createProperties();
        SubscriptionKey key = SubscriptionKey.of("controller-events", Map.of("id", "c1"));

        // --- Instance B setup (receiver) ---
        SubscriberLookup lookupB = k ->
                k.toKeyString().equals(key.toKeyString()) ? Set.of("session-b1") : Set.of();

        RedisBroadcaster broadcasterB = new RedisBroadcaster(
                redisTemplate, objectMapper, props, INSTANCE_B, List.of(lookupB));
        broadcasterB.initialize();

        // Track delivered messages
        CopyOnWriteArrayList<StreamMessage> deliveredMessages = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        MessageSender senderB1 = new MessageSender() {
            @Override
            public boolean send(StreamMessage message) {
                deliveredMessages.add(message);
                latch.countDown();
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void close() {
            }
        };
        broadcasterB.registerSender("session-b1", senderB1);

        // --- Redis Pub/Sub listener for Instance B ---
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListenerAdapter listener = new MessageListenerAdapter(
                new SimpliXStreamDistributedConfiguration.BroadcastMessageHandler(
                        broadcasterB, objectMapper));
        listener.setDefaultListenerMethod("handleMessage");
        listener.afterPropertiesSet();

        container.addMessageListener(listener, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.afterPropertiesSet();
        container.start();

        // Wait for container to be ready
        Thread.sleep(500);

        // --- Instance A broadcasts ---
        RedisBroadcaster broadcasterA = new RedisBroadcaster(
                redisTemplate, objectMapper, props, INSTANCE_A, List.of());
        broadcasterA.initialize();

        StreamMessage message = StreamMessage.data(key, Map.of("event", "door_open"));
        broadcasterA.broadcast(key, message, Set.of("session-a1"));

        // --- Verify Instance B received ---
        boolean received = latch.await(5, TimeUnit.SECONDS);

        // Cleanup
        container.stop();
        container.destroy();
        broadcasterA.shutdown();
        broadcasterB.shutdown();

        assertTrue(received, "Instance B should have received the broadcast message");
        assertEquals(1, deliveredMessages.size());
        assertEquals("controller-events", deliveredMessages.get(0).getResource());
    }

    @Test
    @Order(2)
    @DisplayName("source instance should deliver locally once, not duplicate via Pub/Sub")
    void sourceInstanceShouldDeliverLocallyOnceNotDuplicateViaPubSub() throws Exception {
        StreamProperties props = createProperties();
        SubscriptionKey key = SubscriptionKey.of("self-test", Map.of("id", "st1"));

        // Instance A is both sender and listener
        SubscriberLookup lookupA = k -> Set.of("session-a1");
        RedisBroadcaster broadcasterA = new RedisBroadcaster(
                redisTemplate, objectMapper, props, INSTANCE_A, List.of(lookupA));
        broadcasterA.initialize();

        CopyOnWriteArrayList<StreamMessage> delivered = new CopyOnWriteArrayList<>();
        MessageSender senderA1 = new MessageSender() {
            @Override
            public boolean send(StreamMessage message) {
                delivered.add(message);
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void close() {
            }
        };
        broadcasterA.registerSender("session-a1", senderA1);

        // Listen on Pub/Sub
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListenerAdapter listener = new MessageListenerAdapter(
                new SimpliXStreamDistributedConfiguration.BroadcastMessageHandler(
                        broadcasterA, objectMapper));
        listener.setDefaultListenerMethod("handleMessage");
        listener.afterPropertiesSet();
        container.addMessageListener(listener, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.afterPropertiesSet();
        container.start();
        Thread.sleep(500);

        // Broadcast from same instance
        StreamMessage message = StreamMessage.data(key, "self-data");
        broadcasterA.broadcast(key, message, Set.of("session-a1"));

        // Wait for Pub/Sub round-trip to verify no duplicate delivery
        Thread.sleep(1000);

        // Cleanup
        container.stop();
        container.destroy();
        broadcasterA.shutdown();

        // Exactly 1 delivery: from broadcast() local delivery.
        // Pub/Sub self-filtering prevents duplicate delivery via handleBroadcastMessage().
        assertEquals(1, delivered.size(),
                "Source instance should receive exactly once (local delivery, not Pub/Sub)");
        assertEquals("self-test", delivered.get(0).getResource());
    }

    @Test
    @Order(3)
    @DisplayName("multiple receivers should each resolve their own subscribers")
    void multipleReceiversShouldEachResolveTheirOwnSubscribers() throws Exception {
        StreamProperties props = createProperties();
        SubscriptionKey key = SubscriptionKey.of("multi-test", Map.of("id", "m1"));
        String instanceC = "integration-instance-c";

        // --- Instance B ---
        CountDownLatch latchB = new CountDownLatch(1);
        CopyOnWriteArrayList<String> deliveredB = new CopyOnWriteArrayList<>();
        SubscriberLookup lookupB = k ->
                k.toKeyString().equals(key.toKeyString()) ? Set.of("session-b1") : Set.of();
        RedisBroadcaster broadcasterB = new RedisBroadcaster(
                redisTemplate, objectMapper, props, INSTANCE_B, List.of(lookupB));
        broadcasterB.initialize();
        broadcasterB.registerSender("session-b1", trackingSender("b1", deliveredB, latchB));

        // --- Instance C ---
        CountDownLatch latchC = new CountDownLatch(1);
        CopyOnWriteArrayList<String> deliveredC = new CopyOnWriteArrayList<>();
        SubscriberLookup lookupC = k ->
                k.toKeyString().equals(key.toKeyString()) ? Set.of("session-c1") : Set.of();
        RedisBroadcaster broadcasterC = new RedisBroadcaster(
                redisTemplate, objectMapper, props, instanceC, List.of(lookupC));
        broadcasterC.initialize();
        broadcasterC.registerSender("session-c1", trackingSender("c1", deliveredC, latchC));

        // --- Pub/Sub containers ---
        RedisMessageListenerContainer containerB = createListenerContainer(broadcasterB);
        RedisMessageListenerContainer containerC = createListenerContainer(broadcasterC);
        Thread.sleep(500);

        // --- Instance A broadcasts ---
        RedisBroadcaster broadcasterA = new RedisBroadcaster(
                redisTemplate, objectMapper, props, INSTANCE_A, List.of());
        broadcasterA.initialize();
        StreamMessage message = StreamMessage.data(key, "multi-data");
        broadcasterA.broadcast(key, message, Set.of("session-a1"));

        // --- Verify both received ---
        boolean bReceived = latchB.await(5, TimeUnit.SECONDS);
        boolean cReceived = latchC.await(5, TimeUnit.SECONDS);

        // Cleanup
        containerB.stop();
        containerB.destroy();
        containerC.stop();
        containerC.destroy();
        broadcasterA.shutdown();
        broadcasterB.shutdown();
        broadcasterC.shutdown();

        assertTrue(bReceived, "Instance B should have received the broadcast");
        assertTrue(cReceived, "Instance C should have received the broadcast");
        assertEquals(List.of("b1"), deliveredB);
        assertEquals(List.of("c1"), deliveredC);
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private MessageSender trackingSender(String label, CopyOnWriteArrayList<String> tracker, CountDownLatch latch) {
        return new MessageSender() {
            @Override
            public boolean send(StreamMessage message) {
                tracker.add(label);
                latch.countDown();
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void close() {
            }
        };
    }

    private RedisMessageListenerContainer createListenerContainer(RedisBroadcaster broadcaster) throws Exception {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListenerAdapter listener = new MessageListenerAdapter(
                new SimpliXStreamDistributedConfiguration.BroadcastMessageHandler(
                        broadcaster, objectMapper));
        listener.setDefaultListenerMethod("handleMessage");
        listener.afterPropertiesSet();
        container.addMessageListener(listener, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.afterPropertiesSet();
        container.start();
        return container;
    }
}
