package dev.simplecore.simplix.messaging.integration;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.redis.RedisBrokerStrategy;
import dev.simplecore.simplix.messaging.broker.redis.RedisConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamPublisher;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import dev.simplecore.simplix.messaging.subscriber.IdempotentGuard;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis Streams-based messaging.
 *
 * <p>Requires a running Redis instance at localhost:6379.
 * Uses a dedicated key prefix to avoid polluting real data.
 * All test keys are cleaned up after each test.
 */
@Tag("integration")
@SuppressWarnings("unchecked")
@ExtendWith(RedisAvailableCondition.class)
@DisplayName("Redis Stream Integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisStreamIntegrationTest {

    private static final String KEY_PREFIX = "test:simplix:";
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    private static final int BATCH_SIZE = 10;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisBrokerStrategy broker;
    private RedisConsumerGroupManager groupManager;
    private RedisStreamPublisher publisher;
    private RedisStreamSubscriber subscriber;

    private final CopyOnWriteArrayList<String> createdStreamKeys = new CopyOnWriteArrayList<>();

    @BeforeAll
    void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // Verify Redis connectivity
        String pong = redisTemplate.getConnectionFactory().getConnection().ping();
        assertThat(pong).isEqualTo("PONG");
    }

    @AfterAll
    void tearDownRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        groupManager = new RedisConsumerGroupManager(redisTemplate, KEY_PREFIX);
        publisher = new RedisStreamPublisher(redisTemplate, KEY_PREFIX, 10_000L);
        subscriber = new RedisStreamSubscriber(redisTemplate, KEY_PREFIX, POLL_TIMEOUT, BATCH_SIZE);
        broker = new RedisBrokerStrategy(redisTemplate, KEY_PREFIX, groupManager, publisher, subscriber);
        broker.initialize();
    }

    @AfterEach
    void tearDown() {
        broker.shutdown();

        // Clean up all test stream keys
        for (String key : createdStreamKeys) {
            redisTemplate.delete(key);
        }
        createdStreamKeys.clear();

        // Also clean up any remaining keys with our prefix
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String trackKey(String channel) {
        createdStreamKeys.add(KEY_PREFIX + channel);
        return channel;
    }

    // ---------------------------------------------------------------
    // Publish Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Publish")
    class PublishTests {

        @Test
        @DisplayName("should publish message and return a valid PublishResult")
        void shouldPublishAndReturnResult() {
            String channel = trackKey("publish-test-1");
            byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-001")
                    .with(MessageHeaders.CONTENT_TYPE, "text/plain");

            PublishResult result = broker.send(channel, payload, headers);

            assertThat(result).isNotNull();
            assertThat(result.channel()).isEqualTo(channel);
            assertThat(result.recordId()).isNotNull().isNotEmpty();
            assertThat(result.timestamp()).isNotNull();

            // Verify stream exists in Redis
            Long size = redisTemplate.opsForStream().size(KEY_PREFIX + channel);
            assertThat(size).isEqualTo(1L);
        }

        @Test
        @DisplayName("should publish multiple messages to the same stream")
        void shouldPublishMultipleMessages() {
            String channel = trackKey("publish-test-multi");
            MessageHeaders headers = MessageHeaders.empty();

            for (int i = 0; i < 5; i++) {
                byte[] payload = ("message-" + i).getBytes(StandardCharsets.UTF_8);
                broker.send(channel, payload, headers.with(MessageHeaders.MESSAGE_ID, "msg-" + i));
            }

            Long size = redisTemplate.opsForStream().size(KEY_PREFIX + channel);
            assertThat(size).isEqualTo(5L);
        }

        @Test
        @DisplayName("should preserve custom headers in the stream record")
        void shouldPreserveCustomHeaders() {
            String channel = trackKey("publish-test-headers");
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-hdr")
                    .with(MessageHeaders.CORRELATION_ID, "corr-123")
                    .with("x-source", "integration-test");

            broker.send(channel, payload, headers);

            // Read back from Redis directly to verify fields
            var records = redisTemplate.opsForStream()
                    .read(org.springframework.data.redis.connection.stream.StreamOffset
                            .fromStart(KEY_PREFIX + channel));
            assertThat(records).hasSize(1);

            var fields = records.get(0).getValue();
            assertThat(fields).containsKey("payload");
            assertThat(fields).containsEntry(MessageHeaders.MESSAGE_ID, "msg-hdr");
            assertThat(fields).containsEntry(MessageHeaders.CORRELATION_ID, "corr-123");
            assertThat(fields).containsEntry("x-source", "integration-test");
        }
    }

    // ---------------------------------------------------------------
    // Subscribe Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should receive published message via consumer group subscription")
        void shouldReceiveMessage() throws InterruptedException {
            String channel = trackKey("subscribe-test-1");
            String group = "test-group-1";
            CountDownLatch latch = new CountDownLatch(1);
            CopyOnWriteArrayList<Message<byte[]>> received = new CopyOnWriteArrayList<>();

            // Set up consumer group first
            broker.ensureConsumerGroup(channel, group);

            // Subscribe
            Subscription subscription = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("consumer-1")
                    .listener((message, ack) -> {
                        received.add(message);
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            // Publish after subscribing
            byte[] payload = "hello subscriber".getBytes(StandardCharsets.UTF_8);
            broker.send(channel, payload, MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "sub-msg-001"));

            // Wait for delivery
            boolean delivered = latch.await(5, TimeUnit.SECONDS);

            assertThat(delivered).isTrue();
            assertThat(received).hasSize(1);

            Message<byte[]> msg = received.get(0);
            assertThat(msg.getChannel()).isEqualTo(channel);
            assertThat(new String(msg.getPayload(), StandardCharsets.UTF_8)).isEqualTo("hello subscriber");
            assertThat(msg.getHeaders().get(MessageHeaders.MESSAGE_ID)).hasValue("sub-msg-001");

            subscription.cancel();
        }

        @Test
        @DisplayName("should deliver messages to multiple consumers in round-robin within a group")
        void shouldDistributeAcrossConsumersInGroup() throws InterruptedException {
            String channel = trackKey("subscribe-test-rr");
            String group = "test-group-rr";
            int messageCount = 6;
            CountDownLatch latch = new CountDownLatch(messageCount);

            CopyOnWriteArrayList<String> consumer1Messages = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<String> consumer2Messages = new CopyOnWriteArrayList<>();

            broker.ensureConsumerGroup(channel, group);

            Subscription sub1 = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("consumer-a")
                    .listener((message, ack) -> {
                        consumer1Messages.add(message.getMessageId());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            Subscription sub2 = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("consumer-b")
                    .listener((message, ack) -> {
                        consumer2Messages.add(message.getMessageId());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            // Publish messages
            for (int i = 0; i < messageCount; i++) {
                byte[] payload = ("msg-" + i).getBytes(StandardCharsets.UTF_8);
                broker.send(channel, payload, MessageHeaders.empty()
                        .with(MessageHeaders.MESSAGE_ID, "rr-msg-" + i));
            }

            boolean allDelivered = latch.await(10, TimeUnit.SECONDS);

            assertThat(allDelivered).isTrue();

            int total = consumer1Messages.size() + consumer2Messages.size();
            assertThat(total).isEqualTo(messageCount);
            // Both consumers should have received at least one message
            assertThat(consumer1Messages).isNotEmpty();
            assertThat(consumer2Messages).isNotEmpty();

            sub1.cancel();
            sub2.cancel();
        }

        @Test
        @DisplayName("should deliver same message to different consumer groups (fan-out)")
        void shouldFanOutToMultipleGroups() throws InterruptedException {
            String channel = trackKey("subscribe-test-fanout");
            String groupA = "group-alpha";
            String groupB = "group-beta";
            CountDownLatch latch = new CountDownLatch(2);

            CopyOnWriteArrayList<String> groupAMessages = new CopyOnWriteArrayList<>();
            CopyOnWriteArrayList<String> groupBMessages = new CopyOnWriteArrayList<>();

            broker.ensureConsumerGroup(channel, groupA);
            broker.ensureConsumerGroup(channel, groupB);

            Subscription subA = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(groupA)
                    .consumerName("consumer-a1")
                    .listener((message, ack) -> {
                        groupAMessages.add(message.getMessageId());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            Subscription subB = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(groupB)
                    .consumerName("consumer-b1")
                    .listener((message, ack) -> {
                        groupBMessages.add(message.getMessageId());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            // Publish one message
            broker.send(channel, "fanout-data".getBytes(StandardCharsets.UTF_8),
                    MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "fanout-msg-1"));

            boolean delivered = latch.await(5, TimeUnit.SECONDS);

            assertThat(delivered).isTrue();
            assertThat(groupAMessages).containsExactly("fanout-msg-1");
            assertThat(groupBMessages).containsExactly("fanout-msg-1");

            subA.cancel();
            subB.cancel();
        }
    }

    // ---------------------------------------------------------------
    // Acknowledgment Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Acknowledgment")
    class AcknowledgmentTests {

        @Test
        @DisplayName("should acknowledge message and remove from PEL")
        void shouldAcknowledgeAndRemoveFromPel() throws InterruptedException {
            String channel = trackKey("ack-test-1");
            String group = "ack-group";
            CountDownLatch latch = new CountDownLatch(1);

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("ack-consumer")
                    .listener((message, ack) -> {
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            broker.send(channel, "ack-data".getBytes(StandardCharsets.UTF_8),
                    MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "ack-msg-1"));

            boolean delivered = latch.await(5, TimeUnit.SECONDS);
            assertThat(delivered).isTrue();

            // Wait a bit for the ACK to complete
            Thread.sleep(200);

            // Check PEL is empty
            var pendingInfo = redisTemplate.opsForStream()
                    .pending(KEY_PREFIX + channel, group);
            assertThat(pendingInfo.getTotalPendingMessages()).isZero();

            sub.cancel();
        }

        @Test
        @DisplayName("should leave message in PEL when not acknowledged (nack)")
        void shouldLeaveInPelWhenNacked() throws InterruptedException {
            String channel = trackKey("nack-test-1");
            String group = "nack-group";
            CountDownLatch latch = new CountDownLatch(1);

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("nack-consumer")
                    .listener((message, ack) -> {
                        // Deliberately not acking
                        ack.nack(true);
                        latch.countDown();
                    })
                    .build());

            broker.send(channel, "nack-data".getBytes(StandardCharsets.UTF_8),
                    MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "nack-msg-1"));

            boolean delivered = latch.await(5, TimeUnit.SECONDS);
            assertThat(delivered).isTrue();

            Thread.sleep(200);

            // PEL should have 1 pending message
            var pendingInfo = redisTemplate.opsForStream()
                    .pending(KEY_PREFIX + channel, group);
            assertThat(pendingInfo.getTotalPendingMessages()).isEqualTo(1L);

            sub.cancel();
        }
    }

    // ---------------------------------------------------------------
    // Idempotent Guard Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("IdempotentGuard")
    class IdempotentGuardTests {

        @Test
        @DisplayName("should prevent duplicate message processing within same group")
        void shouldPreventDuplicates() {
            IdempotentGuard guard = new IdempotentGuard(redisTemplate, Duration.ofSeconds(10));
            String channel = "idempotent-channel";
            String group = "test-group";
            String messageId = "dedup-msg-001";

            // First attempt should succeed
            boolean first = guard.tryAcquire(channel, group, messageId);
            assertThat(first).isTrue();

            // Second attempt with same ID should fail
            boolean second = guard.tryAcquire(channel, group, messageId);
            assertThat(second).isFalse();

            // Clean up
            redisTemplate.delete("messaging:idempotent:" + group + ":" + channel + ":" + messageId);
        }

        @Test
        @DisplayName("should allow same message ID on different channels")
        void shouldAllowSameIdOnDifferentChannels() {
            IdempotentGuard guard = new IdempotentGuard(redisTemplate, Duration.ofSeconds(10));
            String group = "test-group";
            String messageId = "cross-channel-msg-001";

            boolean ch1 = guard.tryAcquire("channel-1", group, messageId);
            boolean ch2 = guard.tryAcquire("channel-2", group, messageId);

            assertThat(ch1).isTrue();
            assertThat(ch2).isTrue();

            // Clean up
            redisTemplate.delete("messaging:idempotent:" + group + ":channel-1:" + messageId);
            redisTemplate.delete("messaging:idempotent:" + group + ":channel-2:" + messageId);
        }

        @Test
        @DisplayName("should allow same message ID on different consumer groups")
        void shouldAllowSameIdOnDifferentGroups() {
            IdempotentGuard guard = new IdempotentGuard(redisTemplate, Duration.ofSeconds(10));
            String channel = "shared-channel";
            String messageId = "cross-group-msg-001";

            boolean group1 = guard.tryAcquire(channel, "group-A", messageId);
            boolean group2 = guard.tryAcquire(channel, "group-B", messageId);

            assertThat(group1).isTrue();
            assertThat(group2).isTrue();

            // Clean up
            redisTemplate.delete("messaging:idempotent:group-A:" + channel + ":" + messageId);
            redisTemplate.delete("messaging:idempotent:group-B:" + channel + ":" + messageId);
        }

        @Test
        @DisplayName("should work without group (empty string)")
        void shouldWorkWithoutGroup() {
            IdempotentGuard guard = new IdempotentGuard(redisTemplate, Duration.ofSeconds(10));
            String channel = "no-group-channel";
            String messageId = "no-group-msg-001";

            boolean first = guard.tryAcquire(channel, "", messageId);
            assertThat(first).isTrue();

            boolean second = guard.tryAcquire(channel, "", messageId);
            assertThat(second).isFalse();

            // Clean up
            redisTemplate.delete("messaging:idempotent:" + channel + ":" + messageId);
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should report broker as ready after initialize()")
        void shouldBeReady() {
            assertThat(broker.isReady()).isTrue();
            assertThat(broker.name()).isEqualTo("redis");
        }

        @Test
        @DisplayName("should report correct capabilities")
        void shouldReportCapabilities() {
            var caps = broker.capabilities();
            assertThat(caps.consumerGroups()).isTrue();
            assertThat(caps.replay()).isTrue();
            assertThat(caps.ordering()).isTrue();
            assertThat(caps.deadLetter()).isFalse();
        }

        @Test
        @DisplayName("subscription should be active after subscribe and inactive after cancel")
        void shouldTrackSubscriptionLifecycle() throws InterruptedException {
            String channel = trackKey("lifecycle-test");
            String group = "lifecycle-group";

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("lifecycle-consumer")
                    .listener((message, ack) -> ack.ack())
                    .build());

            assertThat(sub.isActive()).isTrue();
            assertThat(sub.channel()).isEqualTo(channel);
            assertThat(sub.groupName()).isEqualTo(group);

            sub.cancel();

            // After cancel, the container takes a moment to stop
            Thread.sleep(300);
            assertThat(sub.isActive()).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // End-to-End Scenario Tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("End-to-End Scenarios")
    class EndToEndTests {

        @Test
        @DisplayName("should handle full publish-subscribe-ack cycle with binary payload")
        void shouldHandleFullCycleWithBinaryPayload() throws InterruptedException {
            String channel = trackKey("e2e-binary");
            String group = "e2e-group";
            CountDownLatch latch = new CountDownLatch(1);
            CopyOnWriteArrayList<byte[]> receivedPayloads = new CopyOnWriteArrayList<>();

            byte[] binaryPayload = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("e2e-consumer")
                    .listener((message, ack) -> {
                        receivedPayloads.add(message.getPayload());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            broker.send(channel, binaryPayload, MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "e2e-binary-msg")
                    .with(MessageHeaders.CONTENT_TYPE, "application/octet-stream"));

            boolean delivered = latch.await(5, TimeUnit.SECONDS);

            assertThat(delivered).isTrue();
            assertThat(receivedPayloads).hasSize(1);
            assertThat(receivedPayloads.get(0)).isEqualTo(binaryPayload);

            sub.cancel();
        }

        @Test
        @DisplayName("should handle rapid publish of 100 messages and consume all")
        void shouldHandleRapidPublishAndConsume() throws InterruptedException {
            String channel = trackKey("e2e-rapid");
            String group = "e2e-rapid-group";
            int messageCount = 100;
            CountDownLatch latch = new CountDownLatch(messageCount);
            CopyOnWriteArrayList<String> receivedIds = new CopyOnWriteArrayList<>();

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("rapid-consumer")
                    .listener((message, ack) -> {
                        receivedIds.add(message.getMessageId());
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            // Rapid-fire publish
            for (int i = 0; i < messageCount; i++) {
                broker.send(channel, ("payload-" + i).getBytes(StandardCharsets.UTF_8),
                        MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "rapid-" + i));
            }

            boolean allDelivered = latch.await(15, TimeUnit.SECONDS);

            assertThat(allDelivered)
                    .describedAs("Expected %d messages but received %d", messageCount, receivedIds.size())
                    .isTrue();
            assertThat(receivedIds).hasSize(messageCount);

            sub.cancel();
        }

        @Test
        @DisplayName("should preserve message ordering within a single consumer")
        void shouldPreserveOrdering() throws InterruptedException {
            String channel = trackKey("e2e-order");
            String group = "e2e-order-group";
            int messageCount = 20;
            CountDownLatch latch = new CountDownLatch(messageCount);
            CopyOnWriteArrayList<Integer> receivedOrder = new CopyOnWriteArrayList<>();

            broker.ensureConsumerGroup(channel, group);

            Subscription sub = broker.subscribe(SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName("order-consumer")
                    .listener((message, ack) -> {
                        String id = message.getMessageId();
                        int seq = Integer.parseInt(id.replace("order-", ""));
                        receivedOrder.add(seq);
                        ack.ack();
                        latch.countDown();
                    })
                    .build());

            for (int i = 0; i < messageCount; i++) {
                broker.send(channel, ("data-" + i).getBytes(StandardCharsets.UTF_8),
                        MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "order-" + i));
            }

            boolean allDelivered = latch.await(10, TimeUnit.SECONDS);
            assertThat(allDelivered).isTrue();

            // Verify ordering
            for (int i = 0; i < receivedOrder.size() - 1; i++) {
                assertThat(receivedOrder.get(i))
                        .describedAs("Message at index %d should be before message at index %d", i, i + 1)
                        .isLessThan(receivedOrder.get(i + 1));
            }

            sub.cancel();
        }
    }
}
