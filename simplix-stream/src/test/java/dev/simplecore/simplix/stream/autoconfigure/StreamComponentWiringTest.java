package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.eventsource.EventStreamHandler;
import dev.simplecore.simplix.stream.eventsource.SimpliXStreamEventSourceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimpliXStreamCoreConfiguration.StreamComponentWiring.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamComponentWiring")
class StreamComponentWiringTest {

    @Mock
    private SubscriptionManager subscriptionManager;

    @Mock
    private SchedulerManager schedulerManager;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SimpliXStreamDataCollectorRegistry collectorRegistry;

    @Mock
    private EventStreamHandler eventStreamHandler;

    @Mock
    private SimpliXStreamEventSourceRegistry eventSourceRegistry;

    private StreamProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setScheduler(new StreamProperties.SchedulerConfig());
        properties.getScheduler().setDefaultInterval(Duration.ofSeconds(1));
        properties.getScheduler().setMinInterval(Duration.ofMillis(100));
    }

    @Nested
    @DisplayName("without event source")
    class WithoutEventSource {

        @Test
        @DisplayName("should route subscription to scheduler manager")
        void shouldRouteToSchedulerManager() {
            // Capture the callbacks
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> addCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> removeCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, null, null);

            verify(subscriptionManager).setOnSubscriptionAdded(addCaptor.capture());
            verify(subscriptionManager).setOnSubscriptionRemoved(removeCaptor.capture());

            // Simulate adding a subscription (no collector found)
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            when(collectorRegistry.findCollector("stock")).thenReturn(Optional.empty());

            addCaptor.getValue().accept(key, "session-1");

            verify(schedulerManager).addSubscriber(eq(key), eq("session-1"), eq(Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("should use collector interval when available")
        void shouldUseCollectorInterval() {
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> addCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, null, null);

            verify(subscriptionManager).setOnSubscriptionAdded(addCaptor.capture());

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SimpliXStreamDataCollector collector = mock(SimpliXStreamDataCollector.class);
            when(collector.getDefaultIntervalMs()).thenReturn(2000L);
            when(collector.getMinIntervalMs()).thenReturn(500L);
            when(collectorRegistry.findCollector("stock")).thenReturn(Optional.of(collector));

            addCaptor.getValue().accept(key, "session-1");

            verify(schedulerManager).addSubscriber(eq(key), eq("session-1"), eq(Duration.ofMillis(2000)));
        }

        @Test
        @DisplayName("should route removal to scheduler manager")
        void shouldRouteRemovalToScheduler() {
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> removeCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, null, null);

            verify(subscriptionManager).setOnSubscriptionRemoved(removeCaptor.capture());

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            removeCaptor.getValue().accept(key, "session-1");

            verify(schedulerManager).removeSubscriber(key, "session-1");
        }

        @Test
        @DisplayName("should clean up subscriptions on session termination")
        void shouldCleanUpOnSessionTermination() {
            ArgumentCaptor<Consumer<StreamSession>> terminateCaptor =
                    ArgumentCaptor.forClass(Consumer.class);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, null, null);

            verify(sessionManager).setOnSessionTerminated(terminateCaptor.capture());

            StreamSession session = mock(StreamSession.class);
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            when(session.getSubscriptions()).thenReturn(Set.of(key));
            when(session.getId()).thenReturn("session-1");

            terminateCaptor.getValue().accept(session);

            verify(schedulerManager).removeSubscriber(key, "session-1");
        }
    }

    @Nested
    @DisplayName("with event source")
    class WithEventSource {

        @Test
        @DisplayName("should route event-based subscription to event handler")
        void shouldRouteToEventHandler() {
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> addCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("events"));
            when(eventSourceRegistry.hasEventSource("events")).thenReturn(true);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, eventStreamHandler, eventSourceRegistry);

            verify(subscriptionManager).setOnSubscriptionAdded(addCaptor.capture());

            SubscriptionKey key = SubscriptionKey.of("events", Map.of("id", "1"));

            addCaptor.getValue().accept(key, "session-1");

            verify(eventStreamHandler).addSubscriber(key, "session-1");
            verify(schedulerManager, never()).addSubscriber(any(), anyString(), any());
        }

        @Test
        @DisplayName("should route event-based removal to event handler")
        void shouldRouteRemovalToEventHandler() {
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> removeCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("events"));
            when(eventSourceRegistry.hasEventSource("events")).thenReturn(true);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, eventStreamHandler, eventSourceRegistry);

            verify(subscriptionManager).setOnSubscriptionRemoved(removeCaptor.capture());

            SubscriptionKey key = SubscriptionKey.of("events", Map.of("id", "1"));

            removeCaptor.getValue().accept(key, "session-1");

            verify(eventStreamHandler).removeSubscriber(key, "session-1");
            verify(schedulerManager, never()).removeSubscriber(any(), anyString());
        }

        @Test
        @DisplayName("should clean up from event handler on session termination")
        void shouldCleanUpFromEventHandlerOnTermination() {
            ArgumentCaptor<Consumer<StreamSession>> terminateCaptor =
                    ArgumentCaptor.forClass(Consumer.class);

            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("events"));
            when(eventSourceRegistry.hasEventSource("events")).thenReturn(true);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, eventStreamHandler, eventSourceRegistry);

            verify(sessionManager).setOnSessionTerminated(terminateCaptor.capture());

            StreamSession session = mock(StreamSession.class);
            SubscriptionKey key = SubscriptionKey.of("events", Map.of("id", "1"));
            when(session.getSubscriptions()).thenReturn(Set.of(key));
            when(session.getId()).thenReturn("session-1");

            terminateCaptor.getValue().accept(session);

            verify(eventStreamHandler).removeSubscriber(key, "session-1");
            verify(eventStreamHandler).removeSubscriberFromAll("session-1");
        }

        @Test
        @DisplayName("should route non-event resources to scheduler even with event source enabled")
        void shouldRouteNonEventResourcesToScheduler() {
            ArgumentCaptor<BiConsumer<SubscriptionKey, String>> addCaptor =
                    ArgumentCaptor.forClass(BiConsumer.class);

            when(eventSourceRegistry.getRegisteredResources()).thenReturn(Set.of("events"));
            when(eventSourceRegistry.hasEventSource("polling-resource")).thenReturn(false);

            new SimpliXStreamCoreConfiguration.StreamComponentWiring(
                    subscriptionManager, schedulerManager, sessionManager, properties,
                    collectorRegistry, eventStreamHandler, eventSourceRegistry);

            verify(subscriptionManager).setOnSubscriptionAdded(addCaptor.capture());

            SubscriptionKey key = SubscriptionKey.of("polling-resource", Map.of());
            when(collectorRegistry.findCollector("polling-resource")).thenReturn(Optional.empty());

            addCaptor.getValue().accept(key, "session-1");

            verify(schedulerManager).addSubscriber(eq(key), eq("session-1"), eq(Duration.ofSeconds(1)));
            verify(eventStreamHandler, never()).addSubscriber(any(), anyString());
        }
    }
}
