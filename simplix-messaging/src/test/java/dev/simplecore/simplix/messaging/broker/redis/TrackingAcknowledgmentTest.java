package dev.simplecore.simplix.messaging.broker.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrackingAcknowledgment")
class TrackingAcknowledgmentTest {

    private Object createTrackingAck() throws Exception {
        Class<?> clazz = Class.forName(
                "dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber$TrackingAcknowledgment");
        Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private boolean shouldAcknowledge(Object ack) throws Exception {
        Method m = ack.getClass().getDeclaredMethod("shouldAcknowledge");
        m.setAccessible(true);
        return (boolean) m.invoke(ack);
    }

    private void ack(Object ack) throws Exception {
        Method m = ack.getClass().getMethod("ack");
        m.invoke(ack);
    }

    private void nack(Object ack, boolean requeue) throws Exception {
        Method m = ack.getClass().getMethod("nack", boolean.class);
        m.invoke(ack, requeue);
    }

    private void reject(Object ack, String reason) throws Exception {
        Method m = ack.getClass().getMethod("reject", String.class);
        m.invoke(ack, reason);
    }

    @Nested
    @DisplayName("Tracking behavior")
    class TrackingTests {

        @Test
        @DisplayName("shouldAcknowledge should return false initially")
        void shouldNotAcknowledgeInitially() throws Exception {
            Object ack = createTrackingAck();
            assertThat(shouldAcknowledge(ack)).isFalse();
        }

        @Test
        @DisplayName("shouldAcknowledge should return true after ack()")
        void shouldReturnTrueAfterAck() throws Exception {
            Object ack = createTrackingAck();
            ack(ack);
            assertThat(shouldAcknowledge(ack)).isTrue();
        }

        @Test
        @DisplayName("shouldAcknowledge should return true after reject()")
        void shouldReturnTrueAfterReject() throws Exception {
            Object ack = createTrackingAck();
            reject(ack, "bad message");
            assertThat(shouldAcknowledge(ack)).isTrue();
        }

        @Test
        @DisplayName("shouldAcknowledge should return true after nack(false)")
        void shouldReturnTrueAfterNackNoRequeue() throws Exception {
            Object ack = createTrackingAck();
            nack(ack, false);
            assertThat(shouldAcknowledge(ack)).isTrue();
        }

        @Test
        @DisplayName("shouldAcknowledge should return false after nack(true)")
        void shouldReturnFalseAfterNackWithRequeue() throws Exception {
            Object ack = createTrackingAck();
            nack(ack, true);
            assertThat(shouldAcknowledge(ack)).isFalse();
        }
    }
}
