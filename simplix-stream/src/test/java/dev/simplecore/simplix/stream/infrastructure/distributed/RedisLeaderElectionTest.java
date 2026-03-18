package dev.simplecore.simplix.stream.infrastructure.distributed;

import dev.simplecore.simplix.stream.config.StreamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisLeaderElection.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisLeaderElection")
class RedisLeaderElectionTest {

    private static final String INSTANCE_ID = "test-instance";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private StreamProperties properties;
    private RedisLeaderElection leaderElection;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setDistributed(new StreamProperties.DistributedConfig());
        properties.getDistributed().setLeaderElection(new StreamProperties.LeaderElectionConfig());
        properties.getDistributed().getLeaderElection().setTtl(Duration.ofSeconds(30));
        properties.getDistributed().getLeaderElection().setRenewInterval(Duration.ofSeconds(10));
        properties.getDistributed().setRegistry(new StreamProperties.RegistryConfig());
        properties.getDistributed().getRegistry().setKeyPrefix("stream:");

        leaderElection = new RedisLeaderElection(
                redisTemplate, scheduler, INSTANCE_ID, properties);
    }

    @Nested
    @DisplayName("tryBecomeLeader()")
    class TryBecomeLeader {

        @Test
        @DisplayName("should acquire leadership when SETNX succeeds")
        void shouldAcquireLeadershipWhenSetNxSucceeds() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

            AtomicBoolean callbackResult = new AtomicBoolean(false);
            boolean result = leaderElection.tryBecomeLeader("sub-key", callbackResult::set);

            assertThat(result).isTrue();
            assertThat(callbackResult.get()).isTrue();
            verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(10000L), eq(10000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("should return true when already leader")
        void shouldReturnTrueWhenAlreadyLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.FALSE);
            when(valueOperations.get(anyString())).thenReturn(INSTANCE_ID);

            boolean result = leaderElection.tryBecomeLeader("sub-key", null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when another instance is leader")
        void shouldReturnFalseWhenAnotherIsLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.FALSE);
            when(valueOperations.get(anyString())).thenReturn("other-instance");

            boolean result = leaderElection.tryBecomeLeader("sub-key", null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false on Redis exception")
        void shouldReturnFalseOnRedisException() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            boolean result = leaderElection.tryBecomeLeader("sub-key", null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should invoke callback with true on leadership acquisition")
        void shouldInvokeCallbackWithTrue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

            Consumer<Boolean> callback = mock(Consumer.class);
            leaderElection.tryBecomeLeader("sub-key", callback);

            verify(callback).accept(true);
        }

        @Test
        @DisplayName("should return false when null callback causes ConcurrentHashMap rejection")
        void shouldReturnFalseWhenNullCallbackCausesError() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);

            // ConcurrentHashMap does not allow null values, so putting a null callback
            // causes NPE which is caught by the error handler, returning false
            boolean result = leaderElection.tryBecomeLeader("sub-key", null);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isLeader()")
    class IsLeader {

        @Test
        @DisplayName("should return true when this instance is leader")
        void shouldReturnTrueWhenLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("stream:stream:leader:sub-key")).thenReturn(INSTANCE_ID);

            boolean result = leaderElection.isLeader("sub-key");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when another instance is leader")
        void shouldReturnFalseWhenNotLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("stream:stream:leader:sub-key")).thenReturn("other-instance");

            boolean result = leaderElection.isLeader("sub-key");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false on Redis exception")
        void shouldReturnFalseOnException() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            boolean result = leaderElection.isLeader("sub-key");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseLeadership()")
    class ReleaseLeadership {

        @Test
        @DisplayName("should release leadership and delete Redis key")
        void shouldReleaseAndDeleteKey() {
            // First acquire leadership
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

            AtomicBoolean callbackStatus = new AtomicBoolean(false);
            Consumer<Boolean> callback = callbackStatus::set;

            leaderElection.tryBecomeLeader("sub-key", callback);

            // Now release
            when(valueOperations.get(anyString())).thenReturn(INSTANCE_ID);

            leaderElection.releaseLeadership("sub-key");

            verify(scheduledFuture).cancel(false);
            verify(redisTemplate).delete("stream:stream:leader:sub-key");
            assertThat(callbackStatus.get()).isFalse();
        }

        @Test
        @DisplayName("should not delete key if not the current leader")
        void shouldNotDeleteIfNotLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn("other-instance");

            leaderElection.releaseLeadership("sub-key");

            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("should handle Redis exception during release")
        void shouldHandleExceptionDuringRelease() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            // Should not throw
            leaderElection.releaseLeadership("sub-key");
        }
    }

    @Nested
    @DisplayName("releaseAll()")
    class ReleaseAll {

        @Test
        @DisplayName("should release all held leaderships")
        void shouldReleaseAllLeaderships() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

            Consumer<Boolean> callback1 = b -> {};
            Consumer<Boolean> callback2 = b -> {};
            leaderElection.tryBecomeLeader("sub-1", callback1);
            leaderElection.tryBecomeLeader("sub-2", callback2);

            when(valueOperations.get(anyString())).thenReturn(INSTANCE_ID);

            leaderElection.releaseAll();

            verify(scheduledFuture, times(2)).cancel(false);
            assertThat(leaderElection.getLeadershipCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getLeader()")
    class GetLeader {

        @Test
        @DisplayName("should return current leader instance ID")
        void shouldReturnCurrentLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("stream:stream:leader:sub-key")).thenReturn("leader-1");

            String leader = leaderElection.getLeader("sub-key");

            assertThat(leader).isEqualTo("leader-1");
        }

        @Test
        @DisplayName("should return null when no leader")
        void shouldReturnNullWhenNoLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("stream:stream:leader:sub-key")).thenReturn(null);

            String leader = leaderElection.getLeader("sub-key");

            assertThat(leader).isNull();
        }

        @Test
        @DisplayName("should return null on Redis exception")
        void shouldReturnNullOnException() {
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            String leader = leaderElection.getLeader("sub-key");

            assertThat(leader).isNull();
        }
    }

    @Nested
    @DisplayName("renewLeadership (via scheduled task)")
    class RenewLeadership {

        @Test
        @DisplayName("should renew leadership when still leader")
        void shouldRenewWhenStillLeader() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);

            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());

            Consumer<Boolean> callback = b -> {};
            leaderElection.tryBecomeLeader("sub-key", callback);

            // Now simulate the renewal task being invoked
            when(valueOperations.get(anyString())).thenReturn(INSTANCE_ID);

            taskCaptor.getValue().run();

            // Should have called expire to renew TTL
            verify(redisTemplate).expire(eq("stream:stream:leader:sub-key"), any(Duration.class));
        }

        @Test
        @DisplayName("should detect lost leadership during renewal")
        void shouldDetectLostLeadershipDuringRenewal() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);

            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());

            AtomicBoolean leaderStatus = new AtomicBoolean(false);
            leaderElection.tryBecomeLeader("sub-key", leaderStatus::set);

            // Another instance is now leader
            when(valueOperations.get(anyString())).thenReturn("other-instance");

            taskCaptor.getValue().run();

            // Should cancel the renewal task and invoke callback with false
            verify(scheduledFuture).cancel(false);
            assertThat(leaderStatus.get()).isFalse();
        }

        @Test
        @DisplayName("should handle Redis error during renewal")
        void shouldHandleRedisErrorDuringRenewal() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);

            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(taskCaptor.capture(), anyLong(), anyLong(), any());

            Consumer<Boolean> callback = b -> {};
            leaderElection.tryBecomeLeader("sub-key", callback);

            // Redis throws exception during renewal
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis down"));

            // Should not throw
            taskCaptor.getValue().run();
        }
    }

    @Nested
    @DisplayName("getLeadershipCount()")
    class GetLeadershipCount {

        @Test
        @DisplayName("should return zero initially")
        void shouldReturnZeroInitially() {
            assertThat(leaderElection.getLeadershipCount()).isZero();
        }

        @Test
        @DisplayName("should return correct count after acquiring leadership")
        void shouldReturnCorrectCount() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(INSTANCE_ID), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            doReturn(scheduledFuture).when(scheduler)
                    .scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

            Consumer<Boolean> callback = b -> {};
            leaderElection.tryBecomeLeader("sub-1", callback);

            assertThat(leaderElection.getLeadershipCount()).isEqualTo(1);
        }
    }
}
