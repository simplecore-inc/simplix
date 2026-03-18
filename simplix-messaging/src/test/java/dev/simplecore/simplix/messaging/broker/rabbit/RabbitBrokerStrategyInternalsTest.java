package dev.simplecore.simplix.messaging.broker.rabbit;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RabbitBrokerStrategy internals")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitBrokerStrategyInternalsTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ConnectionFactory connectionFactory;

    private RabbitBrokerStrategy strategy;

    @BeforeEach
    void setUp() {
        Connection mockConnection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(mockConnection);
        com.rabbitmq.client.Channel mockChannel = mock(com.rabbitmq.client.Channel.class);
        when(mockConnection.createChannel(any(Boolean.class))).thenReturn(mockChannel);

        strategy = new RabbitBrokerStrategy(rabbitTemplate, connectionFactory);
    }

    @Nested
    @DisplayName("dispatchMessage via reflection")
    class DispatchTests {

        @Test
        @DisplayName("should dispatch AMQP message to listener")
        void shouldDispatchAmqpMessage() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();
            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            byte[] payload = "rabbit-payload".getBytes();
            MessageProperties properties = new MessageProperties();
            properties.setMessageId("rmsg-1");
            properties.setContentType("application/json");
            properties.setDeliveryTag(42L);
            properties.setHeader("x-custom", "value");

            org.springframework.amqp.core.Message amqpMessage =
                    new org.springframework.amqp.core.Message(payload, properties);

            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("my-queue")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {
                        received.set(msg);
                        receivedAck.set(ack);
                    })
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMessage, rabbitChannel, request);

            assertThat(received.get()).isNotNull();
            assertThat(received.get().getPayload()).isEqualTo(payload);
            assertThat(received.get().getChannel()).isEqualTo("my-queue");
            assertThat(received.get().getMessageId()).isEqualTo("rmsg-1");
        }

        @Test
        @DisplayName("should handle null body in AMQP message")
        void shouldHandleNullBody() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            MessageProperties properties = new MessageProperties();
            properties.setMessageId("rmsg-2");
            properties.setDeliveryTag(1L);

            org.springframework.amqp.core.Message amqpMessage =
                    new org.springframework.amqp.core.Message(new byte[0], properties);

            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMessage, rabbitChannel, request);

            assertThat(received.get().getPayload()).isEmpty();
        }

        @Test
        @DisplayName("should use message header ID when properties messageId is null")
        void shouldFallbackToHeaderId() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            MessageProperties properties = new MessageProperties();
            properties.setDeliveryTag(1L);
            properties.setHeader(MessageHeaders.MESSAGE_ID, "header-id");

            org.springframework.amqp.core.Message amqpMessage =
                    new org.springframework.amqp.core.Message("data".getBytes(), properties);

            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMessage, rabbitChannel, request);

            assertThat(received.get().getMessageId()).isEqualTo("header-id");
        }

        @Test
        @DisplayName("should generate UUID when no message ID available")
        void shouldGenerateUuidWhenNoId() throws Exception {
            AtomicReference<Message<byte[]>> received = new AtomicReference<>();

            MessageProperties properties = new MessageProperties();
            properties.setDeliveryTag(1L);
            // No messageId, no header

            org.springframework.amqp.core.Message amqpMessage =
                    new org.springframework.amqp.core.Message("data".getBytes(), properties);

            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> received.set(msg))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMessage, rabbitChannel, request);

            assertThat(received.get().getMessageId()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("RabbitMessageAcknowledgment")
    class RabbitAckTests {

        @Test
        @DisplayName("should ack via RabbitMQ channel")
        void shouldAck() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(99L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            receivedAck.get().ack();
            verify(rabbitChannel).basicAck(99L, false);
        }

        @Test
        @DisplayName("should nack via RabbitMQ channel")
        void shouldNack() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(50L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            receivedAck.get().nack(true);
            verify(rabbitChannel).basicNack(50L, false, true);
        }

        @Test
        @DisplayName("should reject via RabbitMQ channel")
        void shouldReject() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(75L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            receivedAck.get().reject("poison message");
            verify(rabbitChannel).basicReject(75L, false);
        }

        @Test
        @DisplayName("should handle ack exception gracefully")
        void shouldHandleAckException() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);
            doThrow(new IOException("Channel closed")).when(rabbitChannel).basicAck(anyLong(), anyBoolean());

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(1L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            assertThatCode(() -> receivedAck.get().ack()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle nack exception gracefully")
        void shouldHandleNackException() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);
            doThrow(new IOException("Channel closed")).when(rabbitChannel).basicNack(anyLong(), anyBoolean(), anyBoolean());

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(1L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            assertThatCode(() -> receivedAck.get().nack(false)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle reject exception gracefully")
        void shouldHandleRejectException() throws Exception {
            com.rabbitmq.client.Channel rabbitChannel = mock(com.rabbitmq.client.Channel.class);
            doThrow(new IOException("Channel closed")).when(rabbitChannel).basicReject(anyLong(), anyBoolean());

            AtomicReference<MessageAcknowledgment> receivedAck = new AtomicReference<>();

            MessageProperties props = new MessageProperties();
            props.setDeliveryTag(1L);
            org.springframework.amqp.core.Message amqpMsg =
                    new org.springframework.amqp.core.Message("data".getBytes(), props);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("q")
                    .groupName("g")
                    .consumerName("c")
                    .listener((msg, ack) -> receivedAck.set(ack))
                    .build();

            Method dispatchMethod = RabbitBrokerStrategy.class.getDeclaredMethod(
                    "dispatchMessage",
                    org.springframework.amqp.core.Message.class,
                    com.rabbitmq.client.Channel.class,
                    SubscribeRequest.class);
            dispatchMethod.setAccessible(true);
            dispatchMethod.invoke(strategy, amqpMsg, rabbitChannel, request);

            assertThatCode(() -> receivedAck.get().reject("bad")).doesNotThrowAnyException();
        }
    }
}
