package dev.simplecore.simplix.messaging.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessagingMetrics")
class MessagingMetricsTest {

    private MeterRegistry registry;
    private MessagingMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MessagingMetrics(registry);
    }

    @Nested
    @DisplayName("recordPublish()")
    class RecordPublishTests {

        @Test
        @DisplayName("should register and increment published counter")
        void shouldIncrementPublishedCounter() {
            metrics.recordPublish("orders");
            metrics.recordPublish("orders");
            metrics.recordPublish("orders");

            Counter counter = registry.find("simplix.messaging.published")
                    .tag("channel", "orders")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("should track different channels independently")
        void shouldTrackDifferentChannels() {
            metrics.recordPublish("orders");
            metrics.recordPublish("orders");
            metrics.recordPublish("events");

            Counter ordersCounter = registry.find("simplix.messaging.published")
                    .tag("channel", "orders")
                    .counter();
            Counter eventsCounter = registry.find("simplix.messaging.published")
                    .tag("channel", "events")
                    .counter();

            assertThat(ordersCounter).isNotNull();
            assertThat(ordersCounter.count()).isEqualTo(2.0);
            assertThat(eventsCounter).isNotNull();
            assertThat(eventsCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("recordConsume()")
    class RecordConsumeTests {

        @Test
        @DisplayName("should register and increment consumed counter with channel and group tags")
        void shouldIncrementConsumedCounter() {
            metrics.recordConsume("orders", "order-service");
            metrics.recordConsume("orders", "order-service");

            Counter counter = registry.find("simplix.messaging.consumed")
                    .tag("channel", "orders")
                    .tag("group", "order-service")
                    .counter();

            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("should track different groups independently")
        void shouldTrackDifferentGroups() {
            metrics.recordConsume("orders", "group-a");
            metrics.recordConsume("orders", "group-b");
            metrics.recordConsume("orders", "group-b");

            Counter groupACounter = registry.find("simplix.messaging.consumed")
                    .tag("channel", "orders")
                    .tag("group", "group-a")
                    .counter();
            Counter groupBCounter = registry.find("simplix.messaging.consumed")
                    .tag("channel", "orders")
                    .tag("group", "group-b")
                    .counter();

            assertThat(groupACounter).isNotNull();
            assertThat(groupACounter.count()).isEqualTo(1.0);
            assertThat(groupBCounter).isNotNull();
            assertThat(groupBCounter.count()).isEqualTo(2.0);
        }
    }

    @Nested
    @DisplayName("recordFailure()")
    class RecordFailureTests {

        @Test
        @DisplayName("should register and increment failure counter with error type tag")
        void shouldIncrementFailureCounter() {
            metrics.recordFailure("orders", "IOException");
            metrics.recordFailure("orders", "IOException");
            metrics.recordFailure("orders", "TimeoutException");

            Counter ioCounter = registry.find("simplix.messaging.failed")
                    .tag("channel", "orders")
                    .tag("errorType", "IOException")
                    .counter();
            Counter timeoutCounter = registry.find("simplix.messaging.failed")
                    .tag("channel", "orders")
                    .tag("errorType", "TimeoutException")
                    .counter();

            assertThat(ioCounter).isNotNull();
            assertThat(ioCounter.count()).isEqualTo(2.0);
            assertThat(timeoutCounter).isNotNull();
            assertThat(timeoutCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("publish timer")
    class PublishTimerTests {

        @Test
        @DisplayName("should record publish duration")
        void shouldRecordPublishDuration() {
            Timer.Sample sample = metrics.startPublishTimer("orders");
            assertThat(sample).isNotNull();

            metrics.stopPublishTimer(sample, "orders");

            Timer timer = registry.find("simplix.messaging.publish.time")
                    .tag("channel", "orders")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle multiple timer recordings")
        void shouldHandleMultipleTimers() {
            Timer.Sample sample1 = metrics.startPublishTimer("orders");
            metrics.stopPublishTimer(sample1, "orders");

            Timer.Sample sample2 = metrics.startPublishTimer("orders");
            metrics.stopPublishTimer(sample2, "orders");

            Timer timer = registry.find("simplix.messaging.publish.time")
                    .tag("channel", "orders")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("consume timer")
    class ConsumeTimerTests {

        @Test
        @DisplayName("should record consume duration with channel and group tags")
        void shouldRecordConsumeDuration() {
            Timer.Sample sample = metrics.startConsumeTimer("orders", "order-service");
            assertThat(sample).isNotNull();

            metrics.stopConsumeTimer(sample, "orders", "order-service");

            Timer timer = registry.find("simplix.messaging.consume.time")
                    .tag("channel", "orders")
                    .tag("group", "order-service")
                    .timer();

            assertThat(timer).isNotNull();
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("pending gauge")
    class PendingGaugeTests {

        @Test
        @DisplayName("should register pending count gauge with supplier")
        void shouldRegisterPendingGauge() {
            java.util.concurrent.atomic.AtomicInteger pendingCount = new java.util.concurrent.atomic.AtomicInteger(5);
            metrics.registerPendingGauge("orders", "order-service", pendingCount::get);

            Double gaugeValue = registry.find("simplix.messaging.pending.count")
                    .tag("channel", "orders")
                    .tag("group", "order-service")
                    .gauge()
                    .value();

            assertThat(gaugeValue).isEqualTo(5.0);

            pendingCount.set(10);
            gaugeValue = registry.find("simplix.messaging.pending.count")
                    .tag("channel", "orders")
                    .tag("group", "order-service")
                    .gauge()
                    .value();

            assertThat(gaugeValue).isEqualTo(10.0);
        }
    }

    @Nested
    @DisplayName("no-op mode (null registry)")
    class NoOpModeTests {

        @Test
        @DisplayName("should not throw when registry is null")
        void shouldNotThrowWithNullRegistry() {
            MessagingMetrics noopMetrics = new MessagingMetrics(null);

            // All operations should be no-ops without throwing
            noopMetrics.recordPublish("orders");
            noopMetrics.recordConsume("orders", "group");
            noopMetrics.recordFailure("orders", "Error");

            Timer.Sample sample = noopMetrics.startPublishTimer("orders");
            assertThat(sample).isNull();
            noopMetrics.stopPublishTimer(null, "orders");

            Timer.Sample consumeSample = noopMetrics.startConsumeTimer("orders", "group");
            assertThat(consumeSample).isNull();
            noopMetrics.stopConsumeTimer(null, "orders", "group");

            noopMetrics.registerPendingGauge("orders", "group", () -> 0);
        }
    }
}
