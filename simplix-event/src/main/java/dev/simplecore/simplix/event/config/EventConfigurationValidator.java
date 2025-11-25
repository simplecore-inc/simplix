package dev.simplecore.simplix.event.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration validator for event module
 * Validates configuration at startup and provides helpful error messages
 */
@Configuration
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(prefix = "simplix.events", name = "validation.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EventConfigurationValidator {

    private final EventProperties properties;
    private final Environment environment;

    private static final List<String> VALID_MODES = Arrays.asList("local", "redis", "kafka", "rabbit");

    @PostConstruct
    public void validateConfiguration() {
        log.info("Validating event module configuration...");

        validateMode();
        validateModeSpecificConfiguration();
        logConfigurationSummary();

        log.info("Event module configuration validation completed successfully");
    }

    private void validateMode() {
        String mode = properties.getMode();
        if (mode == null || mode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Event mode must be specified. Valid modes: " + VALID_MODES +
                ". Set 'simplix.events.mode' property in your configuration."
            );
        }

        if (!VALID_MODES.contains(mode.toLowerCase())) {
            throw new IllegalArgumentException(
                String.format("Invalid event mode: '%s'. Valid modes are: %s", mode, VALID_MODES)
            );
        }

        log.debug("Event mode '{}' is valid", mode);
    }

    private void validateModeSpecificConfiguration() {
        String mode = properties.getMode().toLowerCase();

        switch (mode) {
            case "redis":
                validateRedisConfiguration();
                break;
            case "kafka":
                validateKafkaConfiguration();
                break;
            case "rabbit":
                validateRabbitConfiguration();
                break;
            case "local":
                log.debug("Local mode requires no additional configuration");
                break;
        }
    }

    private void validateRedisConfiguration() {
        log.debug("Validating Redis configuration...");

        // Check if Redis is on the classpath
        if (!isClassPresent("org.springframework.data.redis.core.RedisTemplate")) {
            throw new IllegalStateException(
                "Redis mode is configured but spring-boot-starter-data-redis is not on the classpath. " +
                "Add the dependency to use Redis event publishing."
            );
        }

        // Check Redis connection properties
        String redisHost = environment.getProperty("spring.data.redis.host");
        if (redisHost == null || redisHost.trim().isEmpty()) {
            log.warn("spring.data.redis.host is not configured, using default 'localhost'");
        }

        // Check Redis Stream configuration
        Boolean streamEnabled = environment.getProperty("simplix.events.redis.stream.enabled", Boolean.class, true);
        if (streamEnabled) {
            log.debug("Redis Stream is enabled");

            String streamPrefix = environment.getProperty("simplix.events.redis.stream-prefix");
            if (streamPrefix == null || streamPrefix.trim().isEmpty()) {
                log.warn("Redis stream prefix not configured, using default 'simplix-events'");
            }

            String consumerGroup = environment.getProperty("simplix.events.redis.stream.consumer-group");
            if (consumerGroup == null || consumerGroup.trim().isEmpty()) {
                log.warn("Redis consumer group not configured, using default 'simplix-events-group'");
            }

            Long maxLen = environment.getProperty("simplix.events.redis.stream.max-len", Long.class);
            if (maxLen != null && maxLen > 0) {
                log.debug("Redis Stream MAXLEN trimming enabled: {}", maxLen);
            } else {
                log.debug("Redis Stream MAXLEN trimming disabled");
            }
        } else {
            log.warn("Redis Stream is disabled. Using Pub/Sub mode (not recommended for production).");
        }
    }

    private void validateKafkaConfiguration() {
        log.debug("Validating Kafka configuration...");

        // Check if Kafka is on the classpath
        if (!isClassPresent("org.springframework.kafka.core.KafkaTemplate")) {
            throw new IllegalStateException(
                "Kafka mode is configured but spring-kafka is not on the classpath. " +
                "Add the dependency to use Kafka event publishing."
            );
        }

        // Check Kafka bootstrap servers
        String bootstrapServers = environment.getProperty("spring.kafka.bootstrap-servers");
        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            log.warn("spring.kafka.bootstrap-servers is not configured, using default 'localhost:9092'");
        }

        String topicPrefix = environment.getProperty("simplix.events.kafka.topic-prefix");
        if (topicPrefix == null || topicPrefix.trim().isEmpty()) {
            log.warn("Kafka topic prefix not configured, using default 'simplix-events'");
        }
    }

    private void validateRabbitConfiguration() {
        log.debug("Validating RabbitMQ configuration...");

        // Check if RabbitMQ is on the classpath
        if (!isClassPresent("org.springframework.amqp.rabbit.core.RabbitTemplate")) {
            throw new IllegalStateException(
                "RabbitMQ mode is configured but spring-boot-starter-amqp is not on the classpath. " +
                "Add the dependency to use RabbitMQ event publishing."
            );
        }

        // Check RabbitMQ connection properties
        String rabbitHost = environment.getProperty("spring.rabbitmq.host");
        if (rabbitHost == null || rabbitHost.trim().isEmpty()) {
            log.warn("spring.rabbitmq.host is not configured, using default 'localhost'");
        }

        String exchange = environment.getProperty("simplix.events.rabbit.exchange");
        if (exchange == null || exchange.trim().isEmpty()) {
            log.warn("RabbitMQ exchange not configured, using default 'simplix.events'");
        }

        // Validate DLQ configuration if enabled
        Boolean dlqEnabled = environment.getProperty("simplix.events.rabbit.dlq.enabled", Boolean.class, true);
        if (dlqEnabled) {
            String dlqExchange = environment.getProperty("simplix.events.rabbit.dlq.exchange");
            if (dlqExchange == null || dlqExchange.trim().isEmpty()) {
                log.warn("DLQ is enabled but exchange not configured, using default 'simplix.events.dlq'");
            }
        }
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void logConfigurationSummary() {
        log.info("=== Event Module Configuration Summary ===");
        log.info("Mode: {}", properties.getMode());
        log.info("Enrich Metadata: {}", properties.isEnrichMetadata());
        log.info("Persistent by Default: {}", properties.isPersistentByDefault());
        log.info("Instance ID: {}", environment.getProperty("simplix.events.instance-id", "auto-generated"));

        String mode = properties.getMode().toLowerCase();
        switch (mode) {
            case "redis":
                Boolean streamEnabled = environment.getProperty("simplix.events.redis.stream.enabled", Boolean.class, true);
                log.info("Redis Stream Enabled: {}", streamEnabled);
                if (streamEnabled) {
                    log.info("Redis Stream Prefix: {}",
                        environment.getProperty("simplix.events.redis.stream-prefix", "simplix-events"));
                    log.info("Redis Consumer Group: {}",
                        environment.getProperty("simplix.events.redis.stream.consumer-group", "simplix-events-group"));
                    log.info("Redis Stream Max Length: {}",
                        environment.getProperty("simplix.events.redis.stream.max-len", "10000"));
                } else {
                    log.info("Redis Channel Prefix: {}",
                        environment.getProperty("simplix.events.redis.channel-prefix", "simplix:events:"));
                }
                break;
            case "kafka":
                log.info("Kafka Topic Prefix: {}",
                    environment.getProperty("simplix.events.kafka.topic-prefix", "simplix-events"));
                log.info("Kafka Default Topic: {}",
                    environment.getProperty("simplix.events.kafka.default-topic", "domain-events"));
                break;
            case "rabbit":
                log.info("RabbitMQ Exchange: {}",
                    environment.getProperty("simplix.events.rabbit.exchange", "simplix.events"));
                log.info("RabbitMQ Queue: {}",
                    environment.getProperty("simplix.events.rabbit.queue", "simplix.events.queue"));
                log.info("RabbitMQ DLQ Enabled: {}",
                    environment.getProperty("simplix.events.rabbit.dlq.enabled", "true"));
                break;
        }
        log.info("==========================================");
    }
}