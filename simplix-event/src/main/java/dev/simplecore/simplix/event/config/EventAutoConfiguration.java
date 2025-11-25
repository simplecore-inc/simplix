package dev.simplecore.simplix.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.event.EventPublisher;
import dev.simplecore.simplix.event.core.EventStrategy;
import dev.simplecore.simplix.event.provider.CoreEventPublisherImpl;
import dev.simplecore.simplix.event.publisher.UnifiedEventPublisher;
import dev.simplecore.simplix.event.strategy.LocalEventStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Spring Boot auto-configuration for the event module
 * Automatically configures event publishing based on available dependencies and configuration
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProperties.class)
@Slf4j
public class EventAutoConfiguration {

    /**
     * Main event publisher bean
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(UnifiedEventPublisher.class)
    public UnifiedEventPublisher unifiedEventPublisher(
            List<EventStrategy> strategies,
            EventProperties properties) {

        log.info("Configuring EventPublisher with mode: {}", properties.getMode());

        if (strategies.isEmpty()) {
            throw new IllegalStateException(
                "No event strategies available. Please check your configuration.");
        }

        log.debug("Available event strategies: {}",
            strategies.stream().map(EventStrategy::getName).toList());

        return new UnifiedEventPublisher(strategies, properties.getMode());
    }

    /**
     * Configuration for local event strategy
     * Other strategies (Redis, Kafka, RabbitMQ) are configured in separate configuration classes
     */
    @Configuration
    @ConditionalOnClass(EventStrategy.class)
    public static class LocalEventStrategyConfiguration {

        /**
         * Local event strategy - always available
         */
        @Bean
        @ConditionalOnMissingBean(LocalEventStrategy.class)
        public LocalEventStrategy localEventStrategy(ApplicationEventPublisher publisher) {
            log.debug("Registering LocalEventStrategy");
            return new LocalEventStrategy(publisher);
        }
    }

    /**
     * CoreEventPublisherImpl for bridging to core module
     */
    @Bean
    @ConditionalOnMissingBean(CoreEventPublisherImpl.class)
    @ConditionalOnClass(name = "dev.simplecore.simplix.core.event.EventPublisher")
    public CoreEventPublisherImpl coreEventPublisherImpl(UnifiedEventPublisher unifiedEventPublisher) {
        log.debug("Registering CoreEventPublisherImpl as bridge to core module");
        return new CoreEventPublisherImpl(unifiedEventPublisher);
    }

    /**
     * ObjectMapper configuration for event serialization
     */
    @Bean
    @ConditionalOnMissingBean(name = "eventObjectMapper")
    public ObjectMapper eventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // Register JavaTime module and others
        return mapper;
    }


    /**
     * Metrics configuration for event publishing
     */
    @Configuration
    @ConditionalOnProperty(
        prefix = "simplix.events.monitoring",
        name = "metrics-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public static class EventMetricsConfiguration {

        @Bean
        public EventMetrics eventMetrics() {
            return new EventMetrics();
        }
    }

    /**
     * Health indicator configuration for event publishing
     */
    @Configuration
    @ConditionalOnProperty(
        prefix = "simplix.events.monitoring.health-check",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public static class EventHealthIndicatorConfiguration {

        @Bean
        public EventPublisherHealthIndicator eventPublisherHealthIndicator(
                UnifiedEventPublisher unifiedEventPublisher,
                EventProperties properties) {
            log.debug("Registering EventPublisherHealthIndicator");
            return new EventPublisherHealthIndicator(unifiedEventPublisher, properties);
        }
    }
}