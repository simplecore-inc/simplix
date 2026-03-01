package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.local.LocalBrokerStrategy;
import dev.simplecore.simplix.messaging.core.DefaultMessagePublisher;
import dev.simplecore.simplix.messaging.core.MessagePublisher;
import dev.simplecore.simplix.messaging.core.RetryPolicy;
import dev.simplecore.simplix.messaging.error.DeadLetterStrategy;
import dev.simplecore.simplix.messaging.error.PoisonMessageHandler;
import dev.simplecore.simplix.messaging.monitoring.MessagingHealthIndicator;
import dev.simplecore.simplix.messaging.monitoring.MessagingMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import dev.simplecore.simplix.messaging.subscriber.IdempotentGuard;
import dev.simplecore.simplix.messaging.subscriber.MessageHandlerRegistrar;

import java.time.Duration;
import java.util.Optional;

/**
 * Auto-configuration for the simplix-messaging module.
 *
 * <p>Activates the appropriate broker strategy based on the {@code simplix.messaging.broker}
 * property and registers the core messaging infrastructure beans.
 *
 * <p>Supported brokers:
 * <ul>
 *   <li>{@code local} (default) - in-memory messaging for single-instance deployments</li>
 *   <li>{@code redis} - Redis Streams-based messaging for distributed deployments</li>
 *   <li>{@code kafka} - Apache Kafka-based messaging for high-throughput scenarios</li>
 *   <li>{@code rabbit} - RabbitMQ-based messaging with dead letter support</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(MessagingProperties.class)
@Import({RedisMessagingConfiguration.class, KafkaMessagingConfiguration.class, RabbitMessagingConfiguration.class})
@Slf4j
public class MessagingAutoConfiguration {

    /**
     * Default message publisher that delegates to the active broker strategy.
     *
     * @param brokerStrategy the active broker strategy
     * @return the message publisher
     */
    @Bean
    public MessagePublisher messagePublisher(BrokerStrategy brokerStrategy) {
        log.info("Initializing MessagePublisher with broker: {}", brokerStrategy.name());
        return new DefaultMessagePublisher(brokerStrategy);
    }

    /**
     * Retry policy derived from error configuration properties.
     *
     * @param properties the messaging properties
     * @return the retry policy
     */
    @Bean
    public RetryPolicy retryPolicy(MessagingProperties properties) {
        MessagingProperties.ErrorProperties error = properties.getError();
        return new RetryPolicy(
                error.getMaxRetries(),
                error.getRetryBackoff(),
                2.0,
                Duration.ofSeconds(30)
        );
    }

    /**
     * Registrar that scans for {@code @MessageHandler} annotated methods
     * and binds them to the broker's subscription mechanism.
     *
     * @param brokerStrategy  the active broker strategy
     * @param environment     the Spring environment for property placeholder resolution
     * @param idempotentGuard optional idempotent guard (present only when Redis broker is active)
     * @param properties      the messaging properties
     * @return the message handler registrar
     */
    @Bean
    public MessageHandlerRegistrar messageHandlerRegistrar(BrokerStrategy brokerStrategy,
                                                           Environment environment,
                                                           Optional<IdempotentGuard> idempotentGuard,
                                                           MessagingProperties properties) {
        return new MessageHandlerRegistrar(brokerStrategy, environment, idempotentGuard, properties);
    }

    // ---------------------------------------------------------------
    // Error handling configuration
    // ---------------------------------------------------------------

    /**
     * Dead letter strategy for routing failed messages to DLQ channels.
     * Activated when {@code simplix.messaging.error.dead-letter.enabled=true}.
     *
     * @param brokerStrategy the active broker strategy
     * @return the dead letter strategy
     */
    @Bean
    @ConditionalOnProperty(prefix = "simplix.messaging.error.dead-letter", name = "enabled", havingValue = "true")
    public DeadLetterStrategy deadLetterStrategy(BrokerStrategy brokerStrategy) {
        log.info("Dead letter queue routing enabled");
        return new DeadLetterStrategy(brokerStrategy);
    }

    /**
     * Poison message handler for detecting and handling unrecoverable message failures.
     *
     * @return the poison message handler
     */
    @Bean
    @ConditionalOnMissingBean
    public PoisonMessageHandler poisonMessageHandler() {
        return new PoisonMessageHandler();
    }

    // ---------------------------------------------------------------
    // Local broker configuration (default)
    // ---------------------------------------------------------------

    /**
     * Local in-memory broker configuration.
     * Active when broker is set to "local" or not configured (matchIfMissing = true).
     */
    @Configuration
    @ConditionalOnProperty(prefix = "simplix.messaging", name = "broker", havingValue = "local", matchIfMissing = true)
    @Slf4j
    static class LocalMessagingConfiguration {

        @Bean
        public LocalBrokerStrategy localBrokerStrategy() {
            log.info("Activating local in-memory broker strategy");
            LocalBrokerStrategy strategy = new LocalBrokerStrategy();
            strategy.initialize();
            return strategy;
        }
    }

    // ---------------------------------------------------------------
    // Monitoring configuration (conditional on classpath)
    // ---------------------------------------------------------------

    /**
     * Micrometer metrics configuration.
     * Activated when Micrometer is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @Slf4j
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public MessagingMetrics messagingMetrics(
                Optional<io.micrometer.core.instrument.MeterRegistry> meterRegistry) {
            log.info("Configuring messaging metrics (Micrometer detected)");
            return new MessagingMetrics(meterRegistry.orElse(null));
        }
    }

    /**
     * Spring Boot Actuator health indicator configuration.
     * Activated when Spring Boot Actuator is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    @Slf4j
    static class HealthIndicatorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public MessagingHealthIndicator messagingHealthIndicator(BrokerStrategy brokerStrategy) {
            log.info("Configuring messaging health indicator (Actuator detected)");
            return new MessagingHealthIndicator(brokerStrategy);
        }
    }
}
