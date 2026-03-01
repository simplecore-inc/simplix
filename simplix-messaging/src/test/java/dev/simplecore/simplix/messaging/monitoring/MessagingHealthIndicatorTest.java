package dev.simplecore.simplix.messaging.monitoring;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("MessagingHealthIndicator")
@ExtendWith(MockitoExtension.class)
class MessagingHealthIndicatorTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    private MessagingHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new MessagingHealthIndicator(brokerStrategy);
    }

    @Nested
    @DisplayName("health()")
    class HealthTests {

        @Test
        @DisplayName("should report UP when broker is ready")
        void shouldReportUpWhenBrokerReady() {
            when(brokerStrategy.isReady()).thenReturn(true);
            when(brokerStrategy.name()).thenReturn("redis");
            when(brokerStrategy.capabilities()).thenReturn(
                    new BrokerCapabilities(true, true, true, false));

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("broker", "redis");
            assertThat(health.getDetails()).containsEntry("consumerGroups", true);
            assertThat(health.getDetails()).containsEntry("replay", true);
            assertThat(health.getDetails()).containsEntry("ordering", true);
            assertThat(health.getDetails()).containsEntry("deadLetter", false);
        }

        @Test
        @DisplayName("should report DOWN when broker is not ready")
        void shouldReportDownWhenBrokerNotReady() {
            when(brokerStrategy.isReady()).thenReturn(false);
            when(brokerStrategy.name()).thenReturn("local");
            when(brokerStrategy.capabilities()).thenReturn(
                    new BrokerCapabilities(true, false, true, false));

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("broker", "local");
        }

        @Test
        @DisplayName("should report DOWN with exception details when broker check fails")
        void shouldReportDownOnException() {
            when(brokerStrategy.isReady()).thenThrow(new RuntimeException("Connection refused"));
            when(brokerStrategy.name()).thenReturn("redis");

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("broker", "redis");
            assertThat(health.getDetails()).containsKey("error");
        }

        @Test
        @DisplayName("should include all capability details")
        void shouldIncludeAllCapabilities() {
            when(brokerStrategy.isReady()).thenReturn(true);
            when(brokerStrategy.name()).thenReturn("kafka");
            when(brokerStrategy.capabilities()).thenReturn(
                    new BrokerCapabilities(true, true, true, true));

            Health health = healthIndicator.health();

            assertThat(health.getDetails())
                    .containsEntry("consumerGroups", true)
                    .containsEntry("replay", true)
                    .containsEntry("ordering", true)
                    .containsEntry("deadLetter", true);
        }
    }
}
