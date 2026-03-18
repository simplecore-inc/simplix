package dev.simplecore.simplix.stream.monitoring;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StreamMetrics.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamMetrics")
class StreamMetricsTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SchedulerManager schedulerManager;

    private StreamProperties properties;
    private StreamMetrics metrics;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        metrics = new StreamMetrics(sessionRegistry, schedulerManager, properties);
    }

    @Nested
    @DisplayName("counter methods")
    class CounterMethods {

        @Test
        @DisplayName("recordMessageDelivered should increment counter")
        void recordMessageDeliveredShouldIncrementCounter() {
            metrics.recordMessageDelivered();
            metrics.recordMessageDelivered();
            metrics.recordMessageDelivered();

            assertThat(metrics.getMessagesDelivered()).isEqualTo(3);
        }

        @Test
        @DisplayName("recordMessageFailed should increment counter")
        void recordMessageFailedShouldIncrementCounter() {
            metrics.recordMessageFailed();
            metrics.recordMessageFailed();

            assertThat(metrics.getMessagesFailed()).isEqualTo(2);
        }

        @Test
        @DisplayName("initial counter values should be zero")
        void initialCounterValuesShouldBeZero() {
            assertThat(metrics.getMessagesDelivered()).isZero();
            assertThat(metrics.getMessagesFailed()).isZero();
        }
    }

    @Nested
    @DisplayName("recording methods")
    class RecordingMethods {

        @Test
        @DisplayName("recordConnectionEstablished should not throw")
        void recordConnectionEstablishedShouldNotThrow() {
            metrics.recordConnectionEstablished();
            // Verify no exception thrown - counter is internal
        }

        @Test
        @DisplayName("recordConnectionClosed should not throw")
        void recordConnectionClosedShouldNotThrow() {
            metrics.recordConnectionClosed();
        }

        @Test
        @DisplayName("recordSubscriptionAdded should not throw")
        void recordSubscriptionAddedShouldNotThrow() {
            metrics.recordSubscriptionAdded();
        }

        @Test
        @DisplayName("recordSubscriptionRemoved should not throw")
        void recordSubscriptionRemovedShouldNotThrow() {
            metrics.recordSubscriptionRemoved();
        }
    }

    @Nested
    @DisplayName("bindTo()")
    class BindTo {

        @Test
        @DisplayName("should register all gauges with meter registry")
        void shouldRegisterAllGaugesWithMeterRegistry() {
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.find("simplix.stream.sessions.active").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.schedulers.active").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.messages.delivered").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.messages.failed").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.connections.established").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.connections.closed").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.subscriptions.added").gauge()).isNotNull();
            assertThat(registry.find("simplix.stream.subscriptions.removed").gauge()).isNotNull();
        }

        @Test
        @DisplayName("should use custom prefix from properties")
        void shouldUseCustomPrefix() {
            properties.getMonitoring().setMetricsPrefix("custom.prefix");
            StreamMetrics customMetrics = new StreamMetrics(sessionRegistry, schedulerManager, properties);
            MeterRegistry registry = new SimpleMeterRegistry();

            customMetrics.bindTo(registry);

            assertThat(registry.find("custom.prefix.sessions.active").gauge()).isNotNull();
        }

        @Test
        @DisplayName("should reflect counter changes after binding")
        void shouldReflectCounterChangesAfterBinding() {
            MeterRegistry registry = new SimpleMeterRegistry();
            metrics.bindTo(registry);

            metrics.recordMessageDelivered();
            metrics.recordMessageDelivered();
            metrics.recordMessageFailed();

            assertThat(registry.find("simplix.stream.messages.delivered").gauge().value())
                    .isEqualTo(2.0);
            assertThat(registry.find("simplix.stream.messages.failed").gauge().value())
                    .isEqualTo(1.0);
        }
    }
}
