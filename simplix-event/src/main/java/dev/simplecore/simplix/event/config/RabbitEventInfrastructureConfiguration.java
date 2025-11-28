package dev.simplecore.simplix.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ infrastructure configuration for event publishing
 *
 * <p>IMPORTANT: This configuration class is only loaded when RabbitTemplate is on the classpath.
 * Uses string-based @ConditionalOnClass to avoid eager class loading and prevent
 * ClassNotFoundException when RabbitMQ dependencies are not present.
 *
 * <p>Configures:
 * <ul>
 *   <li>Topic exchanges for event routing</li>
 *   <li>Queues with dead letter queue support</li>
 *   <li>Bindings for event routing patterns</li>
 *   <li>JSON message converter for serialization</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.event.strategy.RabbitEventStrategy
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@ConditionalOnProperty(value = "simplix.events.mode", havingValue = "rabbit")
public class RabbitEventInfrastructureConfiguration {

    @Value("${simplix.events.rabbit.exchange:simplix.events}")
    private String exchangeName;

    @Value("${simplix.events.rabbit.queue:simplix.events.queue}")
    private String queueName;

    @Value("${simplix.events.rabbit.dlq.exchange:simplix.events.dlq}")
    private String dlqExchangeName;

    @Value("${simplix.events.rabbit.dlq.queue:simplix.events.dlq.queue}")
    private String dlqQueueName;

    @Value("${simplix.events.rabbit.dlq.enabled:true}")
    private boolean dlqEnabled;

    /**
     * Main topic exchange for event publishing
     */
    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    /**
     * Main event queue with optional dead letter exchange configuration
     */
    @Bean
    public Queue eventQueue() {
        Map<String, Object> args = new HashMap<>();

        if (dlqEnabled) {
            args.put("x-dead-letter-exchange", dlqExchangeName);
            args.put("x-dead-letter-routing-key", "dlq.#");
        }

        return new Queue(queueName, true, false, false, args);
    }

    /**
     * Binding from event queue to event exchange with routing pattern
     */
    @Bean
    public Binding eventBinding(Queue eventQueue, TopicExchange eventExchange) {
        return BindingBuilder
            .bind(eventQueue)
            .to(eventExchange)
            .with("event.#");
    }

    /**
     * Dead letter exchange for failed messages
     */
    @Bean
    @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(dlqExchangeName, true, false);
    }

    /**
     * Dead letter queue for failed messages
     */
    @Bean
    @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
    public Queue deadLetterQueue() {
        return new Queue(dlqQueueName, true);
    }

    /**
     * Binding from dead letter queue to dead letter exchange
     */
    @Bean
    @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder
            .bind(deadLetterQueue)
            .to(deadLetterExchange)
            .with("dlq.#");
    }

    /**
     * JSON message converter for event serialization
     */
    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
