package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.kafka.KafkaBrokerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Configuration for Kafka-based messaging broker.
 *
 * <p>Activated when {@code simplix.messaging.broker=kafka} and Spring Kafka
 * is on the classpath. Provides the {@link KafkaBrokerStrategy} bean.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
@ConditionalOnProperty(prefix = "simplix.messaging", name = "broker", havingValue = "kafka")
@Slf4j
public class KafkaMessagingConfiguration {

    @SuppressWarnings("unchecked")
    @Bean
    public KafkaBrokerStrategy kafkaBrokerStrategy(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            org.springframework.kafka.core.ConsumerFactory<String, byte[]> consumerFactory) {
        log.info("Activating Kafka broker strategy");
        KafkaBrokerStrategy strategy = new KafkaBrokerStrategy(kafkaTemplate, consumerFactory);
        strategy.initialize();
        return strategy;
    }
}
