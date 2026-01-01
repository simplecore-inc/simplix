package dev.simplecore.simplix.event.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RabbitMQ-based event publishing strategy
 * Provides reliable message queuing with dead letter queue support
 */
@Slf4j
public class RabbitEventStrategy implements EventStrategy {

    @Setter
    private RabbitTemplate rabbitTemplate;
    @Setter
    private ObjectMapper objectMapper;
    @Setter
    private String exchangeName = "simplix.events";
    @Setter
    private String routingKeyPrefix = "event.";
    @Setter
    private boolean dlqEnabled = true;
    @Setter
    private String dlqExchangeName = "simplix.events.dlq";
    @Setter
    private long messageTtl = 86400000L; // 24 hours default
    @Setter
    private int maxRetries = 3;
    @Setter
    private long retryDelay = 1000L;

    private volatile boolean ready = false;
    private RetryTemplate retryTemplate;

    @Override
    public void initialize() {
        log.info("Initializing RabbitMQ Event Strategy");

        if (rabbitTemplate == null) {
            log.warn("RabbitTemplate not available, RabbitMQ strategy will be disabled");
            this.ready = false;
            return;
        }

        if (objectMapper == null) {
            log.warn("ObjectMapper not available, creating default instance");
            this.objectMapper = new ObjectMapper();
            this.objectMapper.findAndRegisterModules();
        }
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

            this.ready = true;
            log.info("RabbitMQ Event Strategy initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize RabbitMQ event strategy", e);
            this.ready = false;
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down RabbitMQ Event Strategy");
        this.ready = false;
    }

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!isReady()) {
            throw new IllegalStateException("RabbitMQ event strategy is not ready");
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

            log.trace("Event published to RabbitMQ - Exchange: {}, RoutingKey: {}, EventId: {}",
                exchangeName, routingKey, event.getEventId());
            return null;
        });
    }

    private void publishAsync(Event event, String routingKey, PublishOptions options) {
        // Truly asynchronous publish using CompletableFuture with retry support
        publishAsyncWithRetry(event, routingKey, options, 0);
    }

    private void publishAsyncWithRetry(Event event, String routingKey, PublishOptions options, int attempt) {
        CompletableFuture.runAsync(() -> {
            try {
                rabbitTemplate.convertAndSend(exchangeName, routingKey, event, message -> {
                    MessageProperties props = message.getMessageProperties();
                    configureMessageProperties(props, event, options);
                    return message;
                });
                log.trace("Event published asynchronously to RabbitMQ - EventId: {} (attempt: {})",
                    event.getEventId(), attempt + 1);
            } catch (Exception e) {
                log.error("Failed to asynchronously publish event to RabbitMQ: {} (attempt: {})",
                    event.getEventId(), attempt + 1, e);

                // Retry if configured
                if (attempt < maxRetries && options.isCritical()) {
                    long delay = (long) (retryDelay * Math.pow(2, attempt));
                    log.info("Retrying async publish for event: {} after {}ms", event.getEventId(), delay);

                    // Use delayedExecutor to avoid blocking ForkJoinPool threads
                    java.util.concurrent.CompletableFuture.delayedExecutor(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> publishAsyncWithRetry(event, routingKey, options, attempt + 1));
                }
            }
        });
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
        headers.put("occurredAt", event.getOccurredAt() != null ?
            event.getOccurredAt().toString() : "");

        // Add headers from options (already enriched by UnifiedEventPublisher if enabled)
        if (options.getHeaders() != null) {
            headers.putAll(options.getHeaders());
        }

        properties.setHeaders(headers);
    }

    private String buildRoutingKey(Event event) {
        // Build routing key based on event type
        // Format: event.{aggregateId}.{eventType}
        String aggregateId = event.getAggregateId() != null
            ? event.getAggregateId().toLowerCase()
            : "unknown";
        return String.format("%s%s.%s",
            routingKeyPrefix,
            aggregateId,
            event.getEventType().toString().toLowerCase());
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event to RabbitMQ: {}", event.getEventId(), e);

        if (options.isCritical()) {
            throw new RabbitPublishException("Failed to publish critical event to RabbitMQ", e);
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
        return ready && rabbitTemplate != null;
    }

    @Override
    public String getName() {
        return "RabbitEventStrategy";
    }

    /**
     * Custom exception for RabbitMQ publishing failures
     */
    public static class RabbitPublishException extends RuntimeException {
        public RabbitPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}