package dev.simplecore.simplix.stream.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamSubscriptionEntity.
 */
@DisplayName("StreamSubscriptionEntity")
class StreamSubscriptionEntityTest {

    @Nested
    @DisplayName("markUnsubscribed()")
    class MarkUnsubscribed {

        @Test
        @DisplayName("should set active to false and set unsubscribedAt")
        void shouldMarkUnsubscribed() {
            StreamSubscriptionEntity entity = StreamSubscriptionEntity.builder()
                    .sessionId("sess-1")
                    .subscriptionKey("stock:AAPL")
                    .resource("stock")
                    .intervalMs(1000L)
                    .subscribedAt(Instant.now())
                    .active(true)
                    .build();

            entity.markUnsubscribed();

            assertThat(entity.getActive()).isFalse();
            assertThat(entity.getUnsubscribedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should return true when active is true")
        void shouldReturnTrueWhenActive() {
            StreamSubscriptionEntity entity = StreamSubscriptionEntity.builder()
                    .active(true)
                    .build();

            assertThat(entity.isActive()).isTrue();
        }

        @Test
        @DisplayName("should return false when active is false")
        void shouldReturnFalseWhenInactive() {
            StreamSubscriptionEntity entity = StreamSubscriptionEntity.builder()
                    .active(false)
                    .build();

            assertThat(entity.isActive()).isFalse();
        }

        @Test
        @DisplayName("should return false when active is null")
        void shouldReturnFalseWhenNull() {
            StreamSubscriptionEntity entity = new StreamSubscriptionEntity();
            entity.setActive(null);

            assertThat(entity.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should default active to true")
        void shouldDefaultActiveToTrue() {
            StreamSubscriptionEntity entity = StreamSubscriptionEntity.builder()
                    .sessionId("sess-1")
                    .subscriptionKey("test")
                    .resource("test")
                    .intervalMs(1000L)
                    .subscribedAt(Instant.now())
                    .build();

            assertThat(entity.getActive()).isTrue();
        }
    }
}
