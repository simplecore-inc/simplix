package dev.simplecore.simplix.stream.collector;

import dev.simplecore.simplix.stream.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for SimpliXStreamDataCollector implementations.
 * <p>
 * Automatically discovers and registers all SimpliXStreamDataCollector beans in the application context.
 */
@Slf4j
@Component
public class SimpliXStreamDataCollectorRegistry {

    private final Map<String, SimpliXStreamDataCollector> collectors = new ConcurrentHashMap<>();

    /**
     * Create registry with auto-discovered collectors.
     *
     * @param discoveredCollectors the collectors discovered by Spring
     */
    @Autowired(required = false)
    public SimpliXStreamDataCollectorRegistry(List<SimpliXStreamDataCollector> discoveredCollectors) {
        if (discoveredCollectors != null) {
            for (SimpliXStreamDataCollector collector : discoveredCollectors) {
                register(collector);
            }
        }
        log.info("SimpliXStreamDataCollectorRegistry initialized with {} collectors", collectors.size());
    }

    /**
     * Default constructor for when no collectors are found.
     */
    public SimpliXStreamDataCollectorRegistry() {
        log.info("SimpliXStreamDataCollectorRegistry initialized with no collectors");
    }

    /**
     * Register a data collector.
     *
     * @param collector the collector to register
     */
    public void register(SimpliXStreamDataCollector collector) {
        String resource = collector.getResource();
        if (resource == null || resource.isBlank()) {
            log.warn("Skipping collector with null or blank resource: {}", collector.getClass().getName());
            return;
        }

        SimpliXStreamDataCollector existing = collectors.put(resource, collector);
        if (existing != null) {
            log.warn("Replaced existing collector for resource '{}': {} -> {}",
                    resource, existing.getClass().getName(), collector.getClass().getName());
        } else {
            log.debug("Registered collector for resource '{}': {}",
                    resource, collector.getClass().getName());
        }
    }

    /**
     * Unregister a data collector.
     *
     * @param resource the resource name
     * @return the removed collector, or null if not found
     */
    public SimpliXStreamDataCollector unregister(String resource) {
        SimpliXStreamDataCollector removed = collectors.remove(resource);
        if (removed != null) {
            log.debug("Unregistered collector for resource '{}'", resource);
        }
        return removed;
    }

    /**
     * Get a collector by resource name.
     *
     * @param resource the resource name
     * @return the collector
     * @throws ResourceNotFoundException if not found
     */
    public SimpliXStreamDataCollector getCollector(String resource) {
        SimpliXStreamDataCollector collector = collectors.get(resource);
        if (collector == null) {
            throw new ResourceNotFoundException(resource);
        }
        return collector;
    }

    /**
     * Get a collector by resource name, optionally.
     *
     * @param resource the resource name
     * @return the collector if found
     */
    public Optional<SimpliXStreamDataCollector> findCollector(String resource) {
        return Optional.ofNullable(collectors.get(resource));
    }

    /**
     * Check if a resource is registered.
     *
     * @param resource the resource name
     * @return true if registered
     */
    public boolean hasCollector(String resource) {
        return collectors.containsKey(resource);
    }

    /**
     * Get all registered resource names.
     *
     * @return collection of resource names
     */
    public Collection<String> getRegisteredResources() {
        return collectors.keySet();
    }

    /**
     * Get all registered collectors.
     *
     * @return collection of collectors
     */
    public Collection<SimpliXStreamDataCollector> getCollectors() {
        return collectors.values();
    }

    /**
     * Get the number of registered collectors.
     *
     * @return the count
     */
    public int size() {
        return collectors.size();
    }
}
