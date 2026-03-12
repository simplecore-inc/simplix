package dev.simplecore.simplix.core.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncQueryTrackerTest {

    private AsyncQueryTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AsyncQueryTracker(500);
    }

    @Test
    @DisplayName("should track and complete query")
    void shouldTrackAndComplete() throws Exception {
        CompletableFuture<Void> future = tracker.track("q-1");
        assertThat(tracker.getPendingCount()).isEqualTo(1);

        tracker.complete("q-1");
        assertThat(future.isDone()).isTrue();
        assertThat(tracker.getPendingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should timeout pending query")
    void shouldTimeoutPendingQuery() {
        CompletableFuture<Void> future = tracker.track("q-1");
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @DisplayName("should be no-op for null or empty query ID")
    void shouldNoOpForNullOrEmpty() {
        tracker.complete(null);
        tracker.complete("");
        assertThat(tracker.getPendingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should be no-op for unknown query ID")
    void shouldNoOpForUnknownQuery() {
        tracker.complete("unknown");
        assertThat(tracker.getPendingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("should remove completed query from pending")
    void shouldRemoveCompletedQuery() {
        tracker.track("q-1");
        tracker.track("q-2");
        assertThat(tracker.getPendingCount()).isEqualTo(2);

        tracker.complete("q-1");
        assertThat(tracker.getPendingCount()).isEqualTo(1);
    }
}
