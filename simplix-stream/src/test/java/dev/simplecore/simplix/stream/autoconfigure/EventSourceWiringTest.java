package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.eventsource.EventStreamHandler;
import dev.simplecore.simplix.stream.eventsource.SimpliXStreamEventSourceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SimpliXStreamEventSourceConfiguration.EventSourceWiring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventSourceWiring")
class EventSourceWiringTest {

    @Mock
    private EventStreamHandler eventStreamHandler;

    @Mock
    private SimpliXStreamEventSourceRegistry eventSourceRegistry;

    @Nested
    @DisplayName("construction and accessors")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("should create wiring with event stream handler and registry")
        void shouldCreateWiring() {
            StreamProperties properties = new StreamProperties();
            when(eventSourceRegistry.size()).thenReturn(2);
            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("resource1", "resource2"));
            when(eventSourceRegistry.getRegisteredEventTypes()).thenReturn(Set.of("EventType1"));

            SimpliXStreamEventSourceConfiguration.EventSourceWiring wiring =
                    new SimpliXStreamEventSourceConfiguration.EventSourceWiring(
                            eventStreamHandler, eventSourceRegistry, properties);

            assertThat(wiring.getEventStreamHandler()).isEqualTo(eventStreamHandler);
            assertThat(wiring.getEventSourceRegistry()).isEqualTo(eventSourceRegistry);
        }

        @Test
        @DisplayName("should delegate isEventBasedResource to registry")
        void shouldDelegateIsEventBasedResource() {
            StreamProperties properties = new StreamProperties();
            when(eventSourceRegistry.size()).thenReturn(1);
            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("events"));
            when(eventSourceRegistry.getRegisteredEventTypes()).thenReturn(Set.of("TestEvent"));
            when(eventSourceRegistry.hasEventSource("events")).thenReturn(true);
            when(eventSourceRegistry.hasEventSource("polling")).thenReturn(false);

            SimpliXStreamEventSourceConfiguration.EventSourceWiring wiring =
                    new SimpliXStreamEventSourceConfiguration.EventSourceWiring(
                            eventStreamHandler, eventSourceRegistry, properties);

            assertThat(wiring.isEventBasedResource("events")).isTrue();
            assertThat(wiring.isEventBasedResource("polling")).isFalse();
        }
    }
}
