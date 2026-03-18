package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisBrokerStrategy recovery and diagnostic")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisBrokerStrategyRecoveryTest {

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
        // Use short interval for fast testing of PEL recovery
        strategy = new RedisBrokerStrategy(
                redisTemplate, "test:", consumerGroupManager, publisher, subscriber,
                Duration.ofMillis(100), Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        strategy.shutdown();
    }

    @Nested
    @DisplayName("recoverAllPendingMessages (via scheduler)")
    class RecoverAllPendingTests {

        @Test
        @DisplayName("should recover pending messages for active subscriptions")
        void shouldRecoverPendingForActiveSubscriptions() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size(anyString())).thenReturn(10L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);

            // Set up PEL recovery mocking
            PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
            when(summary.getTotalPendingMessages()).thenReturn(5L);
            when(streamOps.pending("test:ch", "grp")).thenReturn(summary);

            StreamInfo.XInfoConsumers consumers = mock(StreamInfo.XInfoConsumers.class);
            when(consumers.size()).thenReturn(1);
            when(streamOps.consumers("test:ch", "grp")).thenReturn(consumers);

            strategy.initialize();
            strategy.subscribe(request);

            // Wait for at least one recovery cycle
            Thread.sleep(300);

            // Verify recovery was attempted
            verify(subscriber, atLeastOnce()).recoverPendingMessages(request);
        }

        @Test
        @DisplayName("should handle ensureConsumerGroup failure during recovery")
        void shouldHandleEnsureGroupFailure() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size(anyString())).thenReturn(0L);

            Subscription mockSub = mock(Subscription.class);
            when(mockSub.isActive()).thenReturn(true);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request)).thenReturn(mockSub);
            doThrow(new RuntimeException("Group error"))
                    .when(consumerGroupManager).ensureConsumerGroup("ch", "grp");

            strategy.initialize();
            strategy.subscribe(request);

            Thread.sleep(300);

            // Should not crash the scheduler
        }

        @Test
        @DisplayName("should resubscribe dead subscriptions")
        void shouldResubscribeDeadSubscriptions() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size(anyString())).thenReturn(0L);

            // First subscription becomes dead
            Subscription deadSub = mock(Subscription.class);
            when(deadSub.isActive()).thenReturn(false);

            // New subscription is active
            Subscription newSub = mock(Subscription.class);
            when(newSub.isActive()).thenReturn(true);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            // First subscribe returns dead sub, second returns new sub
            when(subscriber.subscribe(request)).thenReturn(deadSub).thenReturn(newSub);

            strategy.initialize();
            strategy.subscribe(request);

            // Wait for recovery to detect dead subscription
            Thread.sleep(300);

            // The recovery should attempt resubscription
            verify(subscriber, atLeast(2)).subscribe(request);
        }

        @Test
        @DisplayName("should handle resubscribe failure gracefully")
        void shouldHandleResubscribeFailure() throws Exception {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size(anyString())).thenReturn(0L);

            Subscription deadSub = mock(Subscription.class);
            when(deadSub.isActive()).thenReturn(false);

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel("ch")
                    .groupName("grp")
                    .consumerName("c1")
                    .listener((msg, ack) -> {})
                    .build();

            when(subscriber.subscribe(request))
                    .thenReturn(deadSub)
                    .thenThrow(new RuntimeException("Subscribe failed"));

            strategy.initialize();
            strategy.subscribe(request);

            Thread.sleep(300);

            // Should not crash the scheduler
        }
    }

    @Nested
    @DisplayName("logConsumerDiagnostic")
    class ConsumerDiagnosticTests {

        @Test
        @DisplayName("should handle consumer diagnostic exception gracefully")
        void shouldHandleDiagnosticException() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(10L);
            when(streamOps.consumers(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Redis error"));

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

            // This triggers logConsumerDiagnostic which should handle the exception
            assertThatCode(() -> strategy.subscribe(request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle null consumer info")
        void shouldHandleNullConsumerInfo() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(10L);
            when(streamOps.consumers(anyString(), anyString())).thenReturn(null);

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
            assertThatCode(() -> strategy.subscribe(request)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should warn about multiple consumers in group")
        void shouldWarnAboutMultipleConsumers() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(10L);

            StreamInfo.XInfoConsumers consumers = mock(StreamInfo.XInfoConsumers.class);
            when(consumers.size()).thenReturn(3);

            StreamInfo.XInfoConsumer c1 = mock(StreamInfo.XInfoConsumer.class);
            when(c1.consumerName()).thenReturn("c1");
            when(c1.pendingCount()).thenReturn(5L);
            when(c1.idleTime()).thenReturn(Duration.ofSeconds(10));

            StreamInfo.XInfoConsumer c2 = mock(StreamInfo.XInfoConsumer.class);
            when(c2.consumerName()).thenReturn("c2");
            when(c2.pendingCount()).thenReturn(0L);
            when(c2.idleTime()).thenReturn(Duration.ofSeconds(1));

            when(consumers.iterator()).thenReturn(java.util.List.of(c1, c2).iterator());

            when(streamOps.consumers("test:ch", "grp")).thenReturn(consumers);

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

            // Verify consumers() was called for diagnostic
            verify(streamOps).consumers("test:ch", "grp");
        }

        @Test
        @DisplayName("should log single consumer info")
        void shouldLogSingleConsumer() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.size("test:ch")).thenReturn(10L);

            StreamInfo.XInfoConsumers consumers = mock(StreamInfo.XInfoConsumers.class);
            when(consumers.size()).thenReturn(1);

            when(streamOps.consumers("test:ch", "grp")).thenReturn(consumers);

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

            verify(streamOps).consumers("test:ch", "grp");
        }
    }

    @Nested
    @DisplayName("shutdown with PEL scheduler")
    class ShutdownWithSchedulerTests {

        @Test
        @DisplayName("should gracefully shutdown PEL scheduler")
        void shouldShutdownScheduler() {
            strategy.initialize();

            // Wait a bit for the scheduler to run
            try { Thread.sleep(150); } catch (InterruptedException ignored) {}

            assertThatCode(() -> strategy.shutdown()).doesNotThrowAnyException();
            assertThat(strategy.isReady()).isFalse();
        }
    }
}
