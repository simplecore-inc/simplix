package dev.simplecore.simplix.hibernate.cache.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for PendingEvictionCompletedEvent.
 */
@DisplayName("PendingEvictionCompletedEvent Tests")
class PendingEvictionCompletedEventTest {

    @Nested
    @DisplayName("Constructor tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create event with pending evictions")
        void shouldCreateEventWithPendingEvictions() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(
                    createTestEviction("1"),
                    createTestEviction("2")
            );

            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // Then
            assertThat(event.getPendingEvictions()).hasSize(2);
            assertThat(event.getEvictionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should handle null evictions list")
        void shouldHandleNullEvictionsList() {
            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, null);

            // Then
            assertThat(event.getPendingEvictions()).isEmpty();
            assertThat(event.getEvictionCount()).isZero();
        }

        @Test
        @DisplayName("Should handle empty evictions list")
        void shouldHandleEmptyEvictionsList() {
            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, new ArrayList<>());

            // Then
            assertThat(event.getPendingEvictions()).isEmpty();
            assertThat(event.getEvictionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Immutability tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should make defensive copy of evictions list")
        void shouldMakeDefensiveCopyOfEvictionsList() {
            // Given
            List<PendingEviction> originalList = new ArrayList<>();
            originalList.add(createTestEviction("1"));

            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, originalList);

            // When - modify original list
            originalList.add(createTestEviction("2"));

            // Then - event should not be affected
            assertThat(event.getPendingEvictions()).hasSize(1);
        }

        @Test
        @DisplayName("Should return unmodifiable list")
        void shouldReturnUnmodifiableList() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(createTestEviction("1"));
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // When/Then - should throw when trying to modify
            assertThatThrownBy(() -> event.getPendingEvictions().add(createTestEviction("2")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getEvictionCount() tests")
    class GetEvictionCountTests {

        @Test
        @DisplayName("Should return correct count")
        void shouldReturnCorrectCount() {
            // Given
            List<PendingEviction> evictions = Arrays.asList(
                    createTestEviction("1"),
                    createTestEviction("2"),
                    createTestEviction("3")
            );

            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(this, evictions);

            // Then
            assertThat(event.getEvictionCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("ApplicationEvent properties tests")
    class ApplicationEventTests {

        @Test
        @DisplayName("Should set source correctly")
        void shouldSetSourceCorrectly() {
            // Given
            Object source = new Object();
            List<PendingEviction> evictions = Arrays.asList(createTestEviction("1"));

            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(source, evictions);

            // Then
            assertThat(event.getSource()).isEqualTo(source);
        }

        @Test
        @DisplayName("Should have timestamp")
        void shouldHaveTimestamp() {
            // Given
            long beforeCreate = System.currentTimeMillis();

            // When
            PendingEvictionCompletedEvent event = new PendingEvictionCompletedEvent(
                    this, Arrays.asList(createTestEviction("1")));

            // Then
            assertThat(event.getTimestamp()).isGreaterThanOrEqualTo(beforeCreate);
        }
    }

    private PendingEviction createTestEviction(String entityId) {
        return PendingEviction.of(
                TestEntity.class,
                entityId,
                null,
                PendingEviction.EvictionOperation.UPDATE
        );
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
