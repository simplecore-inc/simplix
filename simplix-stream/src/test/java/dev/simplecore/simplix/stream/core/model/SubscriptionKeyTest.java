package dev.simplecore.simplix.stream.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubscriptionKey.
 */
@DisplayName("SubscriptionKey")
class SubscriptionKeyTest {

    @Nested
    @DisplayName("of()")
    class OfMethod {

        @Test
        @DisplayName("should create key with resource and params")
        void shouldCreateKeyWithResourceAndParams() {
            Map<String, Object> params = Map.of("symbol", "AAPL");

            SubscriptionKey key = SubscriptionKey.of("stock-price", params);

            assertEquals("stock-price", key.getResource());
            assertEquals(params, key.getParams());
            assertNotNull(key.getParamsHash());
            assertFalse(key.getParamsHash().isEmpty());
        }

        @Test
        @DisplayName("should create key with empty params")
        void shouldCreateKeyWithEmptyParams() {
            SubscriptionKey key = SubscriptionKey.of("heartbeat", Collections.emptyMap());

            assertEquals("heartbeat", key.getResource());
            assertTrue(key.getParams().isEmpty());
            assertEquals("empty", key.getParamsHash());
        }

        @Test
        @DisplayName("should create key with null params as empty")
        void shouldCreateKeyWithNullParamsAsEmpty() {
            SubscriptionKey key = SubscriptionKey.of("heartbeat", null);

            assertEquals("heartbeat", key.getResource());
            assertTrue(key.getParams().isEmpty());
            assertEquals("empty", key.getParamsHash());
        }

        @Test
        @DisplayName("should throw exception for null resource")
        void shouldThrowExceptionForNullResource() {
            assertThrows(IllegalArgumentException.class,
                () -> SubscriptionKey.of(null, Map.of()));
        }

        @Test
        @DisplayName("should throw exception for blank resource")
        void shouldThrowExceptionForBlankResource() {
            assertThrows(IllegalArgumentException.class,
                () -> SubscriptionKey.of("  ", Map.of()));
        }

        @Test
        @DisplayName("should generate same hash for same params")
        void shouldGenerateSameHashForSameParams() {
            Map<String, Object> params1 = Map.of("symbol", "AAPL", "interval", 1000);
            Map<String, Object> params2 = Map.of("interval", 1000, "symbol", "AAPL");

            SubscriptionKey key1 = SubscriptionKey.of("stock", params1);
            SubscriptionKey key2 = SubscriptionKey.of("stock", params2);

            assertEquals(key1.getParamsHash(), key2.getParamsHash());
            assertEquals(key1, key2);
        }

        @Test
        @DisplayName("should generate different hash for different params")
        void shouldGenerateDifferentHashForDifferentParams() {
            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));

            assertNotEquals(key1.getParamsHash(), key2.getParamsHash());
            assertNotEquals(key1, key2);
        }
    }

    @Nested
    @DisplayName("fromString()")
    class FromStringMethod {

        @Test
        @DisplayName("should parse valid key string")
        void shouldParseValidKeyString() {
            SubscriptionKey original = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            String keyString = original.toKeyString();

            SubscriptionKey parsed = SubscriptionKey.fromString(keyString);

            assertEquals(original.getResource(), parsed.getResource());
            assertEquals(original.getParamsHash(), parsed.getParamsHash());
            assertEquals(original.toKeyString(), parsed.toKeyString());
        }

        @Test
        @DisplayName("should throw exception for null key string")
        void shouldThrowExceptionForNullKeyString() {
            assertThrows(IllegalArgumentException.class,
                () -> SubscriptionKey.fromString(null));
        }

        @Test
        @DisplayName("should throw exception for invalid format")
        void shouldThrowExceptionForInvalidFormat() {
            assertThrows(IllegalArgumentException.class,
                () -> SubscriptionKey.fromString("no-colon"));
        }
    }

    @Nested
    @DisplayName("toKeyString()")
    class ToKeyStringMethod {

        @Test
        @DisplayName("should return resource:hash format")
        void shouldReturnResourceHashFormat() {
            SubscriptionKey key = SubscriptionKey.of("stock-price", Map.of("symbol", "AAPL"));

            String keyString = key.toKeyString();

            assertTrue(keyString.startsWith("stock-price:"));
            assertTrue(keyString.contains(":"));
        }

        @Test
        @DisplayName("should return consistent key string")
        void shouldReturnConsistentKeyString() {
            SubscriptionKey key = SubscriptionKey.of("test", Map.of("a", 1));

            assertEquals(key.toKeyString(), key.toKeyString());
        }
    }

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal for same resource and params")
        void shouldBeEqualForSameResourceAndParams() {
            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            assertEquals(key1, key2);
            assertEquals(key1.hashCode(), key2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different resources")
        void shouldNotBeEqualForDifferentResources() {
            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("forex", Map.of("symbol", "AAPL"));

            assertNotEquals(key1, key2);
        }

        @Test
        @DisplayName("should not be equal for different params")
        void shouldNotBeEqualForDifferentParams() {
            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));

            assertNotEquals(key1, key2);
        }
    }

    @Nested
    @DisplayName("params immutability")
    class ParamsImmutability {

        @Test
        @DisplayName("should return immutable params")
        void shouldReturnImmutableParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("symbol", "AAPL");

            SubscriptionKey key = SubscriptionKey.of("stock", params);

            assertThrows(UnsupportedOperationException.class,
                () -> key.getParams().put("new", "value"));
        }

        @Test
        @DisplayName("should not be affected by original map changes")
        void shouldNotBeAffectedByOriginalMapChanges() {
            Map<String, Object> params = new HashMap<>();
            params.put("symbol", "AAPL");

            SubscriptionKey key = SubscriptionKey.of("stock", params);
            params.put("extra", "value");

            assertFalse(key.getParams().containsKey("extra"));
        }
    }
}
