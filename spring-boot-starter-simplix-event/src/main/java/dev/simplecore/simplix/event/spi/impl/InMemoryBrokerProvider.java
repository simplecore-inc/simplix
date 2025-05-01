package dev.simplecore.simplix.event.spi.impl;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import io.micrometer.core.instrument.MeterRegistry;

import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.spi.MessageBrokerProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for the in-memory message broker adapter.
 * This is the default provider that is always available.
 */
@Slf4j
public class InMemoryBrokerProvider implements MessageBrokerProvider {
    
    @Override
    public String getBrokerType() {
        return "in-memory";
    }
    
    @Override
    public boolean isAvailable() {
        // In-memory broker is always available
        return true;
    }
    
    @Override
    public MessageBrokerAdapter createAdapter(ApplicationContext context, SimpliXEventProperties properties) {
        log.debug("Creating InMemoryBrokerAdapter");
        
        try {
            RetryTemplate retryTemplate = context.getBean(RetryTemplate.class);
            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            ApplicationEventPublisher eventPublisher = context;
            
            // Create or get task scheduler
            ThreadPoolTaskScheduler taskScheduler;
            try {
                taskScheduler = context.getBean(ThreadPoolTaskScheduler.class);
            } catch (Exception e) {
                log.debug("ThreadPoolTaskScheduler not found, creating a new one");
                taskScheduler = new ThreadPoolTaskScheduler();
                taskScheduler.setPoolSize(5);
                taskScheduler.setThreadNamePrefix("simplix-event-scheduler-");
                taskScheduler.initialize();
            }
            
            return new InMemoryBrokerAdapter(
                    retryTemplate, 
                    meterRegistry, 
                    properties, 
                    context, 
                    eventPublisher, 
                    taskScheduler);
        } catch (Exception e) {
            log.error("Failed to create InMemoryBrokerAdapter", e);
            throw new RuntimeException("Failed to create InMemoryBrokerAdapter", e);
        }
    }
    
    @Override
    public int getOrder() {
        // Lowest priority as this is the default fallback
        return 100;
    }
} 