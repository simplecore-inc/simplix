package dev.simplecore.simplix.stream.eventsource;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EventSubscriberRegistry.
 */
@DisplayName("EventSubscriberRegistry")
class EventSubscriberRegistryTest {

    private EventSubscriberRegistry registry;
    private SubscriptionKey key1;
    private SubscriptionKey key2;
    private SubscriptionKey keyEmptyParams;

    @BeforeEach
    void setUp() {
        registry = new EventSubscriberRegistry();
        key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
        key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));
        keyEmptyParams = SubscriptionKey.of("stock", Map.of());
    }

    @Nested
    @DisplayName("addSubscriber()")
    class AddSubscriber {

        @Test
        @DisplayName("should add subscriber and return true")
        void shouldAddSubscriberAndReturnTrue() {
            boolean added = registry.addSubscriber(key1, "sess-1");

            assertThat(added).isTrue();
            assertThat(registry.getSubscriberCount(key1)).isEqualTo(1);
        }

        @Test
        @DisplayName("should return false for duplicate subscriber")
        void shouldReturnFalseForDuplicate() {
            registry.addSubscriber(key1, "sess-1");

            boolean added = registry.addSubscriber(key1, "sess-1");

            assertThat(added).isFalse();
            assertThat(registry.getSubscriberCount(key1)).isEqualTo(1);
        }

        @Test
        @DisplayName("should support multiple subscribers for same key")
        void shouldSupportMultipleSubscribers() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key1, "sess-2");

            assertThat(registry.getSubscriberCount(key1)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("removeSubscriber()")
    class RemoveSubscriber {

        @Test
        @DisplayName("should remove subscriber and return true")
        void shouldRemoveSubscriberAndReturnTrue() {
            registry.addSubscriber(key1, "sess-1");

            boolean removed = registry.removeSubscriber(key1, "sess-1");

            assertThat(removed).isTrue();
            assertThat(registry.getSubscriberCount(key1)).isZero();
        }

        @Test
        @DisplayName("should return false when subscriber not found")
        void shouldReturnFalseWhenNotFound() {
            boolean removed = registry.removeSubscriber(key1, "sess-1");

            assertThat(removed).isFalse();
        }

        @Test
        @DisplayName("should return false when key not found")
        void shouldReturnFalseWhenKeyNotFound() {
            boolean removed = registry.removeSubscriber(key1, "nonexistent");

            assertThat(removed).isFalse();
        }

        @Test
        @DisplayName("should clean up empty entries after last subscriber removed")
        void shouldCleanUpEmptyEntries() {
            registry.addSubscriber(key1, "sess-1");

            registry.removeSubscriber(key1, "sess-1");

            assertThat(registry.size()).isZero();
            assertThat(registry.hasSubscribers(key1)).isFalse();
        }
    }

    @Nested
    @DisplayName("removeSubscriberFromAll()")
    class RemoveSubscriberFromAll {

        @Test
        @DisplayName("should remove subscriber from all keys")
        void shouldRemoveFromAllKeys() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-1");
            registry.addSubscriber(key1, "sess-2");

            registry.removeSubscriberFromAll("sess-1");

            assertThat(registry.getSubscriberCount(key1)).isEqualTo(1);
            assertThat(registry.getSubscriberCount(key2)).isZero();
        }

        @Test
        @DisplayName("should clean up empty entries after removal")
        void shouldCleanUpEmptyEntries() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-1");

            registry.removeSubscriberFromAll("sess-1");

            assertThat(registry.size()).isZero();
        }

        @Test
        @DisplayName("should handle non-existent subscriber gracefully")
        void shouldHandleNonExistentSubscriber() {
            registry.removeSubscriberFromAll("nonexistent");

            assertThat(registry.size()).isZero();
        }
    }

    @Nested
    @DisplayName("getSubscribers()")
    class GetSubscribers {

        @Test
        @DisplayName("should return subscribers for exact key match")
        void shouldReturnSubscribersForExactMatch() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key1, "sess-2");

            Set<String> subscribers = registry.getSubscribers(key1);

            assertThat(subscribers).containsExactlyInAnyOrder("sess-1", "sess-2");
        }

        @Test
        @DisplayName("should return empty set when no subscribers")
        void shouldReturnEmptyWhenNoSubscribers() {
            Set<String> subscribers = registry.getSubscribers(key1);

            assertThat(subscribers).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            registry.addSubscriber(key1, "sess-1");

            Set<String> subscribers = registry.getSubscribers(key1);

            assertThat(subscribers).isUnmodifiable();
        }

        @Test
        @DisplayName("should return all resource subscribers for empty params key")
        void shouldReturnAllResourceSubscribersForEmptyParamsKey() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-2");

            Set<String> subscribers = registry.getSubscribers(keyEmptyParams);

            assertThat(subscribers).containsExactlyInAnyOrder("sess-1", "sess-2");
        }
    }

    @Nested
    @DisplayName("getSubscribersByResource()")
    class GetSubscribersByResource {

        @Test
        @DisplayName("should return all subscribers for resource across all param variations")
        void shouldReturnAllSubscribersForResource() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-2");

            Set<String> subscribers = registry.getSubscribersByResource("stock");

            assertThat(subscribers).containsExactlyInAnyOrder("sess-1", "sess-2");
        }

        @Test
        @DisplayName("should return empty set for unknown resource")
        void shouldReturnEmptyForUnknownResource() {
            Set<String> subscribers = registry.getSubscribersByResource("unknown");

            assertThat(subscribers).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasSubscribers()")
    class HasSubscribers {

        @Test
        @DisplayName("should return true when subscribers exist")
        void shouldReturnTrueWhenExists() {
            registry.addSubscriber(key1, "sess-1");

            assertThat(registry.hasSubscribers(key1)).isTrue();
        }

        @Test
        @DisplayName("should return false when no subscribers")
        void shouldReturnFalseWhenEmpty() {
            assertThat(registry.hasSubscribers(key1)).isFalse();
        }
    }

    @Nested
    @DisplayName("getSubscriptionKeys()")
    class GetSubscriptionKeys {

        @Test
        @DisplayName("should return all tracked keys")
        void shouldReturnAllTrackedKeys() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-2");

            Set<SubscriptionKey> keys = registry.getSubscriptionKeys();

            assertThat(keys).containsExactlyInAnyOrder(key1, key2);
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            Set<SubscriptionKey> keys = registry.getSubscriptionKeys();

            assertThat(keys).isUnmodifiable();
        }
    }

    @Nested
    @DisplayName("getTotalSubscriptionCount()")
    class GetTotalSubscriptionCount {

        @Test
        @DisplayName("should return total count across all keys")
        void shouldReturnTotalCount() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key1, "sess-2");
            registry.addSubscriber(key2, "sess-3");

            assertThat(registry.getTotalSubscriptionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return zero when empty")
        void shouldReturnZeroWhenEmpty() {
            assertThat(registry.getTotalSubscriptionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("should remove all subscriptions")
        void shouldRemoveAllSubscriptions() {
            registry.addSubscriber(key1, "sess-1");
            registry.addSubscriber(key2, "sess-2");

            registry.clear();

            assertThat(registry.size()).isZero();
            assertThat(registry.getTotalSubscriptionCount()).isZero();
        }
    }
}
