package dev.simplecore.simplix.cache.config;

import dev.simplecore.simplix.cache.strategy.CacheStrategy;
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

@DisplayName("CacheHealthIndicator")
@ExtendWith(MockitoExtension.class)
class CacheHealthIndicatorTest {

    @Mock
    private CacheStrategy cacheStrategy;

    @Nested
    @DisplayName("health")
    class HealthTests {

        @Test
        @DisplayName("should return UP when cache strategy is available")
        void shouldReturnUpWhenAvailable() {
            when(cacheStrategy.isAvailable()).thenReturn(true);
            when(cacheStrategy.getName()).thenReturn("local");

            CacheHealthIndicator indicator = new CacheHealthIndicator(cacheStrategy);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("strategy", "local");
            assertThat(health.getDetails()).containsEntry("available", true);
        }

        @Test
        @DisplayName("should return DOWN when cache strategy is not available")
        void shouldReturnDownWhenNotAvailable() {
            when(cacheStrategy.isAvailable()).thenReturn(false);
            when(cacheStrategy.getName()).thenReturn("redis");

            CacheHealthIndicator indicator = new CacheHealthIndicator(cacheStrategy);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("strategy", "redis");
            assertThat(health.getDetails()).containsEntry("available", false);
            assertThat(health.getDetails()).containsEntry("reason", "Cache strategy not available");
        }

        @Test
        @DisplayName("should return DOWN when cache strategy throws exception")
        void shouldReturnDownWhenExceptionThrown() {
            RuntimeException exception = new RuntimeException("Connection refused");
            when(cacheStrategy.isAvailable()).thenThrow(exception);
            when(cacheStrategy.getName()).thenReturn("redis");

            CacheHealthIndicator indicator = new CacheHealthIndicator(cacheStrategy);
            Health health = indicator.health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("strategy", "redis");
            assertThat(health.getDetails()).containsKey("error");
        }
    }
}
