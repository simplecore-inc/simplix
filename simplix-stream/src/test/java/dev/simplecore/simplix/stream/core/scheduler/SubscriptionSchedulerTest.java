package dev.simplecore.simplix.stream.core.scheduler;

import dev.simplecore.simplix.stream.core.enums.SchedulerState;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionScheduler.
 */
@DisplayName("SubscriptionScheduler")
class SubscriptionSchedulerTest {

    private SubscriptionKey key;
    private SubscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
        scheduler = new SubscriptionScheduler(key, Duration.ofSeconds(5));
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should initialize with key and interval")
        void shouldInitializeWithKeyAndInterval() {
            assertEquals(key, scheduler.getKey());
            assertEquals(Duration.ofSeconds(5), scheduler.getInterval());
            assertEquals(5000, scheduler.getIntervalMs());
            assertNotNull(scheduler.getCreatedAt());
            assertEquals(SchedulerState.CREATED, scheduler.getState());
        }
    }

    @Nested
    @DisplayName("subscriber management")
    class SubscriberManagement {

        @Test
        @DisplayName("addSubscriber should add new subscriber")
        void addSubscriberShouldAddNewSubscriber() {
            boolean added = scheduler.addSubscriber("session1");

            assertTrue(added);
            assertEquals(1, scheduler.getSubscriberCount());
            assertTrue(scheduler.hasSubscribers());
        }

        @Test
        @DisplayName("addSubscriber should return false for duplicate")
        void addSubscriberShouldReturnFalseForDuplicate() {
            scheduler.addSubscriber("session1");

            boolean added = scheduler.addSubscriber("session1");

            assertFalse(added);
            assertEquals(1, scheduler.getSubscriberCount());
        }

        @Test
        @DisplayName("removeSubscriber should remove existing subscriber")
        void removeSubscriberShouldRemoveExisting() {
            scheduler.addSubscriber("session1");

            boolean removed = scheduler.removeSubscriber("session1");

            assertTrue(removed);
            assertEquals(0, scheduler.getSubscriberCount());
            assertFalse(scheduler.hasSubscribers());
        }

        @Test
        @DisplayName("removeSubscriber should return false for non-existing")
        void removeSubscriberShouldReturnFalseForNonExisting() {
            boolean removed = scheduler.removeSubscriber("session1");

            assertFalse(removed);
        }

        @Test
        @DisplayName("getSubscribers should return unmodifiable set")
        void getSubscribersShouldReturnUnmodifiableSet() {
            scheduler.addSubscriber("session1");

            assertThrows(UnsupportedOperationException.class,
                    () -> scheduler.getSubscribers().add("session2"));
        }
    }

    @Nested
    @DisplayName("execution tracking")
    class ExecutionTracking {

        @Test
        @DisplayName("recordSuccess should increment counters")
        void recordSuccessShouldIncrementCounters() {
            scheduler.recordSuccess();

            assertEquals(1, scheduler.getExecutionCount());
            assertEquals(1, scheduler.getSuccessCount());
            assertEquals(0, scheduler.getErrorCount());
            assertEquals(0, scheduler.getConsecutiveErrors());
            assertNotNull(scheduler.getLastExecutedAt());
            assertNotNull(scheduler.getLastSuccessAt());
        }

        @Test
        @DisplayName("recordSuccess should change state to RUNNING")
        void recordSuccessShouldChangeStateToRunning() {
            assertEquals(SchedulerState.CREATED, scheduler.getState());

            scheduler.recordSuccess();

            assertEquals(SchedulerState.RUNNING, scheduler.getState());
        }

        @Test
        @DisplayName("recordSuccess should reset consecutive errors")
        void recordSuccessShouldResetConsecutiveErrors() {
            scheduler.recordError("Error 1", 5);
            scheduler.recordError("Error 2", 5);
            assertEquals(2, scheduler.getConsecutiveErrors());

            scheduler.recordSuccess();

            assertEquals(0, scheduler.getConsecutiveErrors());
            assertNull(scheduler.getLastError());
        }

        @Test
        @DisplayName("recordError should increment error counters")
        void recordErrorShouldIncrementErrorCounters() {
            scheduler.recordError("Test error", 5);

            assertEquals(1, scheduler.getExecutionCount());
            assertEquals(0, scheduler.getSuccessCount());
            assertEquals(1, scheduler.getErrorCount());
            assertEquals(1, scheduler.getConsecutiveErrors());
            assertEquals("Test error", scheduler.getLastError());
        }

        @Test
        @DisplayName("recordError should change state to ERROR when max consecutive reached")
        void recordErrorShouldChangeStateToErrorWhenMaxReached() {
            scheduler.recordSuccess(); // Set state to RUNNING

            for (int i = 0; i < 5; i++) {
                scheduler.recordError("Error " + i, 5);
            }

            assertEquals(SchedulerState.ERROR, scheduler.getState());
            assertEquals(5, scheduler.getConsecutiveErrors());
        }

        @Test
        @DisplayName("recordError should not change state if not RUNNING")
        void recordErrorShouldNotChangeStateIfNotRunning() {
            assertEquals(SchedulerState.CREATED, scheduler.getState());

            for (int i = 0; i < 10; i++) {
                scheduler.recordError("Error " + i, 5);
            }

            assertEquals(SchedulerState.CREATED, scheduler.getState());
        }
    }

    @Nested
    @DisplayName("stop()")
    class StopMethod {

        @Test
        @DisplayName("should change state to STOPPED")
        void shouldChangeStateToStopped() {
            scheduler.stop();

            assertEquals(SchedulerState.STOPPED, scheduler.getState());
            assertFalse(scheduler.isActive());
        }

        @Test
        @DisplayName("should cancel scheduled future")
        void shouldCancelScheduledFuture() {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            scheduler.setScheduledFuture(future);

            scheduler.stop();

            verify(future).cancel(false);
            assertNull(scheduler.getScheduledFuture());
        }

        @Test
        @DisplayName("should handle null scheduled future")
        void shouldHandleNullScheduledFuture() {
            assertDoesNotThrow(() -> scheduler.stop());
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActiveMethod {

        @Test
        @DisplayName("should return true when not stopped")
        void shouldReturnTrueWhenNotStopped() {
            assertTrue(scheduler.isActive());
        }

        @Test
        @DisplayName("should return false when stopped")
        void shouldReturnFalseWhenStopped() {
            scheduler.stop();

            assertFalse(scheduler.isActive());
        }
    }
}
