package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SessionInfo DTO.
 */
@DisplayName("SessionInfo")
class SessionInfoTest {

    @Test
    @DisplayName("should build with all fields")
    void shouldBuildWithAllFields() {
        Instant now = Instant.now();
        Set<String> subscriptions = Set.of("stock:abc", "forex:def");

        SessionInfo info = SessionInfo.builder()
                .sessionId("sess-1")
                .userId("user-1")
                .transportType(TransportType.SSE)
                .state(SessionState.CONNECTED)
                .connectedAt(now)
                .lastActiveAt(now)
                .subscriptions(subscriptions)
                .subscriptionCount(2)
                .instanceId("inst-1")
                .build();

        assertThat(info.getSessionId()).isEqualTo("sess-1");
        assertThat(info.getUserId()).isEqualTo("user-1");
        assertThat(info.getTransportType()).isEqualTo(TransportType.SSE);
        assertThat(info.getState()).isEqualTo(SessionState.CONNECTED);
        assertThat(info.getConnectedAt()).isEqualTo(now);
        assertThat(info.getLastActiveAt()).isEqualTo(now);
        assertThat(info.getSubscriptions()).hasSize(2);
        assertThat(info.getSubscriptionCount()).isEqualTo(2);
        assertThat(info.getInstanceId()).isEqualTo("inst-1");
    }

    @Test
    @DisplayName("should support no-args constructor")
    void shouldSupportNoArgsConstructor() {
        SessionInfo info = new SessionInfo();

        assertThat(info.getSessionId()).isNull();
        assertThat(info.getSubscriptionCount()).isZero();
    }
}
