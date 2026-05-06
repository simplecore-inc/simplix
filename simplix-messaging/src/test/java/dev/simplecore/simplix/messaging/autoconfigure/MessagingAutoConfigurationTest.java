package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessagePublisher;
import dev.simplecore.simplix.messaging.core.PublishResult;
import dev.simplecore.simplix.messaging.core.RetryPolicy;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import dev.simplecore.simplix.messaging.error.DeadLetterStrategy;
import dev.simplecore.simplix.messaging.error.PoisonMessageHandler;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import dev.simplecore.simplix.messaging.subscriber.MessageHandlerRegistrar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("MessagingAutoConfiguration")
@ExtendWith(MockitoExtension.class)
class MessagingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MessagingAutoConfiguration.class);

    @Test
    @DisplayName("local broker registers ReplayService, MessageScheduler, and IdempotencyStore SPI beans")
    void localBroker_registersReplayScheduledAndIdempotencySpi() {
        contextRunner.withPropertyValues("simplix.messaging.broker=local")
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(ReplayService.class);
                assertThat(ctx).hasSingleBean(MessageScheduler.class);
                assertThat(ctx).hasSingleBean(IdempotencyStore.class);
            });
    }

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
            ObjectProvider<IdempotencyStore> storeProvider = mock(ObjectProvider.class);
            ObjectProvider<MessagingProperties> propsProvider = mock(ObjectProvider.class);
            Environment env = mock(Environment.class);

            MessageHandlerRegistrar registrar = MessagingAutoConfiguration.messageHandlerRegistrar(
                    brokerProvider, env, storeProvider, propsProvider);

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

    @Test
    @DisplayName("NatsMessagingConfiguration is included in the @Import annotation of MessagingAutoConfiguration")
    void natsBroker_wiringIsImportedFromMessagingAutoConfiguration() {
        contextRunner.withPropertyValues(
                "simplix.messaging.broker=nats",
                "simplix.messaging.nats.servers=nats://app:apppass@localhost:4222"
            )
            .run(ctx -> {
                // Verify the NatsMessagingConfiguration is part of the @Import chain.
                // We don't actually start a NATS connection in this test (no Connection bean
                // mocked), so the context should fail bean creation if the import is wired
                // (proving the @Import works), or succeed if import is missing (proving import is missing).
                // Use ctx.hasFailed() and inspect the failure trigger to verify NatsMessagingConfiguration
                // contributed beans.
                // Simpler: assert the config class itself is on the import list via reflection.
                org.springframework.context.annotation.Import importAnno =
                    dev.simplecore.simplix.messaging.autoconfigure.MessagingAutoConfiguration.class
                        .getAnnotation(org.springframework.context.annotation.Import.class);
                org.assertj.core.api.Assertions.assertThat(importAnno).isNotNull();
                org.assertj.core.api.Assertions.assertThat(importAnno.value())
                    .contains(dev.simplecore.simplix.messaging.autoconfigure.NatsMessagingConfiguration.class);
            });
    }
}
