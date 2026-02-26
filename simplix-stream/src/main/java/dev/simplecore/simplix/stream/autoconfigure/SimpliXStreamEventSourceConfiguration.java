package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.eventsource.EventStreamHandler;
import dev.simplecore.simplix.stream.eventsource.EventSubscriberRegistry;
import dev.simplecore.simplix.stream.eventsource.SimpliXStreamEventSource;
import dev.simplecore.simplix.stream.eventsource.SimpliXStreamEventSourceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for event-based streaming using simplix-event.
 * <p>
 * This configuration is enabled when:
 * <ul>
 *   <li>simplix-event module is on the classpath (Event class is available)</li>
 *   <li>simplix.stream.enabled is true (default)</li>
 *   <li>simplix.stream.event-source.enabled is true</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnClass(Event.class)
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXStreamEventSourceConfiguration {

    /**
     * Event source configuration when feature is enabled.
     */
    @Configuration
    @ConditionalOnProperty(name = "simplix.stream.event-source.enabled", havingValue = "true")
    public static class EventSourceEnabledConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public SimpliXStreamEventSourceRegistry streamEventSourceRegistry(
                @Autowired(required = false) List<SimpliXStreamEventSource> eventSources) {
            if (eventSources == null || eventSources.isEmpty()) {
                log.info("No SimpliXStreamEventSource implementations found");
                return new SimpliXStreamEventSourceRegistry();
            }
            return new SimpliXStreamEventSourceRegistry(eventSources);
        }

        @Bean
        @ConditionalOnMissingBean
        public EventSubscriberRegistry eventSubscriberRegistry() {
            return new EventSubscriberRegistry();
        }

        @Bean
        @ConditionalOnMissingBean
        public EventStreamHandler eventStreamHandler(
                SimpliXStreamEventSourceRegistry eventSourceRegistry,
                EventSubscriberRegistry subscriberRegistry,
                BroadcastService broadcastService) {
            log.info("Event-based streaming enabled with {} event sources",
                    eventSourceRegistry.size());
            return new EventStreamHandler(eventSourceRegistry, subscriberRegistry, broadcastService);
        }

        @Bean
        public EventSourceWiring eventSourceWiring(
                EventStreamHandler eventStreamHandler,
                SimpliXStreamEventSourceRegistry eventSourceRegistry,
                StreamProperties properties) {
            return new EventSourceWiring(eventStreamHandler, eventSourceRegistry, properties);
        }
    }

    /**
     * Wires event source components to subscription management.
     * <p>
     * This wiring ensures that subscriptions for event-based resources
     * are routed to EventStreamHandler instead of SchedulerManager.
     */
    @Slf4j
    public static class EventSourceWiring {

        private final EventStreamHandler eventStreamHandler;
        private final SimpliXStreamEventSourceRegistry eventSourceRegistry;

        public EventSourceWiring(
                EventStreamHandler eventStreamHandler,
                SimpliXStreamEventSourceRegistry eventSourceRegistry,
                StreamProperties properties) {

            this.eventStreamHandler = eventStreamHandler;
            this.eventSourceRegistry = eventSourceRegistry;

            log.info("Event-based streaming initialized:");
            log.info("  - Event sources: {}", eventSourceRegistry.size());
            log.info("  - Registered resources: {}", eventSourceRegistry.getRegisteredResources());
            log.info("  - Registered event types: {}", eventSourceRegistry.getRegisteredEventTypes());
        }

        /**
         * Check if a resource is handled by an event source.
         *
         * @param resource the resource name
         * @return true if the resource has a SimpliXStreamEventSource
         */
        public boolean isEventBasedResource(String resource) {
            return eventSourceRegistry.hasEventSource(resource);
        }

        /**
         * Get the EventStreamHandler for external wiring.
         *
         * @return the event stream handler
         */
        public EventStreamHandler getEventStreamHandler() {
            return eventStreamHandler;
        }

        /**
         * Get the registry for external access.
         *
         * @return the event source registry
         */
        public SimpliXStreamEventSourceRegistry getEventSourceRegistry() {
            return eventSourceRegistry;
        }
    }
}
