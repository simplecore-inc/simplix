package dev.simplecore.simplix.event.properties;

import dev.simplecore.simplix.event.constant.SimpliXEventConstants;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Configuration properties for the SimpliX Event system.
 * This class contains all configurable properties for different message brokers.
 */
@ConfigurationProperties(prefix = "simplix.event")
@Getter @Setter
@Validated
public class SimpliXEventProperties {
    /** Message queue type: in-memory, rabbitmq, kafka, nats */
    @NotNull
    private String mqType = "in-memory";
    
    /** Channel type: direct, executor, pubsub */
    @NotNull
    private String channelType = "direct";
    
    /** Default outbound channel name */
    @NotNull
    private String outboundChannel = SimpliXEventConstants.DEFAULT_OUTBOUND_CHANNEL;
    
    /** Default error channel name */
    @NotNull
    private String errorChannel = SimpliXEventConstants.DEFAULT_ERROR_CHANNEL;
    
    /** Default channel name when no specific channel is specified */
    @NotNull
    private String defaultChannelName = SimpliXEventConstants.DEFAULT_CHANNEL_NAME;
    
    /** Retry mechanism configuration */
    @Valid
    private RetryProperties retry = new RetryProperties();
    
    /** NATS message broker specific configuration */
    @Valid
    private NatsProperties nats = new NatsProperties();
    
    /** RabbitMQ message broker specific configuration */
    @Valid
    private RabbitProperties rabbit = new RabbitProperties();
    
    /** Kafka message broker specific configuration */
    @Valid
    private KafkaProperties kafka = new KafkaProperties();
    
    /** In-memory message broker specific configuration */
    @Valid
    private InMemoryProperties inMemory = new InMemoryProperties();
    
    /**
     * Retry mechanism configuration properties.
     */
    @Getter @Setter
    public static class RetryProperties {
        /** Maximum number of retry attempts */
        private int maxAttempts = 3;
        /** Initial backoff interval in milliseconds */
        private long backoffInitialInterval = 1000;
        /** Backoff multiplier for exponential backoff */
        private double backoffMultiplier = 2.0;
    }

    /**
     * NATS message broker specific configuration properties.
     */
    @Getter @Setter
    public static class NatsProperties {
        /** Default subject pattern for subscriptions */
        private String subject = "simplix.>";
    }

    /**
     * RabbitMQ message broker specific configuration properties.
     */
    @Getter @Setter
    public static class RabbitProperties {
        /** Default queue name */
        private String queueName = "simplix.events";
        /** Default exchange name */
        private String exchangeName = SimpliXEventConstants.DEFAULT_EXCHANGE_NAME;
    }

    /**
     * Kafka message broker specific configuration properties.
     */
    @Getter @Setter
    public static class KafkaProperties {
        /** Consumer group ID */
        private String groupId = "simplix-event-group";
        /** Topic pattern for subscriptions */
        private String topicPattern = "simplix.*";
        /** Default topic prefix */
        private String topicPrefix = "simplix.event.";
    }

    /**
     * In-memory message broker specific configuration properties.
     */
    @Getter @Setter
    public static class InMemoryProperties {
        /** Message expiration time in milliseconds */
        private long messageExpiryMs = 3600000;
        /** Cleanup interval in milliseconds */
        private long cleanupIntervalMs = 60000;
    }
} 