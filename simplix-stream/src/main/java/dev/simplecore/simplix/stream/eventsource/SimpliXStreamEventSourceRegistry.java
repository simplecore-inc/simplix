package dev.simplecore.simplix.stream.eventsource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for SimpliXStreamEventSource implementations.
 * <p>
 * Automatically discovers and registers all SimpliXStreamEventSource beans in the application context.
 * Provides lookup by both resource name and event type.
 */
@Slf4j
public class SimpliXStreamEventSourceRegistry {

    // Resource name -> EventSource mapping
    private final Map<String, SimpliXStreamEventSource> sourcesByResource = new ConcurrentHashMap<>();

    // Event type -> EventSource mapping (for event handling)
    private final Map<String, SimpliXStreamEventSource> sourcesByEventType = new ConcurrentHashMap<>();

    /**
     * Create registry with auto-discovered event sources.
     *
     * @param discoveredSources the event sources discovered by Spring
     */
    @Autowired(required = false)
    public SimpliXStreamEventSourceRegistry(List<SimpliXStreamEventSource> discoveredSources) {
        if (discoveredSources != null) {
            for (SimpliXStreamEventSource source : discoveredSources) {
                register(source);
            }
        }
        log.info("SimpliXStreamEventSourceRegistry initialized with {} event sources", sourcesByResource.size());
    }

    /**
     * Default constructor for when no event sources are found.
     */
    public SimpliXStreamEventSourceRegistry() {
        log.info("SimpliXStreamEventSourceRegistry initialized with no event sources");
    }

    /**
     * Register an event source.
     *
     * @param source the source to register
     */
    public void register(SimpliXStreamEventSource source) {
        String resource = source.getResource();
        String eventType = source.getEventType();

        if (resource == null || resource.isBlank()) {
            log.warn("Skipping event source with null or blank resource: {}", source.getClass().getName());
            return;
        }

        if (eventType == null || eventType.isBlank()) {
            log.warn("Skipping event source with null or blank eventType: {}", source.getClass().getName());
            return;
        }

        // Register by resource
        SimpliXStreamEventSource existingByResource = sourcesByResource.put(resource, source);
        if (existingByResource != null) {
            log.warn("Replaced existing event source for resource '{}': {} -> {}",
                    resource, existingByResource.getClass().getName(), source.getClass().getName());
        }

        // Register by event type
        SimpliXStreamEventSource existingByEventType = sourcesByEventType.put(eventType, source);
        if (existingByEventType != null) {
            log.warn("Replaced existing event source for eventType '{}': {} -> {}",
                    eventType, existingByEventType.getClass().getName(), source.getClass().getName());
        }

        log.debug("Registered event source for resource '{}' with eventType '{}': {}",
                resource, eventType, source.getClass().getName());
    }

    /**
     * Unregister an event source by resource name.
     *
     * @param resource the resource name
     * @return the removed source, or null if not found
     */
    public SimpliXStreamEventSource unregister(String resource) {
        SimpliXStreamEventSource removed = sourcesByResource.remove(resource);
        if (removed != null) {
            sourcesByEventType.remove(removed.getEventType());
            log.debug("Unregistered event source for resource '{}'", resource);
        }
        return removed;
    }

    /**
     * Get an event source by resource name.
     *
     * @param resource the resource name
     * @return the event source if found
     */
    public Optional<SimpliXStreamEventSource> findByResource(String resource) {
        return Optional.ofNullable(sourcesByResource.get(resource));
    }

    /**
     * Get an event source by event type.
     *
     * @param eventType the event type
     * @return the event source if found
     */
    public Optional<SimpliXStreamEventSource> findByEventType(String eventType) {
        return Optional.ofNullable(sourcesByEventType.get(eventType));
    }

    /**
     * Check if a resource has an event source.
     *
     * @param resource the resource name
     * @return true if registered
     */
    public boolean hasEventSource(String resource) {
        return sourcesByResource.containsKey(resource);
    }

    /**
     * Check if an event type has an event source.
     *
     * @param eventType the event type
     * @return true if registered
     */
    public boolean hasEventSourceForEventType(String eventType) {
        return sourcesByEventType.containsKey(eventType);
    }

    /**
     * Get all registered resource names.
     *
     * @return collection of resource names
     */
    public Collection<String> getRegisteredResources() {
        return sourcesByResource.keySet();
    }

    /**
     * Get all registered event types.
     *
     * @return collection of event types
     */
    public Collection<String> getRegisteredEventTypes() {
        return sourcesByEventType.keySet();
    }

    /**
     * Get all registered event sources.
     *
     * @return collection of event sources
     */
    public Collection<SimpliXStreamEventSource> getEventSources() {
        return sourcesByResource.values();
    }

    /**
     * Get the number of registered event sources.
     *
     * @return the count
     */
    public int size() {
        return sourcesByResource.size();
    }
}
