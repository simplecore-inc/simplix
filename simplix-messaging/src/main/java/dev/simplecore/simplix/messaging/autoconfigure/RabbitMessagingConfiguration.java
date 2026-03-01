package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.rabbit.RabbitBrokerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for RabbitMQ-based messaging broker.
 *
 * <p>Activated when {@code simplix.messaging.broker=rabbit} and Spring AMQP
 * is on the classpath. Provides the {@link RabbitBrokerStrategy} bean.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
@ConditionalOnProperty(prefix = "simplix.messaging", name = "broker", havingValue = "rabbit")
@Slf4j
public class RabbitMessagingConfiguration {

    @Bean
    public RabbitBrokerStrategy rabbitBrokerStrategy(RabbitTemplate rabbitTemplate,
                                                     ConnectionFactory connectionFactory) {
        log.info("Activating RabbitMQ broker strategy");
        RabbitBrokerStrategy strategy = new RabbitBrokerStrategy(rabbitTemplate, connectionFactory);
        strategy.initialize();
        return strategy;
    }
}
