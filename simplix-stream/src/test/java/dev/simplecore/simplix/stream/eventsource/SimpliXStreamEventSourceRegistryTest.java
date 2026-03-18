package dev.simplecore.simplix.stream.eventsource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimpliXStreamEventSourceRegistry.
 */
@DisplayName("SimpliXStreamEventSourceRegistry")
class SimpliXStreamEventSourceRegistryTest {

    private SimpliXStreamEventSourceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpliXStreamEventSourceRegistry();
    }

    private SimpliXStreamEventSource createMockSource(String resource, String eventType) {
        SimpliXStreamEventSource source = mock(SimpliXStreamEventSource.class);
        when(source.getResource()).thenReturn(resource);
        when(source.getEventType()).thenReturn(eventType);
        return source;
    }

    @Nested
    @DisplayName("constructor with sources")
    class ConstructorWithSources {

        @Test
        @DisplayName("should register discovered sources")
        void shouldRegisterDiscoveredSources() {
            SimpliXStreamEventSource source1 = createMockSource("stock", "StockChanged");
            SimpliXStreamEventSource source2 = createMockSource("forex", "ForexChanged");

            SimpliXStreamEventSourceRegistry reg = new SimpliXStreamEventSourceRegistry(List.of(source1, source2));

            assertThat(reg.size()).isEqualTo(2);
            assertThat(reg.hasEventSource("stock")).isTrue();
            assertThat(reg.hasEventSource("forex")).isTrue();
        }

        @Test
        @DisplayName("should handle null sources list")
        void shouldHandleNullSourcesList() {
            SimpliXStreamEventSourceRegistry reg = new SimpliXStreamEventSourceRegistry(null);

            assertThat(reg.size()).isZero();
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register source by resource and event type")
        void shouldRegisterByResourceAndEventType() {
            SimpliXStreamEventSource source = createMockSource("stock", "StockChanged");

            registry.register(source);

            assertThat(registry.findByResource("stock")).isPresent();
            assertThat(registry.findByEventType("StockChanged")).isPresent();
        }

        @Test
        @DisplayName("should skip source with null resource")
        void shouldSkipSourceWithNullResource() {
            SimpliXStreamEventSource source = createMockSource(null, "SomeEvent");

            registry.register(source);

            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should skip source with blank resource")
        void shouldSkipSourceWithBlankResource() {
            SimpliXStreamEventSource source = createMockSource("  ", "SomeEvent");

            registry.register(source);

            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should skip source with null event type")
        void shouldSkipSourceWithNullEventType() {
            SimpliXStreamEventSource source = createMockSource("stock", null);

            registry.register(source);

            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should skip source with blank event type")
        void shouldSkipSourceWithBlankEventType() {
            SimpliXStreamEventSource source = createMockSource("stock", "  ");

            registry.register(source);

            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should replace existing source for same resource")
        void shouldReplaceExistingSourceForSameResource() {
            SimpliXStreamEventSource source1 = createMockSource("stock", "StockV1");
            SimpliXStreamEventSource source2 = createMockSource("stock", "StockV2");

            registry.register(source1);
            registry.register(source2);

            assertThat(registry.size()).isEqualTo(1);
            assertThat(registry.findByEventType("StockV2")).isPresent();
        }
    }

    @Nested
    @DisplayName("unregister()")
    class Unregister {

        @Test
        @DisplayName("should remove source by resource name")
        void shouldRemoveByResourceName() {
            SimpliXStreamEventSource source = createMockSource("stock", "StockChanged");
            registry.register(source);

            SimpliXStreamEventSource removed = registry.unregister("stock");

            assertThat(removed).isNotNull();
            assertThat(registry.hasEventSource("stock")).isFalse();
            assertThat(registry.hasEventSourceForEventType("StockChanged")).isFalse();
        }

        @Test
        @DisplayName("should return null when resource not found")
        void shouldReturnNullWhenNotFound() {
            SimpliXStreamEventSource removed = registry.unregister("nonexistent");

            assertThat(removed).isNull();
        }
    }

    @Nested
    @DisplayName("findByResource()")
    class FindByResource {

        @Test
        @DisplayName("should return source when found")
        void shouldReturnSourceWhenFound() {
            SimpliXStreamEventSource source = createMockSource("stock", "StockChanged");
            registry.register(source);

            Optional<SimpliXStreamEventSource> result = registry.findByResource("stock");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<SimpliXStreamEventSource> result = registry.findByResource("nonexistent");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByEventType()")
    class FindByEventType {

        @Test
        @DisplayName("should return source when found")
        void shouldReturnSourceWhenFound() {
            SimpliXStreamEventSource source = createMockSource("stock", "StockChanged");
            registry.register(source);

            Optional<SimpliXStreamEventSource> result = registry.findByEventType("StockChanged");

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<SimpliXStreamEventSource> result = registry.findByEventType("Unknown");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasEventSource()")
    class HasEventSource {

        @Test
        @DisplayName("should return true when resource registered")
        void shouldReturnTrueWhenRegistered() {
            registry.register(createMockSource("stock", "StockChanged"));

            assertThat(registry.hasEventSource("stock")).isTrue();
        }

        @Test
        @DisplayName("should return false when resource not registered")
        void shouldReturnFalseWhenNotRegistered() {
            assertThat(registry.hasEventSource("unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("hasEventSourceForEventType()")
    class HasEventSourceForEventType {

        @Test
        @DisplayName("should return true when event type registered")
        void shouldReturnTrueWhenRegistered() {
            registry.register(createMockSource("stock", "StockChanged"));

            assertThat(registry.hasEventSourceForEventType("StockChanged")).isTrue();
        }

        @Test
        @DisplayName("should return false when event type not registered")
        void shouldReturnFalseWhenNotRegistered() {
            assertThat(registry.hasEventSourceForEventType("Unknown")).isFalse();
        }
    }

    @Nested
    @DisplayName("collection methods")
    class CollectionMethods {

        @Test
        @DisplayName("getRegisteredResources() should return all resource names")
        void shouldReturnAllResources() {
            registry.register(createMockSource("stock", "StockChanged"));
            registry.register(createMockSource("forex", "ForexChanged"));

            assertThat(registry.getRegisteredResources()).containsExactlyInAnyOrder("stock", "forex");
        }

        @Test
        @DisplayName("getRegisteredEventTypes() should return all event types")
        void shouldReturnAllEventTypes() {
            registry.register(createMockSource("stock", "StockChanged"));
            registry.register(createMockSource("forex", "ForexChanged"));

            assertThat(registry.getRegisteredEventTypes())
                    .containsExactlyInAnyOrder("StockChanged", "ForexChanged");
        }

        @Test
        @DisplayName("getEventSources() should return all sources")
        void shouldReturnAllSources() {
            registry.register(createMockSource("stock", "StockChanged"));
            registry.register(createMockSource("forex", "ForexChanged"));

            assertThat(registry.getEventSources()).hasSize(2);
        }

        @Test
        @DisplayName("size() should return correct count")
        void shouldReturnCorrectSize() {
            assertThat(registry.size()).isZero();

            registry.register(createMockSource("stock", "StockChanged"));
            assertThat(registry.size()).isEqualTo(1);

            registry.register(createMockSource("forex", "ForexChanged"));
            assertThat(registry.size()).isEqualTo(2);
        }
    }
}
