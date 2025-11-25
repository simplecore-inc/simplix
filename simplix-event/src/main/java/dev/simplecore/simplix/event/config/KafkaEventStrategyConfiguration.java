package dev.simplecore.simplix.event.config;

import dev.simplecore.simplix.event.strategy.KafkaEventStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka event strategy configuration
 *
 * <p>IMPORTANT: This configuration class is only loaded when KafkaTemplate is on the classpath.
 * The strategy class ({@link KafkaEventStrategy}) will NOT be loaded if this condition is not met,
 * preventing ClassNotFoundException even though KafkaEventStrategy directly imports Kafka classes.
 *
 * <p>Safety mechanism:
 * <ul>
 *   <li>Uses string-based @ConditionalOnClass to avoid eager class loading</li>
 *   <li>KafkaEventStrategy has no @Component annotation - not component scanned</li>
 *   <li>Created only via @Bean when this Configuration is loaded</li>
 *   <li>If Kafka is missing, Configuration is not loaded, strategy is never referenced</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.event.strategy.KafkaEventStrategy
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
public class KafkaEventStrategyConfiguration {

    /**
     * Creates Kafka event strategy bean for high-throughput distributed event streaming
     *
     * <p>This strategy publishes events to Kafka topics for scalable, ordered event processing
     * with support for partitioning, retry logic, and both sync/async publishing modes.
     *
     * @param kafkaTemplate KafkaTemplate for Kafka operations (optional)
     * @param topicPrefix Kafka topic prefix from configuration
     * @param defaultTopic Default Kafka topic name
     * @return Kafka event strategy instance
     */
    @Bean
    @ConditionalOnMissingBean(KafkaEventStrategy.class)
    public KafkaEventStrategy kafkaEventStrategy(
            @Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${simplix.events.kafka.topic-prefix:simplix-events}") String topicPrefix,
            @Value("${simplix.events.kafka.default-topic:domain-events}") String defaultTopic) {
        log.debug("Registering KafkaEventStrategy");

        KafkaEventStrategy strategy = new KafkaEventStrategy();
        strategy.setKafkaTemplate(kafkaTemplate);
        strategy.setTopicPrefix(topicPrefix);
        strategy.setDefaultTopic(defaultTopic);

        // Initialize the strategy
        strategy.initialize();

        return strategy;
    }
}
