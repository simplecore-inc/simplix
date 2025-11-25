package dev.simplecore.simplix.event.strategy;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka event strategy for high-throughput distributed event streaming
 * This strategy publishes events to Kafka topics for scalable event processing
 */
@Slf4j
public class KafkaEventStrategy implements EventStrategy {

    @Setter
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Setter
    private String topicPrefix = "simplix-events";
    @Setter
    private String defaultTopic = "domain-events";

    private volatile boolean ready = false;

    @Override
    public void publish(Event event, PublishOptions options) {
        if (!isReady()) {
            throw new IllegalStateException("Kafka event strategy is not ready");
        }

        try {
            String topic = buildTopicName(event, options);
            String key = options.getPartitionKey() != null ?
                options.getPartitionKey() : event.getAggregateId();

            log.debug("Publishing event to Kafka topic {}: {}", topic, event.getEventId());

            ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);

            // Add headers if provided with UTF-8 encoding
            if (options.getHeaders() != null) {
                options.getHeaders().forEach((k, v) ->
                    record.headers().add(k, v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                );
            }

            if (options.isAsync()) {
                publishAsync(record, event, options);
                log.trace("Event sent asynchronously to Kafka: {}", event.getEventId());
            } else {
                publishSync(record, event, options);
                log.trace("Event sent synchronously to Kafka: {}", event.getEventId());
            }
        } catch (Exception e) {
            handlePublishError(event, options, e);
        }
    }

    private void publishAsync(ProducerRecord<String, Object> record,
                             Event event,
                             PublishOptions options) {
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(record);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Async publish failed for event: {}", event.getEventId(), ex);
                if (options.isCritical()) {
                    // Could implement retry logic here
                    retryPublish(record, event, options, 1);
                }
            } else {
                log.trace("Async publish succeeded for event: {}", event.getEventId());
            }
        });
    }

    private void publishSync(ProducerRecord<String, Object> record,
                           Event event,
                           PublishOptions options) {
        try {
            // Add 30-second timeout to prevent indefinite blocking
            SendResult<String, Object> result = kafkaTemplate.send(record)
                .get(30, TimeUnit.SECONDS);
            log.trace("Sync publish succeeded for event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Sync publish failed for event: {}", event.getEventId(), e);
            if (options.isCritical() && options.getMaxRetries() > 0) {
                retryPublish(record, event, options, 1);
            } else {
                throw new KafkaPublishException("Failed to publish event to Kafka", e);
            }
        }
    }

    private void retryPublish(ProducerRecord<String, Object> record,
                             Event event,
                             PublishOptions options,
                             int attempt) {
        if (attempt > options.getMaxRetries()) {
            log.error("Max retries exceeded for event: {}", event.getEventId());
            if (options.isCritical()) {
                throw new KafkaPublishException(
                    "Failed to publish critical event after " + options.getMaxRetries() + " attempts");
            }
            return;
        }

        // Calculate exponential backoff delay
        long delayMillis = options.getRetryDelay().toMillis() * attempt;

        // Schedule retry with proper future chaining to avoid accumulation
        CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS)
            .execute(() -> {
                try {
                    // Synchronous send with timeout for retry
                    kafkaTemplate.send(record).get(30, TimeUnit.SECONDS);
                    log.info("Retry successful for event: {} on attempt: {}", event.getEventId(), attempt);
                } catch (Exception ex) {
                    log.warn("Retry {} failed for event: {}", attempt, event.getEventId(), ex);
                    if (attempt < options.getMaxRetries()) {
                        retryPublish(record, event, options, attempt + 1);
                    } else {
                        log.error("All retries exhausted for event: {}", event.getEventId());
                        if (options.isCritical()) {
                            throw new KafkaPublishException(
                                "Failed to publish critical event after " + options.getMaxRetries() + " attempts", ex);
                        }
                    }
                }
            });
    }

    private String buildTopicName(Event event, PublishOptions options) {
        if (options.getRoutingKey() != null) {
            return String.format("%s-%s", topicPrefix, options.getRoutingKey());
        }
        // Use event type for better topic separation
        return String.format("%s-%s", topicPrefix, String.valueOf(event.getEventType()).toLowerCase());
    }

    private void handlePublishError(Event event, PublishOptions options, Exception e) {
        log.error("Failed to publish event to Kafka: {}", event.getEventId(), e);

        if (options.isCritical()) {
            throw new KafkaPublishException("Failed to publish critical event to Kafka", e);
        }

        log.warn("Non-critical event publish failed, continuing: {}", event.getEventId());
    }

    @Override
    public boolean supports(String mode) {
        return "kafka".equalsIgnoreCase(mode);
    }

    @Override
    public void initialize() {
        log.info("Initializing Kafka Event Strategy");

        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available, Kafka strategy will be disabled");
            this.ready = false;
            return;
        }

        // Mark as ready immediately - actual connectivity will be verified on first publish
        // This prevents application startup delays due to Kafka unavailability
        this.ready = true;
        log.info("Kafka Event Strategy initialized successfully (lazy connection verification)");
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Kafka Event Strategy");
        this.ready = false;
        if (kafkaTemplate != null) {
            kafkaTemplate.flush();
        }
    }

    @Override
    public boolean isReady() {
        return ready && kafkaTemplate != null;
    }

    @Override
    public String getName() {
        return "KafkaEventStrategy";
    }

    /**
     * Custom exception for Kafka publishing failures
     */
    public static class KafkaPublishException extends RuntimeException {
        public KafkaPublishException(String message) {
            super(message);
        }

        public KafkaPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}