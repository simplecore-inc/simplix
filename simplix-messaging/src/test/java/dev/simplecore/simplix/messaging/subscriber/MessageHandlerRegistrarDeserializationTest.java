package dev.simplecore.simplix.messaging.subscriber;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("MessageHandlerRegistrar deserialization and listener invocation")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageHandlerRegistrarDeserializationTest {

    @Mock
    private ObjectProvider<BrokerStrategy> brokerStrategyProvider;

    @Mock
    private ObjectProvider<IdempotentGuard> idempotentGuardProvider;

    @Mock
    private ObjectProvider<MessagingProperties> propertiesProvider;

    @Mock
    private Environment environment;

    @Mock
    private BrokerStrategy brokerStrategy;

    @Mock
    private IdempotentGuard idempotentGuard;

    private MessageHandlerRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new MessageHandlerRegistrar(
                brokerStrategyProvider, environment, idempotentGuardProvider, propertiesProvider);
    }

    @Nested
    @DisplayName("String payload deserialization")
    class StringPayloadTests {

        @Test
        @DisplayName("should deserialize byte[] payload to String")
        void shouldDeserializeToString() {
            StringPayloadHandler bean = new StringPayloadHandler();
            when(environment.resolvePlaceholders("str-channel")).thenReturn("str-channel");
            when(environment.resolvePlaceholders("str-group")).thenReturn("str-group");
            registrar.postProcessAfterInitialization(bean, "stringHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            // Get the listener and send a message to it
            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-1")
                    .channel("str-channel")
                    .payload(payload)
                    .headers(MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "msg-1"))
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack);

            assertThat(bean.received).isNotNull();
            assertThat(bean.received.getPayload()).isEqualTo("hello world");
            verify(ack).ack(); // autoAck=true
        }
    }

    @Nested
    @DisplayName("byte[] payload passthrough")
    class ByteArrayPayloadTests {

        @Test
        @DisplayName("should pass byte[] payload through without deserialization")
        void shouldPassthroughBytes() {
            ByteArrayPayloadHandler bean = new ByteArrayPayloadHandler();
            when(environment.resolvePlaceholders("bytes-channel")).thenReturn("bytes-channel");
            when(environment.resolvePlaceholders("")).thenReturn("");
            registrar.postProcessAfterInitialization(bean, "bytesHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            byte[] payload = new byte[]{1, 2, 3};
            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-2")
                    .channel("bytes-channel")
                    .payload(payload)
                    .headers(MessageHeaders.empty())
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack);

            assertThat(bean.received).isNotNull();
            assertThat((byte[]) bean.received.getPayload()).isEqualTo(new byte[]{1, 2, 3});
            verify(ack).ack();
        }
    }

    @Nested
    @DisplayName("Two-parameter handler (Message + Ack)")
    class TwoParamTests {

        @Test
        @DisplayName("should pass ack to handler with two parameters")
        void shouldPassAckToHandler() {
            TwoParamHandler bean = new TwoParamHandler();
            when(environment.resolvePlaceholders("ack-channel")).thenReturn("ack-channel");
            when(environment.resolvePlaceholders("ack-group")).thenReturn("ack-group");
            registrar.postProcessAfterInitialization(bean, "twoParamHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-3")
                    .channel("ack-channel")
                    .payload("test".getBytes())
                    .headers(MessageHeaders.empty())
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack);

            assertThat(bean.receivedAck).isSameAs(ack);
            // autoAck=false so ack.ack() should NOT be called by the registrar
            verify(ack, never()).ack();
        }
    }

    @Nested
    @DisplayName("Idempotent handling")
    class IdempotentTests {

        @Test
        @DisplayName("should skip duplicate messages when idempotent is enabled")
        void shouldSkipDuplicates() {
            IdempotentHandler bean = new IdempotentHandler();
            when(environment.resolvePlaceholders("idem-channel")).thenReturn("idem-channel");
            when(environment.resolvePlaceholders("idem-group")).thenReturn("idem-group");
            registrar.postProcessAfterInitialization(bean, "idempotentHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(idempotentGuard);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            // First message: not duplicate
            when(idempotentGuard.tryAcquire("idem-channel", "idem-group", "msg-idem"))
                    .thenReturn(true);

            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-idem")
                    .channel("idem-channel")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "msg-idem"))
                    .build();

            MessageAcknowledgment ack1 = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack1);
            assertThat(bean.callCount).isEqualTo(1);
            verify(ack1).ack(); // autoAck

            // Second message: duplicate
            when(idempotentGuard.tryAcquire("idem-channel", "idem-group", "msg-idem"))
                    .thenReturn(false);

            MessageAcknowledgment ack2 = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack2);
            assertThat(bean.callCount).isEqualTo(1); // not incremented
            verify(ack2).ack(); // acked to remove from PEL
        }

        @Test
        @DisplayName("should warn and proceed when idempotent guard is null")
        void shouldWarnWhenGuardNull() {
            IdempotentHandler bean = new IdempotentHandler();
            when(environment.resolvePlaceholders("idem-channel")).thenReturn("idem-channel");
            when(environment.resolvePlaceholders("idem-group")).thenReturn("idem-group");
            registrar.postProcessAfterInitialization(bean, "idempotentHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null); // no guard
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-idem")
                    .channel("idem-channel")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "msg-idem"))
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack);

            // Should still process (no guard = proceed with warning)
            assertThat(bean.callCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Handler exception handling")
    class ExceptionTests {

        @Test
        @DisplayName("should wrap handler InvocationTargetException")
        void shouldWrapInvocationException() {
            ThrowingHandler bean = new ThrowingHandler();
            when(environment.resolvePlaceholders("throw-channel")).thenReturn("throw-channel");
            when(environment.resolvePlaceholders("")).thenReturn("");
            registrar.postProcessAfterInitialization(bean, "throwingHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-throw")
                    .channel("throw-channel")
                    .payload("data".getBytes())
                    .headers(MessageHeaders.empty())
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> listener.onMessage(rawMessage, ack));
        }
    }

    @Nested
    @DisplayName("Object payload (raw Message type)")
    class ObjectPayloadTests {

        @Test
        @DisplayName("should pass raw bytes for raw Message handler")
        @SuppressWarnings("rawtypes")
        void shouldPassRawBytesForRawMessage() {
            RawMessageHandler bean = new RawMessageHandler();
            when(environment.resolvePlaceholders("raw-channel")).thenReturn("raw-channel");
            when(environment.resolvePlaceholders("")).thenReturn("");
            registrar.postProcessAfterInitialization(bean, "rawHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            SubscribeRequest capturedRequest = requestCaptor.getValue();
            MessageListener<byte[]> listener = capturedRequest.listener();

            Message<byte[]> rawMessage = Message.<byte[]>builder()
                    .messageId("msg-raw")
                    .channel("raw-channel")
                    .payload(new byte[]{7, 8, 9})
                    .headers(MessageHeaders.empty())
                    .build();

            MessageAcknowledgment ack = mock(MessageAcknowledgment.class);
            listener.onMessage(rawMessage, ack);

            assertThat(bean.received).isNotNull();
        }
    }

    @Nested
    @DisplayName("Placeholder resolution")
    class PlaceholderTests {

        @Test
        @DisplayName("should resolve placeholders in channel name")
        void shouldResolvePlaceholder() {
            PlaceholderHandler bean = new PlaceholderHandler();
            when(environment.resolvePlaceholders("${my.channel}")).thenReturn("resolved-channel");
            when(environment.resolvePlaceholders("")).thenReturn("");
            registrar.postProcessAfterInitialization(bean, "placeholderHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);

            ArgumentCaptor<SubscribeRequest> requestCaptor = ArgumentCaptor.forClass(SubscribeRequest.class);
            when(brokerStrategy.subscribe(requestCaptor.capture())).thenReturn(mockSub);

            registrar.start();

            assertThat(requestCaptor.getValue().channel()).isEqualTo("resolved-channel");
        }
    }

    // --- Test beans ---

    @SuppressWarnings("rawtypes")
    static class RawMessageHandler {
        Message received;

        @MessageHandler(channel = "raw-channel")
        public void handle(Message message) {
            this.received = message;
        }
    }

    static class PlaceholderHandler {
        @MessageHandler(channel = "${my.channel}")
        public void handle(Message<byte[]> message) {
            // no-op
        }
    }

    static class StringPayloadHandler {
        Message<String> received;

        @MessageHandler(channel = "str-channel", group = "str-group")
        public void handle(Message<String> message) {
            this.received = message;
        }
    }

    static class ByteArrayPayloadHandler {
        Message<?> received;

        @MessageHandler(channel = "bytes-channel")
        public void handle(Message<byte[]> message) {
            this.received = message;
        }
    }

    static class TwoParamHandler {
        MessageAcknowledgment receivedAck;

        @MessageHandler(channel = "ack-channel", group = "ack-group", autoAck = false)
        public void handle(Message<byte[]> message, MessageAcknowledgment ack) {
            this.receivedAck = ack;
        }
    }

    static class IdempotentHandler {
        int callCount = 0;

        @MessageHandler(channel = "idem-channel", group = "idem-group", idempotent = true)
        public void handle(Message<byte[]> message) {
            callCount++;
        }
    }

    static class ThrowingHandler {
        @MessageHandler(channel = "throw-channel")
        public void handle(Message<byte[]> message) {
            throw new RuntimeException("Handler error");
        }
    }
}
