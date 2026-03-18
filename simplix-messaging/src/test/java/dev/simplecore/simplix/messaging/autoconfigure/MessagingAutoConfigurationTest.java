package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessagePublisher;
import dev.simplecore.simplix.messaging.core.PublishResult;
import dev.simplecore.simplix.messaging.core.RetryPolicy;
import dev.simplecore.simplix.messaging.error.DeadLetterStrategy;
import dev.simplecore.simplix.messaging.error.PoisonMessageHandler;
import dev.simplecore.simplix.messaging.subscriber.MessageHandlerRegistrar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAutoConfiguration")
@ExtendWith(MockitoExtension.class)
class MessagingAutoConfigurationTest {

    @Nested
    @DisplayName("messagePublisher bean")
    class MessagePublisherBeanTests {

        @Test
        @DisplayName("should create DefaultMessagePublisher with broker strategy")
        void shouldCreatePublisher() {
            BrokerStrategy brokerStrategy = mock(BrokerStrategy.class);
            when(brokerStrategy.name()).thenReturn("local");

            MessagingAutoConfiguration config = new MessagingAutoConfiguration();
            MessagePublisher publisher = config.messagePublisher(brokerStrategy);

            assertThat(publisher).isNotNull();
        }
    }

    @Nested
    @DisplayName("retryPolicy bean")
    class RetryPolicyBeanTests {

        @Test
        @DisplayName("should create retry policy from properties")
        void shouldCreateRetryPolicy() {
            MessagingProperties properties = new MessagingProperties();
            properties.getError().setMaxRetries(5);
            properties.getError().setRetryBackoff(Duration.ofSeconds(2));

            MessagingAutoConfiguration config = new MessagingAutoConfiguration();
            RetryPolicy policy = config.retryPolicy(properties);

            assertThat(policy.maxRetries()).isEqualTo(5);
            assertThat(policy.initialBackoff()).isEqualTo(Duration.ofSeconds(2));
            assertThat(policy.backoffMultiplier()).isEqualTo(2.0);
            assertThat(policy.maxBackoff()).isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Nested
    @DisplayName("messageHandlerRegistrar bean")
    class RegistrarBeanTests {

        @Test
        @DisplayName("should create registrar with lazy providers")
        @SuppressWarnings("unchecked")
        void shouldCreateRegistrar() {
            ObjectProvider<BrokerStrategy> brokerProvider = mock(ObjectProvider.class);
            ObjectProvider<dev.simplecore.simplix.messaging.subscriber.IdempotentGuard> guardProvider =
                    mock(ObjectProvider.class);
            ObjectProvider<MessagingProperties> propsProvider = mock(ObjectProvider.class);
            Environment env = mock(Environment.class);

            MessageHandlerRegistrar registrar = MessagingAutoConfiguration.messageHandlerRegistrar(
                    brokerProvider, env, guardProvider, propsProvider);

            assertThat(registrar).isNotNull();
        }
    }

    @Nested
    @DisplayName("poisonMessageHandler bean")
    class PoisonMessageHandlerBeanTests {

        @Test
        @DisplayName("should create poison message handler")
        void shouldCreatePoisonHandler() {
            MessagingAutoConfiguration config = new MessagingAutoConfiguration();
            PoisonMessageHandler handler = config.poisonMessageHandler();

            assertThat(handler).isNotNull();
        }
    }

    @Nested
    @DisplayName("LocalMessagingConfiguration")
    class LocalConfigTests {

        @Test
        @DisplayName("should create local broker strategy")
        void shouldCreateLocalBroker() {
            MessagingAutoConfiguration.LocalMessagingConfiguration localConfig =
                    new MessagingAutoConfiguration.LocalMessagingConfiguration();

            var broker = localConfig.localBrokerStrategy();

            assertThat(broker).isNotNull();
            assertThat(broker.isReady()).isTrue();
            assertThat(broker.name()).isEqualTo("local");
        }
    }
}
