package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.core.enums.SchedulerState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SchedulerInfo DTO.
 */
@DisplayName("SchedulerInfo")
class SchedulerInfoTest {

    @Test
    @DisplayName("should build with all fields")
    void shouldBuildWithAllFields() {
        Instant now = Instant.now();
        Map<String, Object> params = Map.of("symbol", "AAPL");
        Set<String> subscribers = Set.of("sess-1", "sess-2");

        SchedulerInfo info = SchedulerInfo.builder()
                .subscriptionKey("stock:abc")
                .resource("stock")
                .params(params)
                .state(SchedulerState.RUNNING)
                .intervalMs(5000)
                .subscribers(subscribers)
                .subscriberCount(2)
                .createdAt(now)
                .lastExecutedAt(now)
                .executionCount(100)
                .errorCount(3)
                .consecutiveErrors(0)
                .instanceId("inst-1")
                .build();

        assertThat(info.getSubscriptionKey()).isEqualTo("stock:abc");
        assertThat(info.getResource()).isEqualTo("stock");
        assertThat(info.getParams()).containsEntry("symbol", "AAPL");
        assertThat(info.getState()).isEqualTo(SchedulerState.RUNNING);
        assertThat(info.getIntervalMs()).isEqualTo(5000);
        assertThat(info.getSubscribers()).hasSize(2);
        assertThat(info.getSubscriberCount()).isEqualTo(2);
        assertThat(info.getCreatedAt()).isEqualTo(now);
        assertThat(info.getLastExecutedAt()).isEqualTo(now);
        assertThat(info.getExecutionCount()).isEqualTo(100);
        assertThat(info.getErrorCount()).isEqualTo(3);
        assertThat(info.getConsecutiveErrors()).isZero();
        assertThat(info.getInstanceId()).isEqualTo("inst-1");
    }

    @Test
    @DisplayName("should support no-args constructor")
    void shouldSupportNoArgsConstructor() {
        SchedulerInfo info = new SchedulerInfo();

        assertThat(info.getSubscriptionKey()).isNull();
        assertThat(info.getIntervalMs()).isZero();
    }
}
