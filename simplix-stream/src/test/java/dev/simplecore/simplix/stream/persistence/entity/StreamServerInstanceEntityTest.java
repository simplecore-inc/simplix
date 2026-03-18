package dev.simplecore.simplix.stream.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamServerInstanceEntity.
 */
@DisplayName("StreamServerInstanceEntity")
class StreamServerInstanceEntityTest {

    @Nested
    @DisplayName("heartbeat()")
    class Heartbeat {

        @Test
        @DisplayName("should update lastHeartbeatAt and set status to ACTIVE")
        void shouldUpdateHeartbeatAndSetActive() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.SUSPECTED)
                    .lastHeartbeatAt(Instant.now().minusSeconds(60))
                    .build();

            entity.heartbeat();

            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.ACTIVE);
            assertThat(entity.getLastHeartbeatAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updateStats()")
    class UpdateStats {

        @Test
        @DisplayName("should update session and scheduler counts")
        void shouldUpdateCounts() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .activeSessions(0)
                    .activeSchedulers(0)
                    .build();

            entity.updateStats(10, 5);

            assertThat(entity.getActiveSessions()).isEqualTo(10);
            assertThat(entity.getActiveSchedulers()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("markSuspected()")
    class MarkSuspected {

        @Test
        @DisplayName("should set status to SUSPECTED")
        void shouldSetSuspected() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .build();

            entity.markSuspected();

            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.SUSPECTED);
        }
    }

    @Nested
    @DisplayName("markDead()")
    class MarkDead {

        @Test
        @DisplayName("should set status to DEAD")
        void shouldSetDead() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.SUSPECTED)
                    .build();

            entity.markDead();

            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.DEAD);
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should return true when ACTIVE")
        void shouldReturnTrueWhenActive() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .build();

            assertThat(entity.isActive()).isTrue();
        }

        @Test
        @DisplayName("should return false when SUSPECTED")
        void shouldReturnFalseWhenSuspected() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.SUSPECTED)
                    .build();

            assertThat(entity.isActive()).isFalse();
        }

        @Test
        @DisplayName("should return false when DEAD")
        void shouldReturnFalseWhenDead() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.DEAD)
                    .build();

            assertThat(entity.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("isAlive()")
    class IsAlive {

        @Test
        @DisplayName("should return true when ACTIVE")
        void shouldReturnTrueWhenActive() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.ACTIVE)
                    .build();

            assertThat(entity.isAlive()).isTrue();
        }

        @Test
        @DisplayName("should return true when SUSPECTED")
        void shouldReturnTrueWhenSuspected() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.SUSPECTED)
                    .build();

            assertThat(entity.isAlive()).isTrue();
        }

        @Test
        @DisplayName("should return false when DEAD")
        void shouldReturnFalseWhenDead() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .status(StreamServerInstanceEntity.Status.DEAD)
                    .build();

            assertThat(entity.isAlive()).isFalse();
        }
    }

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        @DisplayName("should have default values")
        void shouldHaveDefaultValues() {
            StreamServerInstanceEntity entity = StreamServerInstanceEntity.builder()
                    .instanceId("inst-1")
                    .startedAt(Instant.now())
                    .lastHeartbeatAt(Instant.now())
                    .build();

            assertThat(entity.getStatus()).isEqualTo(StreamServerInstanceEntity.Status.ACTIVE);
            assertThat(entity.getActiveSessions()).isEqualTo(0);
            assertThat(entity.getActiveSchedulers()).isEqualTo(0);
        }
    }
}
