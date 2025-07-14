package dev.simplecore.simplix.event.spi;

import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry for message broker providers loaded via Java SPI mechanism.
 * This class discovers and manages all available message broker providers.
 */
@Slf4j
public class MessageBrokerProviderRegistry {
    
    private final List<MessageBrokerProvider> providers;
    private final SimpliXEventProperties properties;
    
    /**
     * Creates a new registry and loads all available providers.
     */
    public MessageBrokerProviderRegistry(SimpliXEventProperties properties) {
        this.properties = properties;
        this.providers = loadProviders();
        logLoadedProviders();
    }
    
    private List<MessageBrokerProvider> loadProviders() {
        List<MessageBrokerProvider> loadedProviders = new ArrayList<>();
        
        try {
            ServiceLoader<MessageBrokerProvider> serviceLoader = ServiceLoader.load(MessageBrokerProvider.class);
            
            for (MessageBrokerProvider provider : serviceLoader) {
                try {
                    if (isProviderEnabled(provider) && provider.isAvailable()) {
                        loadedProviders.add(provider);
                        log.debug("Added provider: {} for broker type: {}", 
                                provider.getClass().getName(), provider.getBrokerType());
                    }
                } catch (Exception e) {
                    log.warn("Failed to load provider: {}", provider.getClass().getName(), e);
                }
            }
            
            loadedProviders.sort(Comparator.comparingInt(MessageBrokerProvider::getOrder));
            
        } catch (Exception e) {
            log.error("Error loading message broker providers", e);
        }
        
        return Collections.unmodifiableList(loadedProviders);
    }
    
    private boolean isProviderEnabled(MessageBrokerProvider provider) {
        // Only enable providers that match the configured mqType
        return provider.getBrokerType().equals(properties.getMqType());
    }
    
    private void logLoadedProviders() {
        if (providers.isEmpty()) {
            log.warn("No message broker providers were loaded for type: {}", properties.getMqType());
        } else {
            log.info("Loaded {} message broker providers: {}", 
                    providers.size(),
                    providers.stream()
                            .map(p -> String.format("%s (%s)", p.getBrokerType(), p.getClass().getSimpleName()))
                            .collect(Collectors.joining(", ")));
        }
    }
    
    /**
     * Returns all available providers.
     *
     * @return List of all providers
     */
    public List<MessageBrokerProvider> getProviders() {
        return providers;
    }
    
    /**
     * Returns all available providers for a specific broker type.
     *
     * @param brokerType The broker type to filter by
     * @return List of matching providers
     */
    public List<MessageBrokerProvider> getProviders(String brokerType) {
        return providers.stream()
                .filter(p -> p.getBrokerType().equals(brokerType))
                .collect(Collectors.toList());
    }
    
    /**
     * Creates a message broker adapter for the specified broker type.
     *
     * @param brokerType The broker type
     * @param context The Spring application context
     * @return A new message broker adapter instance, or null if no suitable provider is found
     */
    public MessageBrokerAdapter createAdapter(String brokerType, ApplicationContext context) {
        return getProviders(brokerType).stream()
                .findFirst()
                .map(provider -> {
                    try {
                        log.info("Creating adapter using provider {} for broker type {}", 
                                provider.getClass().getSimpleName(), brokerType);
                        return provider.createAdapter(context, properties);
                    } catch (Exception e) {
                        log.error("Failed to create adapter for broker type: {}", brokerType, e);
                        return null;
                    }
                })
                .orElseGet(() -> {
                    log.warn("No provider found for broker type: {}", brokerType);
                    return null;
                });
    }
}