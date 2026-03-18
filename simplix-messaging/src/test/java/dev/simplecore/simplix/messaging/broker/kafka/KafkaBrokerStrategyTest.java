package dev.simplecore.simplix.messaging.broker.kafka;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("KafkaBrokerStrategy")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaBrokerStrategyTest {

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
    @DisplayName("send()")
    class SendTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should send message to Kafka topic with correct payload and headers")
        void shouldSendMessageWithPayloadAndHeaders() {
            byte[] payload = "test-payload".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-001")
                    .with(MessageHeaders.CONTENT_TYPE, "application/json");

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("orders", 2), 0L, 42, -1L, -1, -1);
            SendResult<String, byte[]> sendResult = new SendResult<>(
                    new ProducerRecord<>("orders", payload), metadata);

            CompletableFuture<SendResult<String, byte[]>> future = CompletableFuture.completedFuture(sendResult);
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            PublishResult result = strategy.send("orders", payload, headers);

            assertThat(result).isNotNull();
            assertThat(result.channel()).isEqualTo("orders");
            assertThat(result.recordId()).isEqualTo("orders-2-42");
            assertThat(result.timestamp()).isNotNull();

            ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());

            ProducerRecord<String, byte[]> captured = captor.getValue();
            assertThat(captured.topic()).isEqualTo("orders");
            assertThat(captured.value()).isEqualTo(payload);
            assertThat(captured.key()).isNull();
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should use partition key from headers as Kafka record key")
        void shouldUsePartitionKeyFromHeaders() {
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.PARTITION_KEY, "patient-123");

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("events", 0), 0L, 1, -1L, -1, -1);
            SendResult<String, byte[]> sendResult = new SendResult<>(
                    new ProducerRecord<>("events", payload), metadata);

            CompletableFuture<SendResult<String, byte[]>> future = CompletableFuture.completedFuture(sendResult);
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            strategy.send("events", payload, headers);

            ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());

            assertThat(captor.getValue().key()).isEqualTo("patient-123");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should copy message headers into Kafka record headers")
        void shouldCopyHeadersIntoKafkaRecord() {
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with("x-custom-header", "custom-value")
                    .with(MessageHeaders.CORRELATION_ID, "corr-456");

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("test-topic", 0), 0L, 0, -1L, -1, -1);
            SendResult<String, byte[]> sendResult = new SendResult<>(
                    new ProducerRecord<>("test-topic", payload), metadata);

            CompletableFuture<SendResult<String, byte[]>> future = CompletableFuture.completedFuture(sendResult);
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            strategy.send("test-topic", payload, headers);

            ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());

            ProducerRecord<String, byte[]> captured = captor.getValue();
            assertThat(captured.headers().lastHeader("x-custom-header")).isNotNull();
            assertThat(new String(captured.headers().lastHeader("x-custom-header").value(), StandardCharsets.UTF_8))
                    .isEqualTo("custom-value");
            assertThat(new String(captured.headers().lastHeader(MessageHeaders.CORRELATION_ID).value(), StandardCharsets.UTF_8))
                    .isEqualTo("corr-456");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should throw when Kafka send fails")
        void shouldThrowWhenSendFails() {
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty();

            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Broker unavailable"));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            assertThatThrownBy(() -> strategy.send("topic", payload, headers))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failed to send Kafka message");
        }
    }

    @Nested
    @DisplayName("capabilities()")
    class CapabilitiesTests {

        @Test
        @DisplayName("should report all capabilities as true")
        void shouldReportAllCapabilities() {
            BrokerCapabilities caps = strategy.capabilities();

            assertThat(caps.consumerGroups()).isTrue();
            assertThat(caps.replay()).isTrue();
            assertThat(caps.ordering()).isTrue();
            assertThat(caps.deadLetter()).isTrue();
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should not be ready before initialize()")
        void shouldNotBeReadyBeforeInit() {
            assertThat(strategy.isReady()).isFalse();
        }

        @Test
        @DisplayName("should be ready after initialize()")
        void shouldBeReadyAfterInit() {
            strategy.initialize();

            assertThat(strategy.isReady()).isTrue();
        }

        @Test
        @DisplayName("should not be ready after shutdown()")
        void shouldNotBeReadyAfterShutdown() {
            strategy.initialize();
            strategy.shutdown();

            assertThat(strategy.isReady()).isFalse();
        }

        @Test
        @DisplayName("should return 'kafka' as name")
        void shouldReturnKafkaName() {
            assertThat(strategy.name()).isEqualTo("kafka");
        }
    }

    @Nested
    @DisplayName("ensureConsumerGroup()")
    class EnsureConsumerGroupTests {

        @Test
        @DisplayName("should not throw for Kafka auto-created groups")
        void shouldNotThrow() {
            // Kafka consumer groups are auto-created; this should be a no-op
            strategy.ensureConsumerGroup("test-topic", "test-group");
            // No exception means success
        }
    }

    @Nested
    @DisplayName("default topic")
    class DefaultTopicTests {

        @Test
        @DisplayName("should throw when channel is empty and no default topic configured")
        void shouldThrowWhenNoTopicAvailable() {
            MessageHeaders headers = MessageHeaders.empty();

            assertThatThrownBy(() -> strategy.send("", new byte[0], headers))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No topic specified");
        }

        @Test
        @DisplayName("should throw when channel is null and no default topic configured")
        void shouldThrowWhenChannelNullNoDefault() {
            MessageHeaders headers = MessageHeaders.empty();

            assertThatThrownBy(() -> strategy.send(null, new byte[0], headers))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No topic specified");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should use default topic when channel is empty")
        void shouldUseDefaultTopic() {
            KafkaBrokerStrategy strategyWithDefault =
                    new KafkaBrokerStrategy(kafkaTemplate, consumerFactory, "default-topic");

            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("default-topic", 0), 0L, 0, -1L, -1, -1);
            SendResult<String, byte[]> sendResult = new SendResult<>(
                    new ProducerRecord<>("default-topic", new byte[0]), metadata);

            CompletableFuture<SendResult<String, byte[]>> future = CompletableFuture.completedFuture(sendResult);
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            strategyWithDefault.send("", new byte[0], MessageHeaders.empty());

            ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());

            assertThat(captor.getValue().topic()).isEqualTo("default-topic");
        }
    }

    @Nested
    @DisplayName("acknowledge()")
    class AcknowledgeTests {

        @Test
        @DisplayName("should not throw for Kafka per-consumer ack")
        void shouldNotThrow() {
            assertThatCode(() -> strategy.acknowledge("topic", "group", "msg-1"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("acknowledge()")
    class AcknowledgeKafkaTests {

        @Test
        @DisplayName("should be a no-op")
        void shouldNotThrow() {
            assertThatCode(() -> strategy.acknowledge("topic", "group", "msg-1"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("InterruptedException handling")
    class InterruptedTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should throw IllegalStateException on interruption")
        void shouldHandleInterruption() {
            byte[] payload = "data".getBytes();
            MessageHeaders headers = MessageHeaders.empty();

            CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();

            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> strategy.send("topic", payload, headers))
                    .isInstanceOf(IllegalStateException.class);

            // Clear interrupt flag
            Thread.interrupted();
        }
    }
}
