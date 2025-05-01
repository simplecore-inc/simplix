package dev.simplecore.simplix.event.constant;

public class SimpliXLogMessages {
    // Common messages
    public static final String NON_EVENT_MESSAGE = "Received non-SimpliXEvent message on channel {}: {}";
    
    // Kafka messages
    public static final String KAFKA_SEND_ERROR = "Failed to send message to Kafka: {}";
    public static final String KAFKA_PROCESS_ERROR = "Failed to process Kafka message: {}";
    
    // RabbitMQ messages
    public static final String RABBIT_SEND_ERROR = "Failed to send message to RabbitMQ: {}";
    public static final String RABBIT_PROCESS_ERROR = "Failed to process RabbitMQ message: {}";
    public static final String RABBIT_CONNECTED = "Connected to RabbitMQ and declared exchange: {}";
    
    // NATS messages
    public static final String NATS_SEND_ERROR = "Failed to send message to NATS: {}";
    public static final String NATS_PROCESS_ERROR = "Failed to process NATS message: {}";
    public static final String NATS_CONNECTED = "Connected to NATS server at {}";
    public static final String NATS_CLOSE_ERROR = "Error closing NATS connection: {}";
    
    // In-Memory messages
    public static final String INMEMORY_SEND_ERROR = "Failed to send message: {}";
    public static final String INMEMORY_NO_SUBSCRIBERS = "No subscribers found for channel: {}";
    public static final String INMEMORY_SUBSCRIBER_ADDED = "Added subscriber for channel: {}";
    public static final String INMEMORY_SUBSCRIBERS_CLEARED = "Cleared all subscribers";
} 