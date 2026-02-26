package dev.simplecore.simplix.stream.core.subscription;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SubscriptionDiff.
 */
@DisplayName("SubscriptionDiff")
class SubscriptionDiffTest {

    private final SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
    private final SubscriptionKey key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));
    private final SubscriptionKey key3 = SubscriptionKey.of("forex", Map.of("pair", "EUR/USD"));

    @Nested
    @DisplayName("calculate()")
    class CalculateMethod {

        @Test
        @DisplayName("should detect additions")
        void shouldDetectAdditions() {
            Set<SubscriptionKey> current = Set.of();
            Set<SubscriptionKey> requested = Set.of(key1, key2);

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertEquals(2, diff.added().size());
            assertTrue(diff.added().contains(key1));
            assertTrue(diff.added().contains(key2));
            assertTrue(diff.removed().isEmpty());
            assertTrue(diff.unchanged().isEmpty());
        }

        @Test
        @DisplayName("should detect removals")
        void shouldDetectRemovals() {
            Set<SubscriptionKey> current = Set.of(key1, key2);
            Set<SubscriptionKey> requested = Set.of();

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertTrue(diff.added().isEmpty());
            assertEquals(2, diff.removed().size());
            assertTrue(diff.removed().contains(key1));
            assertTrue(diff.removed().contains(key2));
            assertTrue(diff.unchanged().isEmpty());
        }

        @Test
        @DisplayName("should detect unchanged subscriptions")
        void shouldDetectUnchanged() {
            Set<SubscriptionKey> current = Set.of(key1, key2);
            Set<SubscriptionKey> requested = Set.of(key1, key2);

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertTrue(diff.added().isEmpty());
            assertTrue(diff.removed().isEmpty());
            assertEquals(2, diff.unchanged().size());
        }

        @Test
        @DisplayName("should detect mixed changes")
        void shouldDetectMixedChanges() {
            Set<SubscriptionKey> current = Set.of(key1, key2);
            Set<SubscriptionKey> requested = Set.of(key2, key3);

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertEquals(1, diff.added().size());
            assertTrue(diff.added().contains(key3));
            assertEquals(1, diff.removed().size());
            assertTrue(diff.removed().contains(key1));
            assertEquals(1, diff.unchanged().size());
            assertTrue(diff.unchanged().contains(key2));
        }

        @Test
        @DisplayName("should handle empty sets")
        void shouldHandleEmptySets() {
            Set<SubscriptionKey> current = Set.of();
            Set<SubscriptionKey> requested = Set.of();

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertTrue(diff.added().isEmpty());
            assertTrue(diff.removed().isEmpty());
            assertTrue(diff.unchanged().isEmpty());
            assertFalse(diff.hasChanges());
        }
    }

    @Nested
    @DisplayName("hasChanges()")
    class HasChangesMethod {

        @Test
        @DisplayName("should return true when there are additions")
        void shouldReturnTrueForAdditions() {
            SubscriptionDiff diff = SubscriptionDiff.calculate(Set.of(), Set.of(key1));

            assertTrue(diff.hasChanges());
        }

        @Test
        @DisplayName("should return true when there are removals")
        void shouldReturnTrueForRemovals() {
            SubscriptionDiff diff = SubscriptionDiff.calculate(Set.of(key1), Set.of());

            assertTrue(diff.hasChanges());
        }

        @Test
        @DisplayName("should return false when no changes")
        void shouldReturnFalseForNoChanges() {
            SubscriptionDiff diff = SubscriptionDiff.calculate(Set.of(key1), Set.of(key1));

            assertFalse(diff.hasChanges());
        }
    }

    @Nested
    @DisplayName("changeCount()")
    class ChangeCountMethod {

        @Test
        @DisplayName("should count additions and removals")
        void shouldCountAdditionsAndRemovals() {
            Set<SubscriptionKey> current = Set.of(key1, key2);
            Set<SubscriptionKey> requested = Set.of(key2, key3);

            SubscriptionDiff diff = SubscriptionDiff.calculate(current, requested);

            assertEquals(2, diff.changeCount()); // 1 added + 1 removed
        }

        @Test
        @DisplayName("should return zero for no changes")
        void shouldReturnZeroForNoChanges() {
            SubscriptionDiff diff = SubscriptionDiff.calculate(Set.of(key1), Set.of(key1));

            assertEquals(0, diff.changeCount());
        }
    }
}
