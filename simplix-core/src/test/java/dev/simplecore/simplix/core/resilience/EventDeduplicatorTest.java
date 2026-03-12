package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeduplicatorTest {

    private EventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new EventDeduplicator(2);
    }

    @Test
    @DisplayName("should not flag first occurrence as duplicate")
    void shouldNotFlagFirst() {
        assertThat(deduplicator.isDuplicate("id-1", 1000L, "key-a")).isFalse();
    }

    @Test
    @DisplayName("should flag second occurrence as duplicate")
    void shouldFlagDuplicate() {
        deduplicator.isDuplicate("id-1", 1000L, "key-a");
        assertThat(deduplicator.isDuplicate("id-1", 1000L, "key-a")).isTrue();
    }

    @Test
    @DisplayName("should not flag different keys as duplicate")
    void shouldNotFlagDifferentKeys() {
        deduplicator.isDuplicate("id-1", 1000L, "key-a");
        assertThat(deduplicator.isDuplicate("id-1", 1000L, "key-b")).isFalse();
        assertThat(deduplicator.isDuplicate("id-2", 1000L, "key-a")).isFalse();
        assertThat(deduplicator.isDuplicate("id-1", 2000L, "key-a")).isFalse();
    }

    @Test
    @DisplayName("should expire entries after window")
    void shouldExpireAfterWindow() throws InterruptedException {
        deduplicator.isDuplicate("id-1", 1000L, "key-a");
        Thread.sleep(2500); // window is 2 seconds
        assertThat(deduplicator.isDuplicate("id-1", 1000L, "key-a")).isFalse();
    }

    @Test
    @DisplayName("should track count of entries")
    void shouldTrackCount() {
        deduplicator.isDuplicate("id-1", 1000L, "key-a");
        deduplicator.isDuplicate("id-2", 1000L, "key-a");
        assertThat(deduplicator.getTrackedCount()).isEqualTo(2);
    }
}
