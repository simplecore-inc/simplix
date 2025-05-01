package dev.simplecore.simplix.event.spi.impl;

import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;

import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.spi.MessageBrokerProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for the NATS message broker adapter.
 */
@Slf4j
public class NatsBrokerProvider implements MessageBrokerProvider {
    
    @Override
    public String getBrokerType() {
        return "nats";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Check if NATS client is available on the classpath
            Class.forName("io.nats.client.Connection");
            return true;
        } catch (ClassNotFoundException e) {
            log.debug("NATS client not available on the classpath");
            return false;
        }
    }
    
    @Override
    public MessageBrokerAdapter createAdapter(ApplicationContext context, SimpliXEventProperties properties) {
        log.debug("Creating NatsBrokerAdapter");
        
        try {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            Connection natsConnection = context.getBean(Connection.class);
            
            return new NatsBrokerAdapter(
                    retryTemplate, 
                    meterRegistry, 
                    properties, 
                    context, 
                    natsConnection);
        } catch (Exception e) {
            log.error("Failed to create NatsBrokerAdapter", e);
            throw new RuntimeException("Failed to create NatsBrokerAdapter", e);
        }
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
} 