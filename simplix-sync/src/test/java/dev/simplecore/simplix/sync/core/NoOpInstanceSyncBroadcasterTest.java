package dev.simplecore.simplix.sync.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("NoOpInstanceSyncBroadcaster")
class NoOpInstanceSyncBroadcasterTest {

    private NoOpInstanceSyncBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new NoOpInstanceSyncBroadcaster();
    }

    @Nested
    @DisplayName("broadcast")
    class BroadcastTests {

        @Test
        @DisplayName("should not throw when broadcasting")
        void shouldNotThrowWhenBroadcasting() {
            assertThatCode(() -> broadcaster.broadcast("test-channel", new byte[]{1, 2, 3}))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should silently ignore null payload")
        void shouldIgnoreNullPayload() {
            assertThatCode(() -> broadcaster.broadcast("test-channel", null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should silently ignore null channel")
        void shouldIgnoreNullChannel() {
            assertThatCode(() -> broadcaster.broadcast(null, new byte[]{1}))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("should not throw when subscribing")
        void shouldNotThrowWhenSubscribing() {
            assertThatCode(() -> broadcaster.subscribe("test-channel", payload -> {}))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not invoke listener since it is no-op")
        void shouldNotInvokeListener() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            broadcaster.subscribe("test-channel", payload -> invoked.set(true));
            broadcaster.broadcast("test-channel", new byte[]{1, 2, 3});

            // In no-op mode, the listener should never be invoked
            assertThat(invoked.get()).isFalse();
        }

        @Test
        @DisplayName("should silently ignore null listener")
        void shouldIgnoreNullListener() {
            assertThatCode(() -> broadcaster.subscribe("test-channel", null))
                    .doesNotThrowAnyException();
        }
    }
}
