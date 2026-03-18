package dev.simplecore.simplix.stream.core.scheduler;

import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisLeaderElection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    @Mock
    private RedisLeaderElection leaderElection;

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
        properties.getScheduler().setDefaultInterval(Duration.ofSeconds(1));

        schedulerManager = new SchedulerManager(
                collectorRegistry, broadcastService, properties, scheduledExecutor);
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should initialize in local mode without leader election")
        void shouldInitializeInLocalMode() {
            SchedulerManager manager = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor);
            assertFalse(manager.isDistributedMode());
        }

        @Test
        @DisplayName("should initialize in distributed mode with leader election")
        void shouldInitializeInDistributedMode() {
            SchedulerManager manager = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, leaderElection);
            assertTrue(manager.isDistributedMode());
        }

        @Test
        @DisplayName("should initialize with null leader election as local mode")
        void shouldInitializeWithNullLeaderElection() {
            SchedulerManager manager = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, null);
            assertFalse(manager.isDistributedMode());
        }
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
        @DisplayName("should not clamp interval within bounds")
        void shouldNotClampIntervalWithinBounds() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            SubscriptionScheduler scheduler = schedulerManager.getScheduler(key).get();
            assertEquals(5000, scheduler.getIntervalMs());
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

        @Test
        @DisplayName("should not create scheduler when maxTotalSchedulers is 0 (unlimited)")
        void shouldCreateSchedulerWhenMaxTotalSchedulersIsUnlimited() {
            properties.getScheduler().setMaxTotalSchedulers(0);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, schedulerManager.getSchedulerCount());
        }
    }

    @Nested
    @DisplayName("addSubscriber() with leader election")
    class AddSubscriberWithLeaderElection {

        private SchedulerManager distributedManager;

        @BeforeEach
        void setUp() {
            distributedManager = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, leaderElection);
        }

        @Test
        @DisplayName("should create scheduler when leader election succeeds")
        void shouldCreateSchedulerWhenLeaderElectionSucceeds() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            when(leaderElection.tryBecomeLeader(anyString(), any())).thenReturn(true);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributedManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, distributedManager.getSchedulerCount());
        }

        @Test
        @DisplayName("should not create scheduler when not leader")
        void shouldNotCreateSchedulerWhenNotLeader() {
            when(leaderElection.tryBecomeLeader(anyString(), any())).thenReturn(false);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributedManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(0, distributedManager.getSchedulerCount());
        }

        @Test
        @DisplayName("should track local subscribers even when not leader")
        void shouldTrackLocalSubscribersEvenWhenNotLeader() {
            when(leaderElection.tryBecomeLeader(anyString(), any())).thenReturn(false);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributedManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, distributedManager.getLocalSubscriberCount(key));
        }

        @Test
        @DisplayName("should create scheduler via leadership callback when leadership is acquired later")
        @SuppressWarnings("unchecked")
        void shouldCreateSchedulerViaLeadershipCallback() {
            ArgumentCaptor<Consumer<Boolean>> callbackCaptor =
                    ArgumentCaptor.forClass(Consumer.class);
            when(leaderElection.tryBecomeLeader(anyString(), callbackCaptor.capture()))
                    .thenReturn(false);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributedManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(0, distributedManager.getSchedulerCount());

            // Now simulate leadership acquired
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            callbackCaptor.getValue().accept(true);

            assertEquals(1, distributedManager.getSchedulerCount());
        }

        @Test
        @DisplayName("should stop scheduler via leadership callback when leadership is lost")
        @SuppressWarnings("unchecked")
        void shouldStopSchedulerViaLeadershipCallback() {
            ArgumentCaptor<Consumer<Boolean>> callbackCaptor =
                    ArgumentCaptor.forClass(Consumer.class);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            when(leaderElection.tryBecomeLeader(anyString(), callbackCaptor.capture()))
                    .thenReturn(true);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributedManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, distributedManager.getSchedulerCount());

            // Simulate leadership lost
            callbackCaptor.getValue().accept(false);

            assertEquals(0, distributedManager.getSchedulerCount());
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

        @Test
        @DisplayName("should release leadership when last subscriber removed in distributed mode")
        void shouldReleaseLeadershipWhenLastSubscriberRemovedInDistributed() {
            SchedulerManager distributed = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, leaderElection);

            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
            when(leaderElection.tryBecomeLeader(anyString(), any())).thenReturn(true);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            distributed.addSubscriber(key, "session1", Duration.ofSeconds(5));
            distributed.removeSubscriber(key, "session1");

            verify(leaderElection).releaseLeadership(key.toKeyString());
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

        @Test
        @DisplayName("should return empty for non-matching key string")
        void shouldReturnEmptyForNonMatchingKeyString() {
            Optional<SubscriptionScheduler> scheduler = schedulerManager.getScheduler("nonexistent:key");

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

        @Test
        @DisplayName("should return false for non-existing key string")
        void shouldReturnFalseForNonExistingKeyString() {
            boolean result = schedulerManager.stopScheduler("nonexistent:key");
            assertFalse(result);
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
    @DisplayName("executeScheduler()")
    class ExecuteSchedulerMethod {

        @Test
        @DisplayName("should collect data and broadcast successfully")
        void shouldCollectDataAndBroadcastSuccessfully() {
            SimpliXStreamDataCollector collector = mock(SimpliXStreamDataCollector.class);
            when(collector.collect(any())).thenReturn(Map.of("price", 150.0));
            when(collectorRegistry.getCollector("stock")).thenReturn(collector);

            ArgumentCaptor<Runnable> schedulerCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(schedulerCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            // Execute the scheduled task
            schedulerCaptor.getValue().run();

            verify(broadcastService).broadcast(eq(key), any(), any());
        }

        @Test
        @DisplayName("should record error and notify subscribers on collection failure")
        void shouldRecordErrorOnCollectionFailure() {
            when(collectorRegistry.getCollector("stock"))
                    .thenThrow(new RuntimeException("Collection failed"));

            ArgumentCaptor<Runnable> schedulerCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(schedulerCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            // Execute the scheduled task - should not throw
            assertDoesNotThrow(() -> schedulerCaptor.getValue().run());

            // Should still broadcast an error message
            verify(broadcastService).broadcast(eq(key), any(), any());
        }

        @Test
        @DisplayName("should not execute when scheduler is stopped")
        void shouldNotExecuteWhenSchedulerIsStopped() {
            ArgumentCaptor<Runnable> schedulerCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(schedulerCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            // Stop the scheduler
            schedulerManager.forceStopScheduler(key);

            // Execute - should be a no-op
            schedulerCaptor.getValue().run();

            verify(collectorRegistry, never()).getCollector(anyString());
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

        @Test
        @DisplayName("should return empty collection when no schedulers")
        void shouldReturnEmptyWhenNoSchedulers() {
            Collection<SubscriptionScheduler> schedulers = schedulerManager.getAllSchedulers();
            assertTrue(schedulers.isEmpty());
        }
    }

    @Nested
    @DisplayName("isLeader()")
    class IsLeaderMethod {

        @Test
        @DisplayName("should return true in local mode")
        void shouldReturnTrueInLocalMode() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            assertTrue(schedulerManager.isLeader(key));
        }

        @Test
        @DisplayName("should delegate to leader election in distributed mode")
        void shouldDelegateToLeaderElectionInDistributedMode() {
            SchedulerManager distributed = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, leaderElection);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            when(leaderElection.isLeader(key.toKeyString())).thenReturn(true);

            assertTrue(distributed.isLeader(key));
            verify(leaderElection).isLeader(key.toKeyString());
        }
    }

    @Nested
    @DisplayName("getLocalSubscribers()")
    class GetLocalSubscribersMethod {

        @Test
        @DisplayName("should return local subscribers")
        void shouldReturnLocalSubscribers() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));
            schedulerManager.addSubscriber(key, "session2", Duration.ofSeconds(5));

            Set<String> subscribers = schedulerManager.getLocalSubscribers(key);

            assertEquals(2, subscribers.size());
            assertTrue(subscribers.contains("session1"));
            assertTrue(subscribers.contains("session2"));
        }

        @Test
        @DisplayName("should return empty set for unknown key")
        void shouldReturnEmptySetForUnknownKey() {
            SubscriptionKey key = SubscriptionKey.of("unknown", Map.of());
            Set<String> subscribers = schedulerManager.getLocalSubscribers(key);
            assertTrue(subscribers.isEmpty());
        }
    }

    @Nested
    @DisplayName("getLocalSubscriberCount()")
    class GetLocalSubscriberCountMethod {

        @Test
        @DisplayName("should return correct count")
        void shouldReturnCorrectCount() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            assertEquals(1, schedulerManager.getLocalSubscriberCount(key));
        }

        @Test
        @DisplayName("should return zero for unknown key")
        void shouldReturnZeroForUnknownKey() {
            SubscriptionKey key = SubscriptionKey.of("unknown", Map.of());
            assertEquals(0, schedulerManager.getLocalSubscriberCount(key));
        }
    }

    @Nested
    @DisplayName("asSubscriberLookup()")
    class AsSubscriberLookupMethod {

        @Test
        @DisplayName("should return a working SubscriberLookup")
        void shouldReturnWorkingSubscriberLookup() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            SubscriberLookup lookup = schedulerManager.asSubscriberLookup();
            Set<String> subscribers = lookup.getSubscribers(key);

            assertEquals(1, subscribers.size());
            assertTrue(subscribers.contains("session1"));
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

        @Test
        @DisplayName("should release all leadership in distributed mode")
        void shouldReleaseAllLeadershipInDistributedMode() {
            SchedulerManager distributed = new SchedulerManager(
                    collectorRegistry, broadcastService, properties, scheduledExecutor, leaderElection);

            distributed.shutdown();

            verify(leaderElection).releaseAll();
        }

        @Test
        @DisplayName("should clear local subscribers and pending intervals")
        void shouldClearLocalSubscribersAndPendingIntervals() {
            doReturn(scheduledFuture).when(scheduledExecutor)
                    .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            schedulerManager.addSubscriber(key, "session1", Duration.ofSeconds(5));

            schedulerManager.shutdown();

            assertEquals(0, schedulerManager.getLocalSubscriberCount(key));
        }
    }
}
