package dev.simplecore.simplix.stream.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for stream server instances.
 * <p>
 * Tracks active server instances for distributed mode, enabling
 * orphan session detection when a server goes down.
 */
@Entity
@Table(name = "stream_server_instances", indexes = {
        @Index(name = "idx_stream_server_status", columnList = "status"),
        @Index(name = "idx_stream_server_heartbeat", columnList = "lastHeartbeatAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamServerInstanceEntity {

    /**
     * Server instance status.
     */
    public enum Status {
        /** Server is active and healthy */
        ACTIVE,
        /** Server is suspected to be down (missed heartbeats) */
        SUSPECTED,
        /** Server is confirmed dead */
        DEAD
    }

    @Id
    @Column(name = "instance_id", length = 64)
    private String instanceId;

    @Column(name = "hostname")
    private String hostname;

    @Column(name = "port")
    private Integer port;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "active_sessions")
    @Builder.Default
    private Integer activeSessions = 0;

    @Column(name = "active_schedulers")
    @Builder.Default
    private Integer activeSchedulers = 0;

    /**
     * Update the heartbeat timestamp.
     */
    public void heartbeat() {
        this.lastHeartbeatAt = Instant.now();
        this.status = Status.ACTIVE;
    }

    /**
     * Update statistics.
     *
     * @param sessions   active session count
     * @param schedulers active scheduler count
     */
    public void updateStats(int sessions, int schedulers) {
        this.activeSessions = sessions;
        this.activeSchedulers = schedulers;
    }

    /**
     * Mark the server as suspected.
     */
    public void markSuspected() {
        this.status = Status.SUSPECTED;
    }

    /**
     * Mark the server as dead.
     */
    public void markDead() {
        this.status = Status.DEAD;
    }

    /**
     * Check if the server is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    /**
     * Check if the server is considered alive (active or suspected).
     *
     * @return true if alive
     */
    public boolean isAlive() {
        return this.status != Status.DEAD;
    }
}
