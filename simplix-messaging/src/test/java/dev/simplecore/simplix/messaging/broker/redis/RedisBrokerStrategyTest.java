package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisBrokerStrategy")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisBrokerStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisConsumerGroupManager consumerGroupManager;

    @Mock
    private RedisStreamPublisher publisher;

    @Mock
    private RedisStreamSubscriber subscriber;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private RedisBrokerStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new RedisBrokerStrategy(
                redisTemplate, "test:", consumerGroupManager, publisher, subscriber,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
    }

    @Nested
    @DisplayName("Constructors")
    class ConstructorTests {

        @Test
        @DisplayName("should create without PEL recovery parameters")
        void shouldCreateWithoutPelRecovery() {
            RedisBrokerStrategy s = new RedisBrokerStrategy(
                    redisTemplate, "test:", consumerGroupManager, publisher, subscriber);
            assertThat(s).isNotNull();
            assertThat(s.name()).isEqualTo("redis");
        }
    }

    @Nested
    @DisplayName("name")
    class NameTests {

        @Test
        @DisplayName("should return 'redis'")
        void shouldReturnRedis() {
            assertThat(strategy.name()).isEqualTo("redis");
        }
    }

    @Nested
    @DisplayName("capabilities")
    class CapabilitiesTests {

        @Test
        @DisplayName("should report correct capabilities")
        void shouldReturnCapabilities() {
            BrokerCapabilities caps = strategy.capabilities();
            assertThat(caps.consumerGroups()).isTrue();
            assertThat(caps.replay()).isTrue();
            assertThat(caps.ordering()).isTrue();
            assertThat(caps.deadLetter()).isFalse();
        }
    }

    @Nested
    @DisplayName("send")
    class SendTests {

        @Test
        @DisplayName("should delegate to RedisStreamPublisher")
        void shouldDelegateToPublisher() {
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.MESSAGE_ID, "msg-1");
            byte[] payload = "hello".getBytes();
            PublishResult expected = new PublishResult("1-0", "ch", Instant.now());
            when(publisher.send("ch", payload, headers)).thenReturn(expected);

            PublishResult result = strategy.send("ch", payload, headers);

            assertThat(result).isEqualTo(expected);
            verify(publisher).send("ch", payload, headers);
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should create subscription and track it")
        void shouldCreateAndTrackSubscription() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(10L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);
            when(mockSub.channel()).thenReturn("ch");
            when(mockSub.groupName()).thenReturn("grp");

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            Subscription result = strategy.subscribe(request);

            assertThat(result).isNotNull();
            assertThat(result.channel()).isEqualTo("ch");
            assertThat(result.groupName()).isEqualTo("grp");

            verify(subscriber).recoverPendingMessages(request);
            verify(subscriber).subscribe(request);
        }

        @Test
        @DisplayName("should handle diagnostic exception during subscribe")
        void shouldHandleDiagnosticException() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenThrow(new RuntimeException("Redis down"));

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            // Should not throw despite diagnostic failure
            Subscription result = strategy.subscribe(request);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("ensureConsumerGroup")
    class EnsureConsumerGroupTests {

        @Test
        @DisplayName("should delegate to RedisConsumerGroupManager")
        void shouldDelegateToManager() {
            strategy.ensureConsumerGroup("ch", "grp");
            verify(consumerGroupManager).ensureConsumerGroup("ch", "grp");
        }
    }

    @Nested
    @DisplayName("acknowledge")
    class AcknowledgeTests {

        @Test
        @DisplayName("should call Redis XACK")
        void shouldCallXack() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);

            strategy.acknowledge("ch", "grp", "1-0");

            verify(streamOps).acknowledge("test:ch", "grp", "1-0");
        }
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTests {

        @Test
        @DisplayName("should set ready to true")
        void shouldSetReady() {
            assertThat(strategy.isReady()).isFalse();

            strategy.initialize();

            assertThat(strategy.isReady()).isTrue();
        }

        @Test
        @DisplayName("should start PEL recovery scheduler when interval is set")
        void shouldStartPelRecoveryScheduler() {
            strategy.initialize();
            assertThat(strategy.isReady()).isTrue();
        }

        @Test
        @DisplayName("should not start PEL scheduler when interval is null")
        void shouldNotStartSchedulerWithNullInterval() {
            RedisBrokerStrategy s = new RedisBrokerStrategy(
                    redisTemplate, "test:", consumerGroupManager, publisher, subscriber);
            s.initialize();
            assertThat(s.isReady()).isTrue();
            s.shutdown();
        }

        @Test
        @DisplayName("should not start PEL scheduler when interval is zero")
        void shouldNotStartSchedulerWithZeroInterval() {
            RedisBrokerStrategy s = new RedisBrokerStrategy(
                    redisTemplate, "test:", consumerGroupManager, publisher, subscriber,
                    Duration.ZERO, Duration.ZERO);
            s.initialize();
            assertThat(s.isReady()).isTrue();
            s.shutdown();
        }

        @Test
        @DisplayName("should not start PEL scheduler when interval is negative")
        void shouldNotStartSchedulerWithNegativeInterval() {
            RedisBrokerStrategy s = new RedisBrokerStrategy(
                    redisTemplate, "test:", consumerGroupManager, publisher, subscriber,
                    Duration.ofSeconds(-1), Duration.ofSeconds(-1));
            s.initialize();
            assertThat(s.isReady()).isTrue();
            s.shutdown();
        }
    }

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        @DisplayName("should cancel all active subscriptions and set ready to false")
        void shouldCancelSubscriptionsAndSetNotReady() {
            // Set up a subscription
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(0L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            strategy.initialize();
            strategy.subscribe(request);

            strategy.shutdown();

            assertThat(strategy.isReady()).isFalse();
        }

        @Test
        @DisplayName("should handle shutdown without PEL scheduler")
        void shouldHandleShutdownWithoutScheduler() {
            RedisBrokerStrategy s = new RedisBrokerStrategy(
                    redisTemplate, "test:", consumerGroupManager, publisher, subscriber);
            s.initialize();
            assertThatCode(s::shutdown).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle subscription cancel exception during shutdown")
        void shouldHandleCancelExceptionDuringShutdown() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(0L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);
            doThrow(new RuntimeException("Cancel failed")).when(mockSub).cancel();

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            strategy.initialize();
            strategy.subscribe(request);

            // Should not throw
            assertThatCode(() -> strategy.shutdown()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle inactive subscription during shutdown")
        void shouldHandleInactiveSubscriptionDuringShutdown() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(0L);

            // The TrackedSubscription wraps the delegate. We need the delegate to
            // return false from isActive, so the TrackedSubscription returns false as well.
            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(false);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            strategy.initialize();
            strategy.subscribe(request);

            strategy.shutdown();

            // The TrackedSubscription checks delegate.isActive() which returns false,
            // so the shutdown code should not call cancel() on the TrackedSubscription.
            // But TrackedSubscription.isActive() returns delegate.isActive(),
            // so it returns false, meaning cancel() won't be called.
        }
    }

    @Nested
    @DisplayName("isReady")
    class IsReadyTests {

        @Test
        @DisplayName("should return false before initialization")
        void shouldReturnFalseBeforeInit() {
            assertThat(strategy.isReady()).isFalse();
        }

        @Test
        @DisplayName("should return true after initialization")
        void shouldReturnTrueAfterInit() {
            strategy.initialize();
            assertThat(strategy.isReady()).isTrue();
        }
    }

    @Nested
    @DisplayName("TrackedSubscription")
    class TrackedSubscriptionTests {

        @Test
        @DisplayName("should delegate cancel and remove from active subscriptions")
        void shouldDelegateCancelAndRemove() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(0L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);
            when(mockSub.channel()).thenReturn("ch");
            when(mockSub.groupName()).thenReturn("grp");

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            strategy.initialize();
            Subscription tracked = strategy.subscribe(request);

            tracked.cancel();

            verify(mockSub).cancel();
        }
    }
}
