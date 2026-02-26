package dev.simplecore.simplix.stream.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for stream subscriptions.
 * <p>
 * Stores subscription information in the database for persistence and
 * cross-server subscription recovery.
 */
@Entity
@Table(name = "stream_subscriptions", indexes = {
        @Index(name = "idx_stream_subscription_session_id", columnList = "sessionId"),
        @Index(name = "idx_stream_subscription_key", columnList = "subscriptionKey"),
        @Index(name = "idx_stream_subscription_resource", columnList = "resource"),
        @Index(name = "idx_stream_subscription_active", columnList = "active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_stream_subscription_session_key",
                columnNames = {"sessionId", "subscriptionKey"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 36, nullable = false)
    private String sessionId;

    @Column(name = "subscription_key", nullable = false)
    private String subscriptionKey;

    @Column(name = "resource", length = 100, nullable = false)
    private String resource;

    @Lob
    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson;

    @Column(name = "interval_ms", nullable = false)
    private Long intervalMs;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Mark the subscription as inactive.
     */
    public void markUnsubscribed() {
        this.active = false;
        this.unsubscribedAt = Instant.now();
    }

    /**
     * Check if the subscription is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.active);
    }
}
