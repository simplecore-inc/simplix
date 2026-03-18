package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LocalBrokerStrategy")
class LocalBrokerStrategyTest {

    private LocalBrokerStrategy broker;

    @BeforeEach
    void setUp() {
        broker = new LocalBrokerStrategy();
        broker.initialize();
    }

    @AfterEach
    void tearDown() {
        broker.shutdown();
    }

    @Nested
    @DisplayName("initialize and shutdown")
    class LifecycleTests {

        @Test
        @DisplayName("should be ready after initialization")
        void shouldBeReadyAfterInit() {
            assertThat(broker.isReady()).isTrue();
        }

        @Test
        @DisplayName("should not be ready after shutdown")
        void shouldNotBeReadyAfterShutdown() {
            broker.shutdown();
            assertThat(broker.isReady()).isFalse();
        }

        @Test
        @DisplayName("should clear state on shutdown")
        void shouldClearStateOnShutdown() {
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "test-id");
            broker.send("ch", new byte[]{1}, headers);
            assertThat(broker.getPublishedMessages("ch")).hasSize(1);

            broker.shutdown();
            assertThat(broker.getPublishedMessages("ch")).isEmpty();
        }
    }

    @Nested
    @DisplayName("name")
    class NameTests {

        @Test
        @DisplayName("should return 'local'")
        void shouldReturnLocal() {
            assertThat(broker.name()).isEqualTo("local");
        }
    }

    @Nested
    @DisplayName("capabilities")
    class CapabilitiesTests {

        @Test
        @DisplayName("should support consumer groups and ordering but not replay or dead letter")
        void shouldReturnCorrectCapabilities() {
            BrokerCapabilities caps = broker.capabilities();
            assertThat(caps.consumerGroups()).isTrue();
            assertThat(caps.replay()).isFalse();
            assertThat(caps.ordering()).isTrue();
            assertThat(caps.deadLetter()).isFalse();
        }
    }

    @Nested
    @DisplayName("send")
    class SendTests {

        @Test
        @DisplayName("should store published message")
        void shouldStorePublishedMessage() {
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            byte[] payload = "hello".getBytes();

            PublishResult result = broker.send("test-channel", payload, headers);

            assertThat(result).isNotNull();
            assertThat(result.channel()).isEqualTo("test-channel");
            assertThat(result.recordId()).isNotBlank();
            assertThat(result.timestamp()).isNotNull();

            List<LocalBrokerStrategy.PublishedMessage> messages =
                    broker.getPublishedMessages("test-channel");
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).payload()).isEqualTo(payload);
        }

        @Test
        @DisplayName("should deliver to ungrouped subscribers")
        void shouldDeliverToUngroupedSubscribers() {
            List<Message<byte[]>> received = new ArrayList<>();

            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> received.add(msg))
                    .build());

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            broker.send("ch", "data".getBytes(), headers);

            assertThat(received).hasSize(1);
            assertThat(received.get(0).getChannel()).isEqualTo("ch");
        }

        @Test
        @DisplayName("should deliver to all ungrouped subscribers (broadcast)")
        void shouldBroadcastToAllUngrouped() {
            AtomicInteger counter = new AtomicInteger(0);

            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> counter.incrementAndGet())
                    .build());

            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> counter.incrementAndGet())
                    .build());

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            broker.send("ch", "data".getBytes(), headers);

            assertThat(counter.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should deliver to one subscriber per consumer group (round-robin)")
        void shouldRoundRobinWithinGroup() {
            AtomicInteger subscriber1Count = new AtomicInteger(0);
            AtomicInteger subscriber2Count = new AtomicInteger(0);

            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("group-a")
                    .consumerName("consumer-1")
                    .listener((msg, ack) -> subscriber1Count.incrementAndGet())
                    .build());

            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("group-a")
                    .consumerName("consumer-2")
                    .listener((msg, ack) -> subscriber2Count.incrementAndGet())
                    .build());

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            // Send two messages; each should go to a different subscriber
            broker.send("ch", "data1".getBytes(), headers);
            broker.send("ch", "data2".getBytes(), headers);

            // Total messages delivered should be 2 (one per send, round-robin across group)
            assertThat(subscriber1Count.get() + subscriber2Count.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("should not fail when no subscribers exist")
        void shouldNotFailWhenNoSubscribers() {
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            assertThatCode(() -> broker.send("empty-channel", "data".getBytes(), headers))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle listener exception gracefully")
        void shouldHandleListenerException() {
            broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> {
                        throw new RuntimeException("Handler error");
                    })
                    .build());

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            assertThatCode(() -> broker.send("ch", "data".getBytes(), headers))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should return active subscription")
        void shouldReturnActiveSubscription() {
            Subscription subscription = broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .listener((msg, ack) -> {})
                    .build());

            assertThat(subscription).isNotNull();
            assertThat(subscription.channel()).isEqualTo("ch");
            assertThat(subscription.groupName()).isEqualTo("grp");
            assertThat(subscription.isActive()).isTrue();
        }

        @Test
        @DisplayName("should cancel subscription")
        void shouldCancelSubscription() {
            AtomicInteger counter = new AtomicInteger(0);

            Subscription subscription = broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> counter.incrementAndGet())
                    .build());

            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();

            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            broker.send("ch", "data".getBytes(), headers);

            assertThat(counter.get()).isEqualTo(0);
        }

        @Test
        @DisplayName("should allow multiple cancellations without error")
        void shouldAllowDoubleCancellation() {
            Subscription subscription = broker.subscribe(SubscribeRequest.builder()
                    .channel("ch")
                    .listener((msg, ack) -> {})
                    .build());

            subscription.cancel();
            assertThatCode(subscription::cancel).doesNotThrowAnyException();
            assertThat(subscription.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("ensureConsumerGroup")
    class EnsureConsumerGroupTests {

        @Test
        @DisplayName("should be a no-op")
        void shouldBeNoOp() {
            assertThatCode(() -> broker.ensureConsumerGroup("ch", "group"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("acknowledge")
    class AcknowledgeTests {

        @Test
        @DisplayName("should be a no-op")
        void shouldBeNoOp() {
            assertThatCode(() -> broker.acknowledge("ch", "group", "msg-1"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Test assertion helpers")
    class TestHelperTests {

        @Test
        @DisplayName("should return empty list for unknown channel")
        void shouldReturnEmptyForUnknownChannel() {
            assertThat(broker.getPublishedMessages("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("should clear published messages")
        void shouldClearPublishedMessages() {
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            broker.send("ch", "data".getBytes(), headers);
            assertThat(broker.getPublishedMessages("ch")).hasSize(1);

            broker.clearPublishedMessages();
            assertThat(broker.getPublishedMessages("ch")).isEmpty();
        }
    }
}
