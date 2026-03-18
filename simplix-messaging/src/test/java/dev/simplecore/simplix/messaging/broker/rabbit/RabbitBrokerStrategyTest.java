package dev.simplecore.simplix.messaging.broker.rabbit;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RabbitBrokerStrategy")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitBrokerStrategyTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ConnectionFactory connectionFactory;

    private RabbitBrokerStrategy strategy;

    @BeforeEach
    void setUp() {
        // Mock the connection factory to support RabbitAdmin initialization
        Connection mockConnection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(mockConnection);
        com.rabbitmq.client.Channel mockChannel = mock(com.rabbitmq.client.Channel.class);
        when(mockConnection.createChannel(any(Boolean.class))).thenReturn(mockChannel);

        strategy = new RabbitBrokerStrategy(rabbitTemplate, connectionFactory);
    }

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("should send message to exchange with channel as routing key")
        void shouldSendToExchangeWithRoutingKey() {
            byte[] payload = "order-created".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-001")
                    .with(MessageHeaders.CONTENT_TYPE, "application/json");

            PublishResult result = strategy.send("order-events", payload, headers);

            assertThat(result).isNotNull();
            assertThat(result.channel()).isEqualTo("order-events");
            assertThat(result.recordId()).isEqualTo("msg-001");
            assertThat(result.timestamp()).isNotNull();

            verify(rabbitTemplate).send(eq("simplix.messaging"), eq("order-events"), any(Message.class));
        }

        @Test
        @DisplayName("should copy message headers into AMQP message properties")
        void shouldCopyHeadersIntoAmqpProperties() {
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-002")
                    .with(MessageHeaders.CONTENT_TYPE, "application/json")
                    .with(MessageHeaders.CORRELATION_ID, "corr-789")
                    .with("x-custom", "value");

            strategy.send("test-channel", payload, headers);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(eq("simplix.messaging"), eq("test-channel"), captor.capture());

            Message captured = captor.getValue();
            assertThat(captured.getBody()).isEqualTo(payload);
            assertThat(captured.getMessageProperties().getMessageId()).isEqualTo("msg-002");
            assertThat(captured.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(captured.getMessageProperties().getHeaders()).containsEntry("x-custom", "value");
            assertThat(captured.getMessageProperties().getHeaders())
                    .containsEntry(MessageHeaders.CORRELATION_ID, "corr-789");
        }

        @Test
        @DisplayName("should generate UUID as message ID when not provided in headers")
        void shouldGenerateMessageIdWhenNotProvided() {
            byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty();

            PublishResult result = strategy.send("events", payload, headers);

            assertThat(result.recordId()).isNotNull();
            assertThat(result.recordId()).isNotEmpty();
        }

        @Test
        @DisplayName("should set default content type when not provided")
        void shouldSetDefaultContentType() {
            byte[] payload = "binary".getBytes(StandardCharsets.UTF_8);
            MessageHeaders headers = MessageHeaders.empty();

            strategy.send("binary-channel", payload, headers);

            ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
            verify(rabbitTemplate).send(eq("simplix.messaging"), eq("binary-channel"), captor.capture());

            assertThat(captor.getValue().getMessageProperties().getContentType())
                    .isEqualTo("application/octet-stream");
        }
    }

    @Nested
    @DisplayName("capabilities()")
    class CapabilitiesTests {

        @Test
        @DisplayName("should report correct capabilities for RabbitMQ")
        void shouldReportCorrectCapabilities() {
            BrokerCapabilities caps = strategy.capabilities();

            assertThat(caps.consumerGroups()).isFalse();
            assertThat(caps.replay()).isFalse();
            assertThat(caps.ordering()).isFalse();
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
        @DisplayName("should return 'rabbit' as name")
        void shouldReturnRabbitName() {
            assertThat(strategy.name()).isEqualTo("rabbit");
        }
    }

    @Nested
    @DisplayName("ensureConsumerGroup()")
    class EnsureConsumerGroupTests {

        @Test
        @DisplayName("should not throw for RabbitMQ competing consumers")
        void shouldNotThrow() {
            // RabbitMQ does not have consumer groups; this should be a no-op
            strategy.ensureConsumerGroup("test-queue", "test-group");
            // No exception means success
        }
    }

    @Nested
    @DisplayName("acknowledge()")
    class AcknowledgeTests {

        @Test
        @DisplayName("should not throw for per-consumer ack")
        void shouldNotThrow() {
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> strategy.acknowledge("queue", "group", "msg-1"));
        }
    }

    @Nested
    @DisplayName("initialize()")
    class InitializeTests {

        @Test
        @DisplayName("should declare exchanges and set ready")
        void shouldDeclareExchangesAndSetReady() {
            strategy.initialize();
            assertThat(strategy.isReady()).isTrue();
        }
    }

    @Nested
    @DisplayName("shutdown without active containers")
    class ShutdownTests {

        @Test
        @DisplayName("should set not ready after shutdown")
        void shouldSetNotReadyAfterShutdown() {
            strategy.initialize();
            strategy.shutdown();
            assertThat(strategy.isReady()).isFalse();
        }
    }
}
