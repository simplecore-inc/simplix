package dev.simplecore.simplix.stream.monitoring;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
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

/**
 * Unit tests for StreamHealthIndicator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamHealthIndicator")
class StreamHealthIndicatorTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SchedulerManager schedulerManager;

    @Mock
    private BroadcastService broadcastService;

    private StreamProperties properties;
    private StreamHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        healthIndicator = new StreamHealthIndicator(
                sessionRegistry, schedulerManager, broadcastService, properties);
    }

    @Nested
    @DisplayName("health()")
    class HealthMethod {

        @Test
        @DisplayName("should return UP when all components available")
        void shouldReturnUpWhenAllComponentsAvailable() {
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionRegistry.count()).thenReturn(5L);
            when(schedulerManager.getSchedulerCount()).thenReturn(3);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("sessionRegistry", "UP");
            assertThat(health.getDetails()).containsEntry("broadcastService", "UP");
            assertThat(health.getDetails()).containsEntry("activeSessions", 5L);
            assertThat(health.getDetails()).containsEntry("activeSchedulers", 3);
            assertThat(health.getDetails()).containsEntry("mode", "LOCAL");
        }

        @Test
        @DisplayName("should return DOWN when session registry unavailable")
        void shouldReturnDownWhenSessionRegistryUnavailable() {
            when(sessionRegistry.isAvailable()).thenReturn(false);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(0);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("sessionRegistry", "DOWN");
        }

        @Test
        @DisplayName("should return DOWN when broadcast service unavailable")
        void shouldReturnDownWhenBroadcastServiceUnavailable() {
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(false);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(0);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("broadcastService", "DOWN");
        }

        @Test
        @DisplayName("should return DOWN when both components unavailable")
        void shouldReturnDownWhenBothUnavailable() {
            when(sessionRegistry.isAvailable()).thenReturn(false);
            when(broadcastService.isAvailable()).thenReturn(false);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(0);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        }

        @Test
        @DisplayName("should include scheduler utilization when max schedulers set")
        void shouldIncludeSchedulerUtilization() {
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(250);
            properties.getScheduler().setMaxTotalSchedulers(500);

            Health health = healthIndicator.health();

            assertThat(health.getDetails()).containsEntry("schedulerUtilization", "50.0%");
        }

        @Test
        @DisplayName("should include warning when scheduler utilization exceeds 90%")
        void shouldIncludeWarningWhenHighUtilization() {
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(460);
            properties.getScheduler().setMaxTotalSchedulers(500);

            Health health = healthIndicator.health();

            assertThat(health.getDetails()).containsKey("schedulerWarning");
            assertThat(health.getDetails().get("schedulerWarning"))
                    .isEqualTo("Scheduler limit nearly reached");
        }

        @Test
        @DisplayName("should not include utilization when max schedulers is 0")
        void shouldNotIncludeUtilizationWhenUnlimited() {
            when(sessionRegistry.isAvailable()).thenReturn(true);
            when(broadcastService.isAvailable()).thenReturn(true);
            when(sessionRegistry.count()).thenReturn(0L);
            when(schedulerManager.getSchedulerCount()).thenReturn(100);
            properties.getScheduler().setMaxTotalSchedulers(0);

            Health health = healthIndicator.health();

            assertThat(health.getDetails()).doesNotContainKey("schedulerUtilization");
        }
    }
}
