package dev.simplecore.simplix.stream.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Subscription.
 */
@DisplayName("Subscription")
class SubscriptionTest {

    @Nested
    @DisplayName("of()")
    class OfMethod {

        @Test
        @DisplayName("should create subscription with key and interval")
        void shouldCreateSubscriptionWithKeyAndInterval() {
            SubscriptionKey key = SubscriptionKey.of("stock-price", Map.of("symbol", "AAPL"));
            Duration interval = Duration.ofSeconds(5);

            Subscription subscription = Subscription.of(key, interval);

            assertThat(subscription.getKey()).isEqualTo(key);
            assertThat(subscription.getInterval()).isEqualTo(interval);
            assertThat(subscription.getRequestedAt()).isNotNull();
            assertThat(subscription.getRequestedAt()).isBeforeOrEqualTo(Instant.now());
        }

        @Test
        @DisplayName("should set requestedAt to current time")
        void shouldSetRequestedAtToCurrentTime() {
            SubscriptionKey key = SubscriptionKey.of("test", Map.of());
            Instant before = Instant.now();

            Subscription subscription = Subscription.of(key, Duration.ofSeconds(1));

            assertThat(subscription.getRequestedAt()).isAfterOrEqualTo(before);
            assertThat(subscription.getRequestedAt()).isBeforeOrEqualTo(Instant.now());
        }
    }

    @Nested
    @DisplayName("getResource()")
    class GetResourceMethod {

        @Test
        @DisplayName("should delegate to key's resource")
        void shouldDelegateToKeyResource() {
            SubscriptionKey key = SubscriptionKey.of("stock-price", Map.of("symbol", "AAPL"));
            Subscription subscription = Subscription.of(key, Duration.ofSeconds(5));

            assertThat(subscription.getResource()).isEqualTo("stock-price");
        }
    }

    @Nested
    @DisplayName("getIntervalMs()")
    class GetIntervalMsMethod {

        @Test
        @DisplayName("should return interval in milliseconds")
        void shouldReturnIntervalInMilliseconds() {
            Subscription subscription = Subscription.of(
                    SubscriptionKey.of("test", Map.of()),
                    Duration.ofSeconds(5)
            );

            assertThat(subscription.getIntervalMs()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("should handle sub-second intervals")
        void shouldHandleSubSecondIntervals() {
            Subscription subscription = Subscription.of(
                    SubscriptionKey.of("test", Map.of()),
                    Duration.ofMillis(250)
            );

            assertThat(subscription.getIntervalMs()).isEqualTo(250L);
        }

        @Test
        @DisplayName("should handle zero duration")
        void shouldHandleZeroDuration() {
            Subscription subscription = Subscription.of(
                    SubscriptionKey.of("test", Map.of()),
                    Duration.ZERO
            );

            assertThat(subscription.getIntervalMs()).isZero();
        }
    }

    @Nested
    @DisplayName("builder()")
    class BuilderMethod {

        @Test
        @DisplayName("should build subscription with all fields")
        void shouldBuildSubscriptionWithAllFields() {
            SubscriptionKey key = SubscriptionKey.of("forex", Map.of("pair", "EURUSD"));
            Duration interval = Duration.ofSeconds(10);
            Instant requestedAt = Instant.parse("2025-01-01T00:00:00Z");

            Subscription subscription = Subscription.builder()
                    .key(key)
                    .interval(interval)
                    .requestedAt(requestedAt)
                    .build();

            assertThat(subscription.getKey()).isEqualTo(key);
            assertThat(subscription.getInterval()).isEqualTo(interval);
            assertThat(subscription.getRequestedAt()).isEqualTo(requestedAt);
        }
    }
}
