package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinSelectorTest {

    private RoundRobinSelector selector;

    @BeforeEach
    void setUp() {
        selector = new RoundRobinSelector();
    }

    @Test
    @DisplayName("should select first batch from beginning")
    void shouldSelectFirstBatch() {
        List<String> items = List.of("A", "B", "C", "D", "E");
        List<String> batch = selector.selectBatch(items, 2);
        assertThat(batch).containsExactly("A", "B");
    }

    @Test
    @DisplayName("should advance cursor for next batch")
    void shouldAdvanceCursor() {
        List<String> items = List.of("A", "B", "C", "D", "E");
        selector.selectBatch(items, 2); // A, B
        List<String> batch2 = selector.selectBatch(items, 2); // C, D
        assertThat(batch2).containsExactly("C", "D");
    }

    @Test
    @DisplayName("should wrap around at end")
    void shouldWrapAround() {
        List<String> items = List.of("A", "B", "C", "D", "E");
        selector.selectBatch(items, 2); // A, B (cursor -> 2)
        selector.selectBatch(items, 2); // C, D (cursor -> 4)
        List<String> batch3 = selector.selectBatch(items, 2); // E, A (cursor -> 1)
        assertThat(batch3).containsExactly("E", "A");
    }

    @Test
    @DisplayName("should handle batch size larger than list")
    void shouldHandleLargeBatchSize() {
        List<String> items = List.of("A", "B");
        List<String> batch = selector.selectBatch(items, 5);
        assertThat(batch).containsExactly("A", "B");
    }

    @Test
    @DisplayName("should return empty for empty list")
    void shouldReturnEmptyForEmptyList() {
        List<String> batch = selector.selectBatch(List.of(), 3);
        assertThat(batch).isEmpty();
    }

    @Test
    @DisplayName("should return empty for null list")
    void shouldReturnEmptyForNull() {
        List<String> batch = selector.selectBatch(null, 3);
        assertThat(batch).isEmpty();
    }

    @Test
    @DisplayName("should reset cursor")
    void shouldResetCursor() {
        List<String> items = List.of("A", "B", "C");
        selector.selectBatch(items, 2);
        assertThat(selector.getCursorPosition()).isNotEqualTo(0);
        selector.resetCursor();
        assertThat(selector.getCursorPosition()).isEqualTo(0);
    }
}
