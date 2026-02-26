package dev.simplecore.simplix.stream.admin.dto;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for session information in admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    private String sessionId;
    private String userId;
    private TransportType transportType;
    private SessionState state;
    private Instant connectedAt;
    private Instant lastActiveAt;
    private Set<String> subscriptions;
    private int subscriptionCount;
    private String instanceId;
}
