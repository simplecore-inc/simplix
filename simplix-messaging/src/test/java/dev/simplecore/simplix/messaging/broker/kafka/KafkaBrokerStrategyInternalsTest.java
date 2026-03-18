package dev.simplecore.simplix.messaging.broker.kafka;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@DisplayName("KafkaBrokerStrategy internals")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaBrokerStrategyInternalsTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Mock
    private ConsumerFactory<String, byte[]> consumerFactory;

    private KafkaBrokerStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new KafkaBrokerStrategy(kafkaTemplate, consumerFactory);
    }

    @Nested
    @DisplayName("dispatchMessage via reflection")
    class DispatchTests {

        @Test
        @DisplayName("should dispatch consumer record to listener")
        void shouldDispatchConsumerRecord() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();
            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            byte[] payload = "kafka-payload".getBytes(StandardCharsets.UTF_8);
            Headers headers = new RecordHeaders();
            headers.add(MessageHeaders.MESSAGE_ID, "kmsg-1".getBytes(StandardCharsets.UTF_8));
            headers.add(MessageHeaders.CONTENT_TYPE, "application/json".getBytes(StandardCharsets.UTF_8));

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                    "my-topic", 0, 42L, "key", payload);
            record.headers().add(MessageHeaders.MESSAGE_ID, "kmsg-1".getBytes(StandardCharsets.UTF_8));

            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("my-topic")
                    .groupName("my-group")
                    .consumerName("c1")
                    .listener((msg, ack) -> {
                        received.set(msg);
                        receivedAck.set(ack);
                    })
                    .build();

            // Use reflection to call dispatchMessage
            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo(payload);
            assertThat(received.get().getChannel()).isEqualTo("my-topic");
            assertThat(received.get().getMessageId()).isEqualTo("kmsg-1");
        }

        @Test
        @DisplayName("should handle null record value")
        void shouldHandleNullRecordValue() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                    "my-topic", 0, 42L, "key", null);

            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("my-topic")
                    .groupName("my-group")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEmpty();
        }

        @Test
        @DisplayName("should use topic-partition-offset as message ID when header missing")
        void shouldUseOffsetAsMessageId() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>(
                    "events", 2, 99L, "key", "data".getBytes());

            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("events")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            assertThat(received.get().getMessageId()).isEqualTo("events-2-99");
        }
    }

    @Nested
    @DisplayName("KafkaMessageAcknowledgment")
    class KafkaAckTests {

        @Test
        @DisplayName("should ack via Kafka Acknowledgment")
        void shouldAck() throws Exception {
            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("t", 0, 0L, "k", "v".getBytes());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("t")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            receivedAck.get().ack();
            verify(kafkaAck).acknowledge();
        }

        @Test
        @DisplayName("should nack via Kafka Acknowledgment")
        void shouldNack() throws Exception {
            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("t", 0, 0L, "k", "v".getBytes());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("t")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            receivedAck.get().nack(true);
            verify(kafkaAck).nack(java.time.Duration.ZERO);
        }

        @Test
        @DisplayName("should reject via Kafka Acknowledgment (ack to skip)")
        void shouldReject() throws Exception {
            Acknowledgment kafkaAck = mock(Acknowledgment.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("t", 0, 0L, "k", "v".getBytes());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("t")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, kafkaAck, request);

            receivedAck.get().reject("bad message");
            verify(kafkaAck).acknowledge();
        }

        @Test
        @DisplayName("should handle null kafka acknowledgment gracefully")
        void shouldHandleNullKafkaAck() throws Exception {
            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("t", 0, 0L, "k", "v".getBytes());

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("t")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = KafkaBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage", ConsumerRecord.class, Acknowledgment.class, SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, record, (Acknowledgment) null, request);

            // These should not throw with null ack
            assertThatCode(() -> receivedAck.get().ack()).doesNotThrowAnyException();
            assertThatCode(() -> receivedAck.get().nack(true)).doesNotThrowAnyException();
            assertThatCode(() -> receivedAck.get().reject("reason")).doesNotThrowAnyException();
        }
    }
}
