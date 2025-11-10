package dev.simplecore.simplix.event.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ-based event publishing strategy
 * Provides reliable message queuing with dead letter queue support
 */
@Slf4j
@Component
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(value = "simplix.events.mode", havingValue = "rabbit")
public class RabbitEventStrategy implements EventStrategy {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public RabbitEventStrategy() {
        // Default constructor for Spring
    }

    public RabbitEventStrategy(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${simplix.events.rabbit.exchange:simplix.events}")
    private String exchangeName;

    @Value("${simplix.events.rabbit.routing-key-prefix:event.}")
    private String routingKeyPrefix;

    @Value("${simplix.events.rabbit.dlq.enabled:true}")
    private boolean dlqEnabled;

    @Value("${simplix.events.rabbit.dlq.exchange:simplix.events.dlq}")
    private String dlqExchangeName;

    @Value("${simplix.events.rabbit.ttl:86400000}") // 24 hours default
    private long messageTtl;

    @Value("${simplix.events.rabbit.max-retries:3}")
    private int maxRetries;

    @Value("${simplix.events.rabbit.retry-delay:1000}")
    private long retryDelay;

    private volatile boolean initialized = false;
    private RetryTemplate retryTemplate;

    @PostConstruct
    @Override
    public void initialize() {
        try {
            // Configure retry template
            retryTemplate = new RetryTemplate();

            SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
            retryPolicy.setMaxAttempts(maxRetries);
            retryTemplate.setRetryPolicy(retryPolicy);

            ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
            backOffPolicy.setInitialInterval(retryDelay);
            backOffPolicy.setMultiplier(2.0);
            backOffPolicy.setMaxInterval(30000); // Max 30 seconds
            retryTemplate.setBackOffPolicy(backOffPolicy);

            // Set message converter
            rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));

            initialized = true;
            log.info("RabbitMQ event strategy initialized with exchange: {}", exchangeName);
        } catch (Exception e) {
            log.error("Failed to initialize RabbitMQ event strategy", e);
            initialized = false;
        }
    }

    @PreDestroy
    @Override
    public void shutdown() {
        log.info("Shutting down RabbitMQ event strategy");
        initialized = false;
    }

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!initialized) {
            log.warn("RabbitMQ strategy not initialized, cannot publish event: {}", event.getEventId());
            return;
        }

        String routingKey = buildRoutingKey(event);

        try {
            if (options.isAsync()) {
                publishAsync(event, routingKey, options);
            } else {
                publishSync(event, routingKey, options);
            }
        } catch (Exception e) {
            handlePublishError(event, options, e);
        }
    }

    private void publishSync(Event event, String routingKey, PublishOptions options) {
        retryTemplate.execute(context -> {
            MessageProperties properties = createMessageProperties(event, options);
            Message message = rabbitTemplate.getMessageConverter()
                .toMessage(event, properties);

            rabbitTemplate.send(exchangeName, routingKey, message);

            log.debug("Event published to RabbitMQ - Exchange: {}, RoutingKey: {}, EventId: {}",
                exchangeName, routingKey, event.getEventId());
            return null;
        });
    }

    private void publishAsync(Event event, String routingKey, PublishOptions options) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, event, message -> {
            MessageProperties props = message.getMessageProperties();
            configureMessageProperties(props, event, options);
            return message;
        });

        log.debug("Event published asynchronously to RabbitMQ - EventId: {}", event.getEventId());
    }

    private MessageProperties createMessageProperties(Event event, PublishOptions options) {
        MessageProperties properties = new MessageProperties();
        configureMessageProperties(properties, event, options);
        return properties;
    }

    private void configureMessageProperties(MessageProperties properties,
                                           Event event,
                                           PublishOptions options) {
        // Set message ID
        properties.setMessageId(event.getEventId());

        // Set content type
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        // Set TTL if specified
        if (messageTtl > 0) {
            properties.setExpiration(String.valueOf(messageTtl));
        }

        // Set priority for critical messages
        if (options.isCritical()) {
            properties.setPriority(9); // Highest priority
        } else {
            properties.setPriority(5); // Normal priority
        }

        // Set persistence
        if (options.isPersistent()) {
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        } else {
            properties.setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
        }

        // Add custom headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("eventType", event.getEventType().toString());
        headers.put("aggregateId", event.getAggregateId());
        headers.put("aggregateType", event.getAggregateId());
        headers.put("occurredAt", event.getOccurredAt() != null ?
            event.getOccurredAt().toString() : "");

        if (event.getMetadata() != null) {
            headers.putAll(event.getMetadata());
        }

        properties.setHeaders(headers);
    }

    private String buildRoutingKey(Event event) {
        // Build routing key based on event type and aggregate type
        // Format: event.{aggregateType}.{eventType}
        return String.format("%s%s.%s",
            routingKeyPrefix,
            event.getAggregateId().toLowerCase(),
            event.getEventType().toString().toLowerCase());
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event to RabbitMQ: {}", event.getEventId(), e);

        if (options.isCritical()) {
            throw new RuntimeException("Failed to publish critical event to RabbitMQ", e);
        }

        // For non-critical events, optionally send to DLQ
        if (dlqEnabled) {
            try {
                sendToDeadLetterQueue(event, e);
            } catch (Exception dlqError) {
                log.error("Failed to send event to dead letter queue: {}", event.getEventId(), dlqError);
            }
        }
    }

    private void sendToDeadLetterQueue(Event event, Exception originalError) {
        String dlqRoutingKey = "dlq." + buildRoutingKey(event);

        rabbitTemplate.convertAndSend(dlqExchangeName, dlqRoutingKey, event, message -> {
            MessageProperties props = message.getMessageProperties();
            props.setHeader("x-original-error", originalError.getMessage());
            props.setHeader("x-failed-at", System.currentTimeMillis());
            props.setHeader("x-original-exchange", exchangeName);
            return message;
        });

        log.info("Event sent to dead letter queue: {}", event.getEventId());
    }

    @Override
    public boolean supports(String mode) {
        return "rabbit".equalsIgnoreCase(mode) || "rabbitmq".equalsIgnoreCase(mode);
    }

    @Override
    public boolean isReady() {
        if (!initialized) {
            return false;
        }

        try {
            // Check connection is alive
            rabbitTemplate.getConnectionFactory().createConnection().isOpen();
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ connection check failed", e);
            return false;
        }
    }

    @Override
    public String getName() {
        return "RabbitMQ";
    }

    /**
     * RabbitMQ configuration for event publishing
     */
    @Configuration
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(value = "simplix.events.mode", havingValue = "rabbit")
    public static class RabbitEventConfiguration {

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

        @Bean
        public TopicExchange eventExchange() {
            return new TopicExchange(exchangeName, true, false);
        }

        @Bean
        public Queue eventQueue() {
            Map<String, Object> args = new HashMap<>();

            if (dlqEnabled) {
                args.put("x-dead-letter-exchange", dlqExchangeName);
                args.put("x-dead-letter-routing-key", "dlq.#");
            }

            return new Queue(queueName, true, false, false, args);
        }

        @Bean
        public Binding eventBinding(Queue eventQueue, TopicExchange eventExchange) {
            return BindingBuilder
                .bind(eventQueue)
                .to(eventExchange)
                .with("event.#");
        }

        @Bean
        @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
        public TopicExchange deadLetterExchange() {
            return new TopicExchange(dlqExchangeName, true, false);
        }

        @Bean
        @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
        public Queue deadLetterQueue() {
            return new Queue(dlqQueueName, true);
        }

        @Bean
        @ConditionalOnProperty(value = "simplix.events.rabbit.dlq.enabled", havingValue = "true")
        public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
            return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with("dlq.#");
        }

        @Bean
        public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
            return new Jackson2JsonMessageConverter(objectMapper);
        }
    }
}