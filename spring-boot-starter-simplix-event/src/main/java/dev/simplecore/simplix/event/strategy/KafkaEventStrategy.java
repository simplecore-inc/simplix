package dev.simplecore.simplix.event.strategy;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.core.PublishOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka event strategy for high-throughput distributed event streaming
 * This strategy publishes events to Kafka topics for scalable event processing
 */
@Component
@ConditionalOnClass(KafkaTemplate.class)
@Slf4j
public class KafkaEventStrategy implements EventStrategy {

    @Autowired(required = false)
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${simplix.events.kafka.topic-prefix:simplix-events}")
    private String topicPrefix;

    @Value("${simplix.events.kafka.default-topic:domain-events}")
    private String defaultTopic;

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

            // Add headers if provided
            if (options.getHeaders() != null) {
                options.getHeaders().forEach((k, v) ->
                    record.headers().add(k, v.toString().getBytes())
                );
            }

            if (options.isAsync()) {
                publishAsync(record, event, options);
            } else {
                publishSync(record, event, options);
            }

            log.trace("Successfully published event to Kafka: {}", event.getEventId());
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
            SendResult<String, Object> result = kafkaTemplate.send(record).get();
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

        try {
            Thread.sleep(options.getRetryDelay().toMillis() * attempt);
            kafkaTemplate.send(record).get();
            log.info("Retry successful for event: {} on attempt: {}", event.getEventId(), attempt);
        } catch (Exception e) {
            log.warn("Retry {} failed for event: {}", attempt, event.getEventId(), e);
            retryPublish(record, event, options, attempt + 1);
        }
    }

    private String buildTopicName(Event event, PublishOptions options) {
        if (options.getRoutingKey() != null) {
            return String.format("%s-%s", topicPrefix, options.getRoutingKey());
        }
        return String.format("%s-%s", topicPrefix, defaultTopic);
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

        // Test Kafka connection by checking if default topic exists
        try {
            kafkaTemplate.partitionsFor(String.format("%s-%s", topicPrefix, defaultTopic));
            this.ready = true;
            log.info("Kafka Event Strategy initialized successfully");
        } catch (Exception e) {
            log.error("Failed to connect to Kafka", e);
            this.ready = false;
        }
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