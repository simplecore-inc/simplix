package dev.simplecore.simplix.messaging.broker.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SubscriptionHealthTracker")
class SubscriptionHealthTrackerTest {

    private Object createTracker(String streamKey, String encoding) throws Exception {
        Class<?> trackerClass = Class.forName(
                "dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber$SubscriptionHealthTracker");
        Constructor<?> ctor = trackerClass.getDeclaredConstructor(String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(streamKey, encoding);
    }

    private boolean isHealthy(Object tracker) throws Exception {
        Method m = tracker.getClass().getDeclaredMethod("isHealthy");
        m.setAccessible(true);
        return (boolean) m.invoke(tracker);
    }

    private void onError(Object tracker, Throwable t) throws Exception {
        Method m = tracker.getClass().getDeclaredMethod("onError", Throwable.class);
        m.setAccessible(true);
        m.invoke(tracker, t);
    }

    private void recordSuccess(Object tracker) throws Exception {
        Method m = tracker.getClass().getDeclaredMethod("recordSuccess");
        m.setAccessible(true);
        m.invoke(tracker);
    }

    @Nested
    @DisplayName("health tracking")
    class HealthTests {

        @Test
        @DisplayName("should be healthy initially")
        void shouldBeHealthyInitially() throws Exception {
            Object tracker = createTracker("test:stream", "BASE64");
            assertThat(isHealthy(tracker)).isTrue();
        }

        @Test
        @DisplayName("should remain healthy after fewer than 5 errors")
        void shouldRemainHealthyAfterFewErrors() throws Exception {
            Object tracker = createTracker("test:stream", "BASE64");
            for (int i = 0; i < 4; i++) {
                onError(tracker, new RuntimeException("test error"));
            }
            assertThat(isHealthy(tracker)).isTrue();
        }

        @Test
        @DisplayName("should become unhealthy after 5 consecutive errors")
        void shouldBecomeUnhealthyAfterThreshold() throws Exception {
            Object tracker = createTracker("test:stream", "BASE64");
            for (int i = 0; i < 5; i++) {
                onError(tracker, new RuntimeException("test error"));
            }
            assertThat(isHealthy(tracker)).isFalse();
        }

        @Test
        @DisplayName("should recover after a success")
        void shouldRecoverAfterSuccess() throws Exception {
            Object tracker = createTracker("test:stream", "BASE64");
            // Accumulate some errors
            for (int i = 0; i < 3; i++) {
                onError(tracker, new RuntimeException("error"));
            }
            // Success resets counter
            recordSuccess(tracker);
            assertThat(isHealthy(tracker)).isTrue();
        }

        @Test
        @DisplayName("recordSuccess should be no-op when no errors")
        void recordSuccessShouldBeNoOpWhenNoErrors() throws Exception {
            Object tracker = createTracker("test:stream", "RAW");
            recordSuccess(tracker);
            assertThat(isHealthy(tracker)).isTrue();
        }
    }
}
