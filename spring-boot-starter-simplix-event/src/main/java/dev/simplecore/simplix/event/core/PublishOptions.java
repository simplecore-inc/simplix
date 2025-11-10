package dev.simplecore.simplix.event.core;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Options for publishing events
 * Provides fine-grained control over how events are published
 */
@Data
@Builder
public class PublishOptions {

    /**
     * Whether to persist the event to database for reliability
     */
    @Builder.Default
    private boolean persistent = false;

    /**
     * Whether this is a critical event that must be delivered
     */
    @Builder.Default
    private boolean critical = false;

    /**
     * Time-to-live for the event (null means no expiry)
     */
    private Duration ttl;

    /**
     * Routing key for message routing (used in AMQP/Kafka)
     */
    private String routingKey;

    /**
     * Partition key for partitioned systems (Kafka)
     */
    private String partitionKey;

    /**
     * Whether to publish asynchronously
     */
    @Builder.Default
    private boolean async = true;

    /**
     * Maximum retry attempts for failed publishes
     */
    @Builder.Default
    private int maxRetries = 3;

    /**
     * Delay between retry attempts
     */
    @Builder.Default
    private Duration retryDelay = Duration.ofSeconds(1);

    /**
     * Custom headers/metadata for the event
     */
    private java.util.Map<String, Object> headers;

    /**
     * Create default publish options
     */
    public static PublishOptions defaults() {
        return PublishOptions.builder().build();
    }

    /**
     * Create options for critical events
     */
    public static PublishOptions critical() {
        return PublishOptions.builder()
            .critical(true)
            .persistent(true)
            .maxRetries(5)
            .build();
    }

    /**
     * Create options for non-critical, fire-and-forget events
     */
    public static PublishOptions fireAndForget() {
        return PublishOptions.builder()
            .critical(false)
            .persistent(false)
            .async(true)
            .maxRetries(0)
            .build();
    }
}