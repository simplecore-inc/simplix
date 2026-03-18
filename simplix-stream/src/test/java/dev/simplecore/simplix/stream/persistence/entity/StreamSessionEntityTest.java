package dev.simplecore.simplix.stream.persistence.entity;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamSessionEntity.
 */
@DisplayName("StreamSessionEntity")
class StreamSessionEntityTest {

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should return true when state is CONNECTED")
        void shouldReturnTrueWhenConnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.CONNECTED)
                    .build();

            assertThat(entity.isActive()).isTrue();
        }

        @Test
        @DisplayName("should return true when state is DISCONNECTED")
        void shouldReturnTrueWhenDisconnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.DISCONNECTED)
                    .build();

            assertThat(entity.isActive()).isTrue();
        }

        @Test
        @DisplayName("should return false when state is TERMINATED")
        void shouldReturnFalseWhenTerminated() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.TERMINATED)
                    .build();

            assertThat(entity.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("markDisconnected()")
    class MarkDisconnected {

        @Test
        @DisplayName("should set state to DISCONNECTED and disconnectedAt when CONNECTED")
        void shouldMarkDisconnectedWhenConnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.CONNECTED)
                    .build();

            entity.markDisconnected();

            assertThat(entity.getState()).isEqualTo(SessionState.DISCONNECTED);
            assertThat(entity.getDisconnectedAt()).isNotNull();
        }

        @Test
        @DisplayName("should not change state when already DISCONNECTED")
        void shouldNotChangeWhenAlreadyDisconnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.DISCONNECTED)
                    .disconnectedAt(Instant.now().minusSeconds(60))
                    .build();
            Instant originalDisconnectedAt = entity.getDisconnectedAt();

            entity.markDisconnected();

            assertThat(entity.getState()).isEqualTo(SessionState.DISCONNECTED);
            assertThat(entity.getDisconnectedAt()).isEqualTo(originalDisconnectedAt);
        }
    }

    @Nested
    @DisplayName("markReconnected()")
    class MarkReconnected {

        @Test
        @DisplayName("should set state to CONNECTED when DISCONNECTED")
        void shouldReconnectWhenDisconnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.DISCONNECTED)
                    .disconnectedAt(Instant.now().minusSeconds(30))
                    .lastActiveAt(Instant.now().minusSeconds(60))
                    .build();

            entity.markReconnected();

            assertThat(entity.getState()).isEqualTo(SessionState.CONNECTED);
            assertThat(entity.getDisconnectedAt()).isNull();
            assertThat(entity.getLastActiveAt()).isNotNull();
        }

        @Test
        @DisplayName("should not change state when CONNECTED")
        void shouldNotChangeWhenConnected() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.CONNECTED)
                    .build();

            entity.markReconnected();

            assertThat(entity.getState()).isEqualTo(SessionState.CONNECTED);
        }
    }

    @Nested
    @DisplayName("markTerminated()")
    class MarkTerminated {

        @Test
        @DisplayName("should set state to TERMINATED and terminatedAt")
        void shouldMarkTerminated() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .state(SessionState.CONNECTED)
                    .build();

            entity.markTerminated();

            assertThat(entity.getState()).isEqualTo(SessionState.TERMINATED);
            assertThat(entity.getTerminatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("touch()")
    class Touch {

        @Test
        @DisplayName("should update lastActiveAt")
        void shouldUpdateLastActiveAt() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .lastActiveAt(Instant.now().minusSeconds(60))
                    .build();
            Instant before = entity.getLastActiveAt();

            entity.touch();

            assertThat(entity.getLastActiveAt()).isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("incrementStats()")
    class IncrementStats {

        @Test
        @DisplayName("should increment message count and bytes")
        void shouldIncrementStats() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .messagesSent(10L)
                    .bytesSent(500L)
                    .build();

            entity.incrementStats(100);

            assertThat(entity.getMessagesSent()).isEqualTo(11L);
            assertThat(entity.getBytesSent()).isEqualTo(600L);
        }

        @Test
        @DisplayName("should handle null messagesSent and bytesSent")
        void shouldHandleNullStats() {
            StreamSessionEntity entity = new StreamSessionEntity();
            entity.setMessagesSent(null);
            entity.setBytesSent(null);

            entity.incrementStats(50);

            assertThat(entity.getMessagesSent()).isEqualTo(1L);
            assertThat(entity.getBytesSent()).isEqualTo(50L);
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have default values for messagesSent and bytesSent")
        void shouldHaveDefaultValues() {
            StreamSessionEntity entity = StreamSessionEntity.builder()
                    .id("sess-1")
                    .userId("user-1")
                    .transportType(TransportType.SSE)
                    .state(SessionState.CONNECTED)
                    .instanceId("inst-1")
                    .connectedAt(Instant.now())
                    .lastActiveAt(Instant.now())
                    .build();

            assertThat(entity.getMessagesSent()).isEqualTo(0L);
            assertThat(entity.getBytesSent()).isEqualTo(0L);
        }
    }
}
