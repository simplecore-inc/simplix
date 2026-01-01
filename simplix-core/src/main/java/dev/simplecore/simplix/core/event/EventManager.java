package dev.simplecore.simplix.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Core Event Manager
 * Manages event publishers using Service Provider Interface (SPI)
 * This allows modules to publish events without depending on event implementation
 */
public class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);
    private static final EventManager INSTANCE = new EventManager();
    private final EventPublisher publisher;
    private static final String NO_EVENT_PUBLISHER = "NoOpEventPublisher";

    private EventManager() {
        this.publisher = loadPublisher();
        log.info("EventManager initialized with publisher: {}", publisher.getName());
    }

    public static EventManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load event publisher using SPI
     */
    private EventPublisher loadPublisher() {
        ServiceLoader<EventPublisher> loader = ServiceLoader.load(EventPublisher.class);
        
        List<EventPublisher> publishers = new ArrayList<>();
        for (EventPublisher publisher : loader) {
            publishers.add(publisher);
            log.trace("Found event publisher: {} with priority: {}",
                publisher.getName(), publisher.getPriority());
        }

        if (publishers.isEmpty()) {
            log.warn("No event publisher found via SPI, using NoOp publisher");
            return new NoOpEventPublisher();
        }

        // Sort by priority (highest first)
        publishers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        EventPublisher selected = publishers.get(0);
        log.info("Selected event publisher: {} with priority: {}", 
            selected.getName(), selected.getPriority());
        
        return selected;
    }

    /**
     * Publish an event
     */
    public void publish(Event event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }

        try {
            publisher.publish(event);
            log.trace("Published event: {} for aggregate: {}",
                event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            log.error("Failed to publish event: {} for aggregate: {}",
                event.getEventType(), event.getAggregateId(), e);
        }
    }

    /**
     * Check if event publishing is available
     */
    public boolean isAvailable() {
        return publisher.isAvailable();
    }

    /**
     * Get current publisher name
     */
    public String getPublisherName() {
        return publisher.getName();
    }

    /**
     * No-operation event publisher (used when no real publisher is available)
     */
    private static class NoOpEventPublisher implements EventPublisher {

        @Override
        public void publish(Event event) {
            log.trace("NoOp: Would publish event {} for aggregate {}",
                event.getEventType(), event.getAggregateId());
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getName() {
            return NO_EVENT_PUBLISHER;
        }
    }
}