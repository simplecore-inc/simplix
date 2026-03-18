package dev.simplecore.simplix.stream.admin.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamStats DTO.
 */
@DisplayName("StreamStats")
class StreamStatsTest {

    @Test
    @DisplayName("should build with all fields")
    void shouldBuildWithAllFields() {
        Instant startedAt = Instant.now();

        StreamStats stats = StreamStats.builder()
                .activeSessions(10)
                .activeSchedulers(5)
                .totalSubscriptions(25)
                .messagesDelivered(1000)
                .messagesFailed(2)
                .mode("LOCAL")
                .sessionRegistryAvailable(true)
                .broadcastServiceAvailable(true)
                .serverStartedAt(startedAt)
                .instanceId("inst-1")
                .distributedAdminEnabled(false)
                .build();

        assertThat(stats.getActiveSessions()).isEqualTo(10);
        assertThat(stats.getActiveSchedulers()).isEqualTo(5);
        assertThat(stats.getTotalSubscriptions()).isEqualTo(25);
        assertThat(stats.getMessagesDelivered()).isEqualTo(1000);
        assertThat(stats.getMessagesFailed()).isEqualTo(2);
        assertThat(stats.getMode()).isEqualTo("LOCAL");
        assertThat(stats.isSessionRegistryAvailable()).isTrue();
        assertThat(stats.isBroadcastServiceAvailable()).isTrue();
        assertThat(stats.getServerStartedAt()).isEqualTo(startedAt);
        assertThat(stats.getInstanceId()).isEqualTo("inst-1");
        assertThat(stats.isDistributedAdminEnabled()).isFalse();
    }

    @Test
    @DisplayName("should support no-args constructor")
    void shouldSupportNoArgsConstructor() {
        StreamStats stats = new StreamStats();

        assertThat(stats.getActiveSessions()).isZero();
        assertThat(stats.getMode()).isNull();
    }
}
