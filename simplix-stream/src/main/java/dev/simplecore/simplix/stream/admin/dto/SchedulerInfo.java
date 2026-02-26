package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.core.enums.SchedulerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * DTO for scheduler information in admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerInfo {

    private String subscriptionKey;
    private String resource;
    private Map<String, Object> params;
    private SchedulerState state;
    private long intervalMs;
    private Set<String> subscribers;
    private int subscriberCount;
    private Instant createdAt;
    private Instant lastExecutedAt;
    private long executionCount;
    private long errorCount;
    private int consecutiveErrors;
    private String instanceId;
}
