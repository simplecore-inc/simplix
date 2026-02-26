package dev.simplecore.simplix.stream.collector;

import dev.simplecore.simplix.stream.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SimpliXStreamDataCollectorRegistry.
 */
@DisplayName("SimpliXStreamDataCollectorRegistry")
class SimpliXStreamDataCollectorRegistryTest {

    private SimpliXStreamDataCollectorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpliXStreamDataCollectorRegistry();
    }

    private SimpliXStreamDataCollector createCollector(String resource) {
        return new SimpliXStreamDataCollector() {
            @Override
            public String getResource() {
                return resource;
            }

            @Override
            public Object collect(Map<String, Object> params) {
                return Map.of("data", "test");
            }
        };
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should initialize with discovered collectors")
        void shouldInitializeWithDiscoveredCollectors() {
            SimpliXStreamDataCollector collector1 = createCollector("stock");
            SimpliXStreamDataCollector collector2 = createCollector("forex");

            SimpliXStreamDataCollectorRegistry registryWithCollectors = new SimpliXStreamDataCollectorRegistry(List.of(collector1, collector2));

            assertEquals(2, registryWithCollectors.size());
            assertTrue(registryWithCollectors.hasCollector("stock"));
            assertTrue(registryWithCollectors.hasCollector("forex"));
        }

        @Test
        @DisplayName("should handle null collector list")
        void shouldHandleNullCollectorList() {
            SimpliXStreamDataCollectorRegistry registryWithNull = new SimpliXStreamDataCollectorRegistry(null);

            assertEquals(0, registryWithNull.size());
        }

        @Test
        @DisplayName("should initialize empty with default constructor")
        void shouldInitializeEmptyWithDefaultConstructor() {
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("register()")
    class RegisterMethod {

        @Test
        @DisplayName("should register collector")
        void shouldRegisterCollector() {
            SimpliXStreamDataCollector collector = createCollector("stock");

            registry.register(collector);

            assertEquals(1, registry.size());
            assertTrue(registry.hasCollector("stock"));
        }

        @Test
        @DisplayName("should skip collector with null resource")
        void shouldSkipCollectorWithNullResource() {
            SimpliXStreamDataCollector collector = createCollector(null);

            registry.register(collector);

            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("should skip collector with blank resource")
        void shouldSkipCollectorWithBlankResource() {
            SimpliXStreamDataCollector collector = createCollector("   ");

            registry.register(collector);

            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("should replace existing collector")
        void shouldReplaceExistingCollector() {
            SimpliXStreamDataCollector collector1 = createCollector("stock");
            SimpliXStreamDataCollector collector2 = createCollector("stock");

            registry.register(collector1);
            registry.register(collector2);

            assertEquals(1, registry.size());
            assertSame(collector2, registry.getCollector("stock"));
        }
    }

    @Nested
    @DisplayName("unregister()")
    class UnregisterMethod {

        @Test
        @DisplayName("should remove registered collector")
        void shouldRemoveRegisteredCollector() {
            SimpliXStreamDataCollector collector = createCollector("stock");
            registry.register(collector);

            SimpliXStreamDataCollector removed = registry.unregister("stock");

            assertSame(collector, removed);
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("should return null for non-existing collector")
        void shouldReturnNullForNonExistingCollector() {
            SimpliXStreamDataCollector removed = registry.unregister("nonexistent");

            assertNull(removed);
        }
    }

    @Nested
    @DisplayName("getCollector()")
    class GetCollectorMethod {

        @Test
        @DisplayName("should return collector when found")
        void shouldReturnCollectorWhenFound() {
            SimpliXStreamDataCollector collector = createCollector("stock");
            registry.register(collector);

            SimpliXStreamDataCollector result = registry.getCollector("stock");

            assertSame(collector, result);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            assertThrows(ResourceNotFoundException.class,
                    () -> registry.getCollector("nonexistent"));
        }
    }

    @Nested
    @DisplayName("findCollector()")
    class FindCollectorMethod {

        @Test
        @DisplayName("should return optional with collector when found")
        void shouldReturnOptionalWithCollectorWhenFound() {
            SimpliXStreamDataCollector collector = createCollector("stock");
            registry.register(collector);

            Optional<SimpliXStreamDataCollector> result = registry.findCollector("stock");

            assertTrue(result.isPresent());
            assertSame(collector, result.get());
        }

        @Test
        @DisplayName("should return empty optional when not found")
        void shouldReturnEmptyOptionalWhenNotFound() {
            Optional<SimpliXStreamDataCollector> result = registry.findCollector("nonexistent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("hasCollector()")
    class HasCollectorMethod {

        @Test
        @DisplayName("should return true when collector exists")
        void shouldReturnTrueWhenCollectorExists() {
            registry.register(createCollector("stock"));

            assertTrue(registry.hasCollector("stock"));
        }

        @Test
        @DisplayName("should return false when collector does not exist")
        void shouldReturnFalseWhenCollectorDoesNotExist() {
            assertFalse(registry.hasCollector("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getRegisteredResources()")
    class GetRegisteredResourcesMethod {

        @Test
        @DisplayName("should return all registered resource names")
        void shouldReturnAllRegisteredResourceNames() {
            registry.register(createCollector("stock"));
            registry.register(createCollector("forex"));

            var resources = registry.getRegisteredResources();

            assertEquals(2, resources.size());
            assertTrue(resources.contains("stock"));
            assertTrue(resources.contains("forex"));
        }
    }

    @Nested
    @DisplayName("getCollectors()")
    class GetCollectorsMethod {

        @Test
        @DisplayName("should return all registered collectors")
        void shouldReturnAllRegisteredCollectors() {
            SimpliXStreamDataCollector collector1 = createCollector("stock");
            SimpliXStreamDataCollector collector2 = createCollector("forex");
            registry.register(collector1);
            registry.register(collector2);

            var collectors = registry.getCollectors();

            assertEquals(2, collectors.size());
            assertTrue(collectors.contains(collector1));
            assertTrue(collectors.contains(collector2));
        }
    }

    @Nested
    @DisplayName("size()")
    class SizeMethod {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            assertEquals(0, registry.size());

            registry.register(createCollector("stock"));
            assertEquals(1, registry.size());

            registry.register(createCollector("forex"));
            assertEquals(2, registry.size());

            registry.unregister("stock");
            assertEquals(1, registry.size());
        }
    }
}
