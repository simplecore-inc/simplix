package dev.simplecore.simplix.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.event.strategy.RabbitEventStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ event strategy configuration
 *
 * <p>IMPORTANT: This configuration class is only loaded when RabbitTemplate is on the classpath.
 * The strategy class ({@link RabbitEventStrategy}) will NOT be loaded if this condition is not met,
 * preventing ClassNotFoundException even though RabbitEventStrategy directly imports RabbitMQ classes.
 *
 * <p>Safety mechanism:
 * <ul>
 *   <li>Uses string-based @ConditionalOnClass to avoid eager class loading</li>
 *   <li>RabbitEventStrategy has no @Component annotation - not component scanned</li>
 *   <li>Created only via @Bean when this Configuration is loaded</li>
 *   <li>If RabbitMQ is missing, Configuration is not loaded, strategy is never referenced</li>
 * </ul>
 *
 * <p>Note: RabbitMQ infrastructure configuration (exchanges, queues, bindings) is managed
 * in {@link RabbitEventInfrastructureConfiguration}.
 *
 * @see dev.simplecore.simplix.event.strategy.RabbitEventStrategy
 * @see RabbitEventInfrastructureConfiguration
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class RabbitEventStrategyConfiguration {

    /**
     * Creates RabbitMQ event strategy bean for reliable message queuing
     *
     * <p>This strategy provides:
     * <ul>
     *   <li>Reliable message delivery with acknowledgments</li>
     *   <li>Dead letter queue support for failed messages</li>
     *   <li>Retry mechanism with exponential backoff</li>
     *   <li>Message priority and TTL configuration</li>
     *   <li>Both sync and async publishing modes</li>
     * </ul>
     *
     * @param rabbitTemplate RabbitMQ template for message publishing (optional)
     * @param objectMapper JSON serializer for event payloads (optional)
     * @param exchangeName Exchange name from configuration
     * @param routingKeyPrefix Routing key prefix from configuration
     * @param dlqEnabled Whether DLQ is enabled
     * @param dlqExchangeName DLQ exchange name from configuration
     * @param messageTtl Message TTL in milliseconds
     * @param maxRetries Maximum retry attempts
     * @param retryDelay Retry delay in milliseconds
     * @return RabbitMQ event strategy instance
     */
    @Bean
    @ConditionalOnMissingBean(RabbitEventStrategy.class)
    public RabbitEventStrategy rabbitEventStrategy(
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${simplix.events.rabbit.exchange:simplix.events}") String exchangeName,
            @Value("${simplix.events.rabbit.routing-key-prefix:event.}") String routingKeyPrefix,
            @Value("${simplix.events.rabbit.dlq.enabled:true}") boolean dlqEnabled,
            @Value("${simplix.events.rabbit.dlq.exchange:simplix.events.dlq}") String dlqExchangeName,
            @Value("${simplix.events.rabbit.ttl:86400000}") long messageTtl,
            @Value("${simplix.events.rabbit.max-retries:3}") int maxRetries,
            @Value("${simplix.events.rabbit.retry-delay:1000}") long retryDelay) {
        log.debug("Registering RabbitEventStrategy");

        RabbitEventStrategy strategy = new RabbitEventStrategy();
        strategy.setRabbitTemplate(rabbitTemplate);
        strategy.setObjectMapper(objectMapper);
        strategy.setExchangeName(exchangeName);
        strategy.setRoutingKeyPrefix(routingKeyPrefix);
        strategy.setDlqEnabled(dlqEnabled);
        strategy.setDlqExchangeName(dlqExchangeName);
        strategy.setMessageTtl(messageTtl);
        strategy.setMaxRetries(maxRetries);
        strategy.setRetryDelay(retryDelay);

        // Initialize the strategy
        strategy.initialize();

        return strategy;
    }
}
