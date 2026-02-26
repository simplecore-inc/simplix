package dev.simplecore.simplix.stream.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for stream statistics in admin API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamStats {

    private long activeSessions;
    private long activeSchedulers;
    private long totalSubscriptions;
    private long messagesDelivered;
    private long messagesFailed;
    private String mode;
    private boolean sessionRegistryAvailable;
    private boolean broadcastServiceAvailable;
    private Instant serverStartedAt;
    private String instanceId;
    private boolean distributedAdminEnabled;
}
