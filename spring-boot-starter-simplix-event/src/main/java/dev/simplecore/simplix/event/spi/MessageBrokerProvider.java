package dev.simplecore.simplix.event.spi;

import org.springframework.context.ApplicationContext;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;

/**
 * Service Provider Interface for message broker implementations.
 * This interface allows external developers to add support for new message brokers
 * by implementing this interface and registering it via Java SPI mechanism.
 */
public interface MessageBrokerProvider {
    
    /**
     * Returns the unique type identifier for this message broker.
     * This value should match the one used in configuration (simplix.event.mq-type).
     *
     * @return The broker type identifier
     */
    String getBrokerType();
    
    /**
     * Checks if this provider can create a broker adapter in the current environment.
     * This method should verify if all required dependencies are available.
     *
     * @return true if this provider can create a broker adapter, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Creates a new message broker adapter instance.
     *
     * @param context The Spring application context
     * @param properties The SimpliX event properties
     * @return A new message broker adapter instance
     */
    MessageBrokerAdapter createAdapter(ApplicationContext context, SimpliXEventProperties properties);
    
    /**
     * Returns the order of this provider when multiple providers are available for the same broker type.
     * Lower values have higher priority.
     *
     * @return The order value
     */
    default int getOrder() {
        return 0;
    }
} 