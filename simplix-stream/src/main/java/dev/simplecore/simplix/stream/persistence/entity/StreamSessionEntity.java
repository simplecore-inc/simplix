package dev.simplecore.simplix.stream.persistence.entity;

import dev.simplecore.simplix.stream.core.enums.SessionState;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for stream sessions.
 * <p>
 * Stores session information in the database for persistence and
 * cross-server session recovery.
 */
@Entity
@Table(name = "stream_sessions", indexes = {
        @Index(name = "idx_stream_session_user_id", columnList = "userId"),
        @Index(name = "idx_stream_session_state", columnList = "state"),
        @Index(name = "idx_stream_session_instance_id", columnList = "instanceId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamSessionEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", length = 20, nullable = false)
    private TransportType transportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private SessionState state;

    @Column(name = "instance_id", length = 64, nullable = false)
    private String instanceId;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Lob
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "messages_sent")
    @Builder.Default
    private Long messagesSent = 0L;

    @Column(name = "bytes_sent")
    @Builder.Default
    private Long bytesSent = 0L;

    /**
     * Check if the session is active (connected or disconnected within grace period).
     *
     * @return true if active
     */
    public boolean isActive() {
        return state == SessionState.CONNECTED || state == SessionState.DISCONNECTED;
    }

    /**
     * Mark the session as disconnected.
     */
    public void markDisconnected() {
        if (this.state == SessionState.CONNECTED) {
            this.state = SessionState.DISCONNECTED;
            this.disconnectedAt = Instant.now();
        }
    }

    /**
     * Mark the session as reconnected.
     */
    public void markReconnected() {
        if (this.state == SessionState.DISCONNECTED) {
            this.state = SessionState.CONNECTED;
            this.disconnectedAt = null;
            this.lastActiveAt = Instant.now();
        }
    }

    /**
     * Mark the session as terminated.
     */
    public void markTerminated() {
        this.state = SessionState.TERMINATED;
        this.terminatedAt = Instant.now();
    }

    /**
     * Update the last active timestamp.
     */
    public void touch() {
        this.lastActiveAt = Instant.now();
    }

    /**
     * Increment message count and bytes.
     *
     * @param bytes the bytes sent
     */
    public void incrementStats(long bytes) {
        this.messagesSent = (this.messagesSent != null ? this.messagesSent : 0L) + 1;
        this.bytesSent = (this.bytesSent != null ? this.bytesSent : 0L) + bytes;
    }
}
