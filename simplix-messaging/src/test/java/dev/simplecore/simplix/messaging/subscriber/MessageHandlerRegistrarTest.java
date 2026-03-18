package dev.simplecore.simplix.messaging.subscriber;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

@DisplayName("MessageHandlerRegistrar")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageHandlerRegistrarTest {

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

    private MessageHandlerRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new MessageHandlerRegistrar(
                brokerStrategyProvider, environment, idempotentGuardProvider, propertiesProvider);
    }

    @Nested
    @DisplayName("postProcessAfterInitialization")
    class PostProcessTests {

        @Test
        @DisplayName("should discover annotated handler methods")
        void shouldDiscoverAnnotatedMethods() {
            SampleHandler bean = new SampleHandler();

            Object result = registrar.postProcessAfterInitialization(bean, "sampleHandler");

            assertThat(result).isSameAs(bean);
        }

        @Test
        @DisplayName("should skip beans without @MessageHandler methods")
        void shouldSkipUnannotatedBeans() {
            String bean = "plainBean";

            Object result = registrar.postProcessAfterInitialization(bean, "plainBean");

            assertThat(result).isSameAs(bean);
        }

        @Test
        @DisplayName("should reject handler method with wrong parameter count")
        void shouldRejectInvalidParameterCount() {
            InvalidHandlerNoParams bean = new InvalidHandlerNoParams();

            assertThatThrownBy(() -> registrar.postProcessAfterInitialization(bean, "invalidHandler"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must have 1 or 2 parameters");
        }

        @Test
        @DisplayName("should reject handler method with wrong first parameter type")
        void shouldRejectWrongFirstParamType() {
            InvalidHandlerWrongType bean = new InvalidHandlerWrongType();

            assertThatThrownBy(() -> registrar.postProcessAfterInitialization(bean, "invalidHandler"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("first parameter must be Message<T>");
        }
    }

    @Nested
    @DisplayName("afterSingletonsInstantiated")
    class AfterSingletonsTests {

        @Test
        @DisplayName("should resolve lazy dependencies")
        void shouldResolveLazyDependencies() {
            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);

            registrar.afterSingletonsInstantiated();

            verify(brokerStrategyProvider).getObject();
            verify(idempotentGuardProvider).getIfAvailable();
            verify(propertiesProvider).getIfAvailable();
        }
    }

    @Nested
    @DisplayName("SmartLifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should not be running initially")
        void shouldNotBeRunningInitially() {
            assertThat(registrar.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should return late lifecycle phase")
        void shouldReturnLatePhase() {
            assertThat(registrar.getPhase()).isEqualTo(Integer.MAX_VALUE - 100);
        }

        @Test
        @DisplayName("should start and set running to true")
        void shouldStartAndSetRunning() {
            // No registrations, so start is quick
            registrar.start();
            assertThat(registrar.isRunning()).isTrue();
        }

        @Test
        @DisplayName("should stop and set running to false")
        void shouldStopAndSetRunning() {
            registrar.start();
            registrar.stop();
            assertThat(registrar.isRunning()).isFalse();
        }

        @Test
        @DisplayName("should not fail when stopping without being started")
        void shouldNotFailWhenStopWithoutStart() {
            assertThatCode(() -> registrar.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should start subscriptions with broker strategy")
        void shouldStartSubscriptionsWithBroker() {
            // Set up a handler
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            // Resolve dependencies
            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            // Mock broker behavior
            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();

            verify(brokerStrategy).ensureConsumerGroup("test-channel", "test-group");
            verify(brokerStrategy).subscribe(any(SubscribeRequest.class));
            assertThat(registrar.getActiveSubscriptions()).hasSize(1);
        }

        @Test
        @DisplayName("should cancel active subscriptions on stop")
        void shouldCancelSubscriptionsOnStop() {
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();
            registrar.stop();

            verify(mockSub).cancel();
            assertThat(registrar.getActiveSubscriptions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getActiveSubscriptions")
    class ActiveSubscriptionsTests {

        @Test
        @DisplayName("should return empty list when no subscriptions")
        void shouldReturnEmptyWhenNoSubscriptions() {
            assertThat(registrar.getActiveSubscriptions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Startup delay")
    class StartupDelayTests {

        @Test
        @DisplayName("should defer subscriptions when startup delay is configured")
        void shouldDeferWithStartupDelay() {
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            MessagingProperties properties = new MessagingProperties();
            properties.setSubscriberStartupDelay(Duration.ofMillis(50));

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(properties);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();
            assertThat(registrar.isRunning()).isTrue();

            // Wait for deferred start
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            verify(brokerStrategy, atLeastOnce()).subscribe(any(SubscribeRequest.class));
        }
    }

    @Nested
    @DisplayName("Handler with two parameters (Message + MessageAcknowledgment)")
    class TwoParamHandlerTests {

        @Test
        @DisplayName("should accept handler with MessageAcknowledgment parameter")
        void shouldAcceptTwoParamHandler() {
            TwoParamHandler bean = new TwoParamHandler();

            Object result = registrar.postProcessAfterInitialization(bean, "twoParamHandler");
            assertThat(result).isSameAs(bean);
        }
    }

    @Nested
    @DisplayName("Handler with raw Message parameter (no generics)")
    class RawMessageHandlerTests {

        @Test
        @DisplayName("should accept handler with raw Message parameter")
        void shouldAcceptRawMessageHandler() {
            RawMessageHandler bean = new RawMessageHandler();

            Object result = registrar.postProcessAfterInitialization(bean, "rawHandler");
            assertThat(result).isSameAs(bean);
        }
    }

    @Nested
    @DisplayName("Handler with byte[] payload type")
    class ByteArrayHandlerTests {

        @Test
        @DisplayName("should accept handler with byte[] payload and start subscription")
        void shouldSubscribeByteArrayHandler() {
            ByteArrayHandler bean = new ByteArrayHandler();
            when(environment.resolvePlaceholders("bytes-channel")).thenReturn("bytes-channel");
            when(environment.resolvePlaceholders("")).thenReturn("");
            registrar.postProcessAfterInitialization(bean, "byteArrayHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();

            // Consumer group should not be ensured for empty group name
            verify(brokerStrategy, never()).ensureConsumerGroup(anyString(), anyString());
            verify(brokerStrategy).subscribe(any(SubscribeRequest.class));
        }
    }

    @Nested
    @DisplayName("Invalid handler second param")
    class InvalidSecondParamTests {

        @Test
        @DisplayName("should reject handler with wrong second parameter type")
        void shouldRejectWrongSecondParam() {
            InvalidSecondParam bean = new InvalidSecondParam();

            assertThatThrownBy(() -> registrar.postProcessAfterInitialization(bean, "invalidSecondParam"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("second parameter must be MessageAcknowledgment");
        }
    }

    @Nested
    @DisplayName("Handler with too many parameters")
    class TooManyParamsTests {

        @Test
        @DisplayName("should reject handler with three parameters")
        void shouldRejectThreeParams() {
            ThreeParamHandler bean = new ThreeParamHandler();

            assertThatThrownBy(() -> registrar.postProcessAfterInitialization(bean, "threeParams"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must have 1 or 2 parameters");
        }
    }

    @Nested
    @DisplayName("Subscription failure handling")
    class SubscriptionFailureTests {

        @Test
        @DisplayName("should handle subscription failure gracefully")
        void shouldHandleSubscriptionFailure() {
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            when(brokerStrategy.subscribe(any(SubscribeRequest.class)))
                    .thenThrow(new RuntimeException("Broker unavailable"));

            assertThatCode(() -> registrar.start()).doesNotThrowAnyException();
            assertThat(registrar.getActiveSubscriptions()).isEmpty();
        }

        @Test
        @DisplayName("should handle ensureConsumerGroup failure gracefully")
        void shouldHandleEnsureGroupFailure() {
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            doThrow(new RuntimeException("Group create failed"))
                    .when(brokerStrategy).ensureConsumerGroup("test-channel", "test-group");
            Subscription mockSub = mock(Subscription.class);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            assertThatCode(() -> registrar.start()).doesNotThrowAnyException();
            assertThat(registrar.getActiveSubscriptions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Stop with subscription cancel exception")
    class StopWithExceptionTests {

        @Test
        @DisplayName("should handle exception during subscription cancel on stop")
        void shouldHandleCancelException() {
            SampleHandler bean = new SampleHandler();
            when(environment.resolvePlaceholders("test-channel")).thenReturn("test-channel");
            when(environment.resolvePlaceholders("test-group")).thenReturn("test-group");
            registrar.postProcessAfterInitialization(bean, "sampleHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);
            doThrow(new RuntimeException("Cancel failed")).when(mockSub).cancel();
            when(mockSub.channel()).thenReturn("test-channel");
            when(mockSub.groupName()).thenReturn("test-group");
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();
            assertThatCode(() -> registrar.stop()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("should create multiple subscriptions for concurrency > 1")
        void shouldCreateMultipleSubscriptions() {
            ConcurrentHandler bean = new ConcurrentHandler();
            when(environment.resolvePlaceholders("conc-channel")).thenReturn("conc-channel");
            when(environment.resolvePlaceholders("conc-group")).thenReturn("conc-group");
            registrar.postProcessAfterInitialization(bean, "concurrentHandler");

            when(brokerStrategyProvider.getObject()).thenReturn(brokerStrategy);
            when(idempotentGuardProvider.getIfAvailable()).thenReturn(null);
            when(propertiesProvider.getIfAvailable()).thenReturn(null);
            registrar.afterSingletonsInstantiated();

            when(brokerStrategy.name()).thenReturn("local");
            Subscription mockSub = mock(Subscription.class);
            when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(mockSub);

            registrar.start();

            verify(brokerStrategy, times(3)).subscribe(any(SubscribeRequest.class));
            assertThat(registrar.getActiveSubscriptions()).hasSize(3);
        }
    }

    // --- Test beans ---

    static class SampleHandler {
        @MessageHandler(channel = "test-channel", group = "test-group")
        public void handle(Message<String> message) {
            // no-op
        }
    }

    static class TwoParamHandler {
        @MessageHandler(channel = "test-channel", group = "test-group")
        public void handle(Message<String> message, MessageAcknowledgment ack) {
            // no-op
        }
    }

    @SuppressWarnings("rawtypes")
    static class RawMessageHandler {
        @MessageHandler(channel = "raw-channel")
        public void handle(Message message) {
            // no-op - raw Message without generics
        }
    }

    static class ByteArrayHandler {
        @MessageHandler(channel = "bytes-channel")
        public void handle(Message<byte[]> message) {
            // no-op
        }
    }

    static class InvalidHandlerNoParams {
        @MessageHandler(channel = "ch")
        public void handle() {
            // invalid: no parameters
        }
    }

    static class InvalidHandlerWrongType {
        @MessageHandler(channel = "ch")
        public void handle(String notAMessage) {
            // invalid: first param is not Message<T>
        }
    }

    static class InvalidSecondParam {
        @MessageHandler(channel = "ch")
        public void handle(Message<String> message, String notAnAck) {
            // invalid: second param is not MessageAcknowledgment
        }
    }

    static class ThreeParamHandler {
        @MessageHandler(channel = "ch")
        public void handle(Message<String> message, MessageAcknowledgment ack, String extra) {
            // invalid: three parameters
        }
    }

    static class ConcurrentHandler {
        @MessageHandler(channel = "conc-channel", group = "conc-group", concurrency = 3)
        public void handle(Message<String> message) {
            // no-op
        }
    }
}
