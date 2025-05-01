package dev.simplecore.simplix.event.constant;

public class SimpliXMetricsConstants {
    public static final String BASE = "simplix";
    
    // Common metrics
    public static final String METRIC_CHANNEL = "channel";
    
    // Kafka metrics
    public static final String KAFKA_OUTBOUND_ATTEMPTS = BASE + ".kafka.outbound.attempts";
    public static final String KAFKA_OUTBOUND_SUCCESS = BASE + ".kafka.outbound.success";
    public static final String KAFKA_OUTBOUND_ERRORS = BASE + ".kafka.outbound.errors";
    public static final String KAFKA_INBOUND_RECEIVED = BASE + ".kafka.inbound.received";
    public static final String KAFKA_INBOUND_PROCESSED = BASE + ".kafka.inbound.processed";
    public static final String KAFKA_INBOUND_ERRORS = BASE + ".kafka.inbound.errors";
    public static final String KAFKA_SUBSCRIBERS = BASE + ".kafka.subscribers";
    
    // RabbitMQ metrics
    public static final String RABBIT_OUTBOUND_ATTEMPTS = BASE + ".rabbit.outbound.attempts";
    public static final String RABBIT_OUTBOUND_SUCCESS = BASE + ".rabbit.outbound.success";
    public static final String RABBIT_OUTBOUND_ERRORS = BASE + ".rabbit.outbound.errors";
    public static final String RABBIT_INBOUND_RECEIVED = BASE + ".rabbit.inbound.received";
    public static final String RABBIT_INBOUND_PROCESSED = BASE + ".rabbit.inbound.processed";
    public static final String RABBIT_INBOUND_ERRORS = BASE + ".rabbit.inbound.errors";
    public static final String RABBIT_SUBSCRIBERS = BASE + ".rabbit.subscribers";
    
    // NATS metrics
    public static final String NATS_OUTBOUND_ATTEMPTS = BASE + ".nats.outbound.attempts";
    public static final String NATS_OUTBOUND_SUCCESS = BASE + ".nats.outbound.success";
    public static final String NATS_OUTBOUND_ERRORS = BASE + ".nats.outbound.errors";
    public static final String NATS_INBOUND_RECEIVED = BASE + ".nats.inbound.received";
    public static final String NATS_INBOUND_PROCESSED = BASE + ".nats.inbound.processed";
    public static final String NATS_INBOUND_ERRORS = BASE + ".nats.inbound.errors";
    public static final String NATS_SUBSCRIBERS = BASE + ".nats.subscribers";
    
    // In-Memory metrics
    public static final String INMEMORY_OUTBOUND_ATTEMPTS = BASE + ".inmemory.outbound.attempts";
    public static final String INMEMORY_OUTBOUND_SUCCESS = BASE + ".inmemory.outbound.success";
    public static final String INMEMORY_OUTBOUND_ERRORS = BASE + ".inmemory.outbound.errors";
    public static final String INMEMORY_INBOUND_RECEIVED = BASE + ".inmemory.inbound.received";
    public static final String INMEMORY_INBOUND_PROCESSED = BASE + ".inmemory.inbound.processed";
    public static final String INMEMORY_INBOUND_ERRORS = BASE + ".inmemory.inbound.errors";
    public static final String INMEMORY_SUBSCRIBERS = BASE + ".inmemory.subscribers";
} 