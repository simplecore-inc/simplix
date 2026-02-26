package dev.simplecore.simplix.stream.core.scheduler;

import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for SchedulerManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerManager")
class SchedulerManagerTest {

    @Mock
    private SimpliXStreamDataCollectorRegistry collectorRegistry;

    @Mock
    private BroadcastService broadcastService;

    @Mock
    private ScheduledExecutorService scheduledExecutor;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private StreamProperties properties;
    private SchedulerManager schedulerManager;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setScheduler(new StreamProperties.SchedulerConfig());
        properties.getScheduler().setMinInterval(Duration.ofMillis(100));
        properties.getScheduler().setMaxInterval(Duration.ofMinutes(1));
        properties.getScheduler().setMaxTotalSchedulers(100);
        properties.getScheduler().setMaxConsecutiveErrors(5);
        properties.getScheduler().setThreadPoolSize(10);

        schedulerManager = new SchedulerManager(
                collectorRegistry, broadcastService, properties, scheduledExecutor);
    }

    @Nested
    @DisplayName("addSubscriber()")
    class AddSubscriberMethod {

        @Test
        @DisplayName("should create new scheduler for new subscription")
        void shouldCreateNewSchedulerForNewSubscription() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, schedulerManager.getSchedulerCount());
            assertTrue(schedulerManager.getScheduler(key).isPresent());
        }

        @Test
        @DisplayName("should share scheduler for same subscription key")
        void shouldShareSchedulerForSameSubscriptionKey() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));
            schedulerManager.addSubscriber(key, "session2", Duration.ofSeconds(5));

            assertEquals(1, schedulerManager.getSchedulerCount());
            assertEquals(2, schedulerManager.getScheduler(key).get().getSubscriberCount());
        }

        @Test
        @DisplayName("should clamp interval to minimum")
        void shouldClampIntervalToMinimum() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofMillis(10));

            SubscriptionScheduler scheduler = schedulerManager.getScheduler(key).get();
            assertEquals(100, scheduler.getIntervalMs());
        }

        @Test
        @DisplayName("should clamp interval to maximum")
        void shouldClampIntervalToMaximum() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofHours(1));

            SubscriptionScheduler scheduler = schedulerManager.getScheduler(key).get();
            assertEquals(60000, scheduler.getIntervalMs());
        }

        @Test
        @DisplayName("should not create scheduler when max limit reached")
        void shouldNotCreateSchedulerWhenMaxLimitReached() {
            properties.getScheduler().setMaxTotalSchedulers(1);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));

            schedulerManager.addSubscriber(key1, "session1", Duration.ofSeconds(5));
            schedulerManager.addSubscriber(key2, "session2", Duration.ofSeconds(5));

            assertEquals(1, schedulerManager.getSchedulerCount());
            assertTrue(schedulerManager.getScheduler(key1).isPresent());
            assertFalse(schedulerManager.getScheduler(key2).isPresent());
        }

        @Test
        @DisplayName("should schedule at fixed rate")
        void shouldScheduleAtFixedRate() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            verify(scheduledExecutor).scheduleAtFixedRate(
                    any(Runnable.class),
                    eq(0L),
                    eq(5000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }
    }

    @Nested
    @DisplayName("removeSubscriber()")
    class RemoveSubscriberMethod {

        @Test
        @DisplayName("should remove subscriber from scheduler")
        void shouldRemoveSubscriberFromScheduler() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));
            schedulerManager.addSubscriber(key, "session2", Duration.ofSeconds(5));

            schedulerManager.removeSubscriber(key, "session1");

            assertEquals(1, schedulerManager.getScheduler(key).get().getSubscriberCount());
        }

        @Test
        @DisplayName("should stop and remove scheduler when no subscribers remain")
        void shouldStopAndRemoveSchedulerWhenNoSubscribersRemain() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            schedulerManager.removeSubscriber(key, "session1");

            assertEquals(0, schedulerManager.getSchedulerCount());
            assertFalse(schedulerManager.getScheduler(key).isPresent());
            verify(scheduledFuture).cancel(false);
        }

        @Test
        @DisplayName("should do nothing for non-existing subscription")
        void shouldDoNothingForNonExistingSubscription() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            assertDoesNotThrow(() -> schedulerManager.removeSubscriber(key, "session1"));
        }
    }

    @Nested
    @DisplayName("getScheduler()")
    class GetSchedulerMethod {

        @Test
        @DisplayName("should return scheduler by key")
        void shouldReturnSchedulerByKey() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            Optional<SubscriptionScheduler> scheduler = schedulerManager.getScheduler(key);

            assertTrue(scheduler.isPresent());
            assertEquals(key, scheduler.get().getKey());
        }

        @Test
        @DisplayName("should return scheduler by key string")
        void shouldReturnSchedulerByKeyString() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            Optional<SubscriptionScheduler> scheduler = schedulerManager.getScheduler(key.toKeyString());

            assertTrue(scheduler.isPresent());
        }

        @Test
        @DisplayName("should return empty for non-existing key")
        void shouldReturnEmptyForNonExistingKey() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            Optional<SubscriptionScheduler> scheduler = schedulerManager.getScheduler(key);

            assertTrue(scheduler.isEmpty());
        }
    }

    @Nested
    @DisplayName("forceStopScheduler()")
    class ForceStopSchedulerMethod {

        @Test
        @DisplayName("should stop and remove scheduler")
        void shouldStopAndRemoveScheduler() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            boolean result = schedulerManager.forceStopScheduler(key);

            assertTrue(result);
            assertEquals(0, schedulerManager.getSchedulerCount());
            verify(scheduledFuture).cancel(false);
        }

        @Test
        @DisplayName("should return false for non-existing scheduler")
        void shouldReturnFalseForNonExistingScheduler() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            boolean result = schedulerManager.forceStopScheduler(key);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("stopScheduler(String)")
    class StopSchedulerByStringMethod {

        @Test
        @DisplayName("should stop scheduler by key string")
        void shouldStopSchedulerByKeyString() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            boolean result = schedulerManager.stopScheduler(key.toKeyString());

            assertTrue(result);
            assertEquals(0, schedulerManager.getSchedulerCount());
        }
    }

    @Nested
    @DisplayName("triggerNow()")
    class TriggerNowMethod {

        @Test
        @DisplayName("should trigger immediate execution")
        void shouldTriggerImmediateExecution() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            schedulerManager.triggerNow(key.toKeyString());

            verify(scheduledExecutor).execute(any(Runnable.class));
        }

        @Test
        @DisplayName("should do nothing for non-existing scheduler")
        void shouldDoNothingForNonExistingScheduler() {
            schedulerManager.triggerNow("nonexistent");

            verify(scheduledExecutor, never()).execute(any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("getAllSchedulers()")
    class GetAllSchedulersMethod {

        @Test
        @DisplayName("should return all schedulers")
        void shouldReturnAllSchedulers() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("forex", Map.of("pair", "EUR/USD"));

            schedulerManager.addSubscriber(key1, "session1", Duration.ofSeconds(5));
            schedulerManager.addSubscriber(key2, "session2", Duration.ofSeconds(5));

            assertEquals(2, schedulerManager.getAllSchedulers().size());
        }
    }

    @Nested
    @DisplayName("shutdown()")
    class ShutdownMethod {

        @Test
        @DisplayName("should stop all schedulers")
        void shouldStopAllSchedulers() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            schedulerManager.shutdown();

            assertEquals(0, schedulerManager.getSchedulerCount());
            verify(scheduledFuture).cancel(false);
        }
    }
}
