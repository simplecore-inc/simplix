package dev.simplecore.simplix.stream.eventsource;

import dev.simplecore.simplix.core.event.Event;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventStreamHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventStreamHandler")
class EventStreamHandlerTest {

    @Mock
    private SimpliXStreamEventSourceRegistry eventSourceRegistry;

    @Mock
    private EventSubscriberRegistry subscriberRegistry;

    @Mock
    private BroadcastService broadcastService;

    @Mock
    private SimpliXStreamEventSource eventSource;

    private EventStreamHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EventStreamHandler(eventSourceRegistry, subscriberRegistry, broadcastService);
    }

    private Event createMockEvent(String eventType, Object payload) {
        Event event = mock(Event.class);
        lenient().when(event.getEventType()).thenReturn(eventType);
        lenient().when(event.getPayload()).thenReturn(payload);
        lenient().when(event.getEventId()).thenReturn("evt-1");
        return event;
    }

    @Nested
    @DisplayName("onEvent()")
    class OnEvent {

        @Test
        @DisplayName("should ignore null event")
        void shouldIgnoreNullEvent() {
            handler.onEvent(null);

            verifyNoInteractions(eventSourceRegistry, subscriberRegistry, broadcastService);
        }

        @Test
        @DisplayName("should ignore event with null event type")
        void shouldIgnoreEventWithNullEventType() {
            Event event = mock(Event.class);
            when(event.getEventType()).thenReturn(null);

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }

        @Test
        @DisplayName("should ignore event with no registered source")
        void shouldIgnoreEventWithNoRegisteredSource() {
            Event event = createMockEvent("UnknownEvent", "payload");
            when(eventSourceRegistry.findByEventType("UnknownEvent")).thenReturn(Optional.empty());

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }

        @Test
        @DisplayName("should ignore event that source does not support")
        void shouldIgnoreUnsupportedEvent() {
            Event event = createMockEvent("StockChanged", "payload");
            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(false);

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }

        @Test
        @DisplayName("should skip when no subscribers for key")
        void shouldSkipWhenNoSubscribers() {
            Event event = createMockEvent("StockChanged", "payload");
            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(true);
            when(eventSource.getResource()).thenReturn("stock");
            when(eventSource.extractParams("payload")).thenReturn(Map.of("symbol", "AAPL"));
            when(subscriberRegistry.getSubscribers(any(SubscriptionKey.class))).thenReturn(Set.of());

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }

        @Test
        @DisplayName("should skip when extracted data is null")
        void shouldSkipWhenExtractedDataIsNull() {
            Event event = createMockEvent("StockChanged", "payload");
            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(true);
            when(eventSource.getResource()).thenReturn("stock");
            when(eventSource.extractParams("payload")).thenReturn(Map.of("symbol", "AAPL"));
            when(subscriberRegistry.getSubscribers(any(SubscriptionKey.class))).thenReturn(Set.of("sess-1"));
            when(eventSource.extractData("payload")).thenReturn(null);

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }

        @Test
        @DisplayName("should broadcast data to subscribers")
        void shouldBroadcastDataToSubscribers() {
            Map<String, Object> params = Map.of("symbol", "AAPL");
            Object data = Map.of("price", 150.0);
            Event event = createMockEvent("StockChanged", "payload");

            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(true);
            when(eventSource.getResource()).thenReturn("stock");
            when(eventSource.extractParams("payload")).thenReturn(params);
            when(subscriberRegistry.getSubscribers(any(SubscriptionKey.class))).thenReturn(Set.of("sess-1", "sess-2"));
            when(eventSource.extractData("payload")).thenReturn(data);

            handler.onEvent(event);

            verify(broadcastService).broadcast(any(SubscriptionKey.class), any(StreamMessage.class),
                    eq(Set.of("sess-1", "sess-2")));
        }

        @Test
        @DisplayName("should handle null extracted params as empty map")
        void shouldHandleNullExtractedParams() {
            Object data = Map.of("price", 100.0);
            Event event = createMockEvent("StockChanged", "payload");

            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(true);
            when(eventSource.getResource()).thenReturn("stock");
            when(eventSource.extractParams("payload")).thenReturn(null);
            when(subscriberRegistry.getSubscribers(any(SubscriptionKey.class))).thenReturn(Set.of("sess-1"));
            when(eventSource.extractData("payload")).thenReturn(data);

            handler.onEvent(event);

            verify(broadcastService).broadcast(any(SubscriptionKey.class), any(StreamMessage.class),
                    eq(Set.of("sess-1")));
        }

        @Test
        @DisplayName("should handle exception during processing gracefully")
        void shouldHandleExceptionGracefully() {
            Event event = createMockEvent("StockChanged", "payload");
            when(eventSourceRegistry.findByEventType("StockChanged")).thenReturn(Optional.of(eventSource));
            when(eventSource.supports("StockChanged", "payload")).thenReturn(true);
            when(eventSource.extractParams("payload")).thenThrow(new RuntimeException("extraction error"));

            handler.onEvent(event);

            verifyNoInteractions(broadcastService);
        }
    }

    @Nested
    @DisplayName("subscriber management")
    class SubscriberManagement {

        @Test
        @DisplayName("addSubscriber should delegate to registry")
        void addSubscriberShouldDelegateToRegistry() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            handler.addSubscriber(key, "sess-1");

            verify(subscriberRegistry).addSubscriber(key, "sess-1");
        }

        @Test
        @DisplayName("removeSubscriber should delegate to registry")
        void removeSubscriberShouldDelegateToRegistry() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            handler.removeSubscriber(key, "sess-1");

            verify(subscriberRegistry).removeSubscriber(key, "sess-1");
        }

        @Test
        @DisplayName("removeSubscriberFromAll should delegate to registry")
        void removeSubscriberFromAllShouldDelegateToRegistry() {
            handler.removeSubscriberFromAll("sess-1");

            verify(subscriberRegistry).removeSubscriberFromAll("sess-1");
        }

        @Test
        @DisplayName("getSubscriberCount should delegate to registry")
        void getSubscriberCountShouldDelegateToRegistry() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            when(subscriberRegistry.getSubscriberCount(key)).thenReturn(5);

            int count = handler.getSubscriberCount(key);

            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("isEventBasedResource()")
    class IsEventBasedResource {

        @Test
        @DisplayName("should return true when resource has event source")
        void shouldReturnTrueWhenResourceHasEventSource() {
            when(eventSourceRegistry.hasEventSource("stock")).thenReturn(true);

            assertThat(handler.isEventBasedResource("stock")).isTrue();
        }

        @Test
        @DisplayName("should return false when resource has no event source")
        void shouldReturnFalseWhenNoEventSource() {
            when(eventSourceRegistry.hasEventSource("unknown")).thenReturn(false);

            assertThat(handler.isEventBasedResource("unknown")).isFalse();
        }
    }
}
