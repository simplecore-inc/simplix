package dev.simplecore.simplix.event.spi.impl;

import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;

import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.spi.MessageBrokerProvider;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for the Kafka message broker adapter.
 */
@Slf4j
public class KafkaBrokerProvider implements MessageBrokerProvider {
    
    @Override
    public String getBrokerType() {
        return "kafka";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if Kafka client is available on the classpath
            Class.forName("org.springframework.kafka.core.KafkaTemplate");
            return true;
        } catch (ClassNotFoundException e) {
            log.debug("Kafka client not available on the classpath");
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public MessageBrokerAdapter createAdapter(ApplicationContext context, SimpliXEventProperties properties) {
        log.debug("Creating KafkaBrokerAdapter");
        
        try {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            
            KafkaTemplate<String, SimpliXMessageEvent> kafkaTemplate = 
                (KafkaTemplate<String, SimpliXMessageEvent>) context.getBean(KafkaTemplate.class);
            
            ConsumerFactory<String, SimpliXMessageEvent> consumerFactory = 
                (ConsumerFactory<String, SimpliXMessageEvent>) context.getBean(ConsumerFactory.class);
            
            return new KafkaBrokerAdapter(
                    retryTemplate, 
                    meterRegistry, 
                    properties, 
                    context, 
                    kafkaTemplate, 
                    consumerFactory);
        } catch (Exception e) {
            log.error("Failed to create KafkaBrokerAdapter", e);
            throw new RuntimeException("Failed to create KafkaBrokerAdapter", e);
        }
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
} 