package dev.simplecore.simplix.excel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StreamingCollection")
class StreamingCollectionTest {

    @Nested
    @DisplayName("Constructor with auto-size detection")
    class AutoSizeConstructorTests {

        @Test
        @DisplayName("should query first page to determine total size")
        void shouldDetermineTotalSize() {
            List<String> allItems = List.of("a", "b", "c", "d", "e");

            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> createPage(allItems, pageRequest),
                    2
            );

            assertThat(collection.size()).isEqualTo(5);
        }

        @Test
        @DisplayName("should handle empty data set")
        void shouldHandleEmptyDataSet() {
            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> new PageImpl<>(List.of(), pageRequest, 0),
                    10
            );

            assertThat(collection.size()).isZero();
            assertThat(collection.iterator().hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("iterator")
    class IteratorTests {

        @Test
        @DisplayName("should iterate through all pages")
        void shouldIterateThroughAllPages() {
            List<String> allItems = List.of("a", "b", "c", "d", "e");

            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> createPage(allItems, pageRequest),
                    2
            );

            List<String> result = new ArrayList<>();
            for (String item : collection) {
                result.add(item);
            }

            assertThat(result).containsExactly("a", "b", "c", "d", "e");
        }

        @Test
        @DisplayName("should handle single page")
        void shouldHandleSinglePage() {
            List<String> allItems = List.of("x", "y");

            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> createPage(allItems, pageRequest),
                    10
            );

            List<String> result = new ArrayList<>();
            for (String item : collection) {
                result.add(item);
            }

            assertThat(result).containsExactly("x", "y");
        }

        @Test
        @DisplayName("should throw NoSuchElementException when exhausted")
        void shouldThrowWhenExhausted() {
            List<String> allItems = List.of("only");

            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> createPage(allItems, pageRequest),
                    10
            );

            Iterator<String> it = collection.iterator();
            it.next(); // consume the only element

            assertThatThrownBy(it::next)
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("should return correct hasNext for empty collection")
        void shouldReturnFalseHasNextForEmpty() {
            StreamingCollection<String> collection = new StreamingCollection<>(
                    pageRequest -> new PageImpl<>(List.of(), pageRequest, 0),
                    10
            );

            assertThat(collection.iterator().hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("size")
    class SizeTests {

        @Test
        @DisplayName("should return correct size")
        void shouldReturnCorrectSize() {
            List<Integer> allItems = List.of(1, 2, 3, 4, 5, 6, 7);

            StreamingCollection<Integer> collection = new StreamingCollection<>(
                    pageRequest -> createPage(allItems, pageRequest),
                    3
            );

            assertThat(collection.size()).isEqualTo(7);
        }
    }

    // Helper to simulate paged data
    private <T> Page<T> createPage(List<T> allItems, PageRequest pageRequest) {
        int start = (int) pageRequest.getOffset();
        int end = Math.min(start + pageRequest.getPageSize(), allItems.size());
        List<T> content = start < allItems.size()
                ? allItems.subList(start, end)
                : List.of();
        return new PageImpl<>(content, pageRequest, allItems.size());
    }
}
