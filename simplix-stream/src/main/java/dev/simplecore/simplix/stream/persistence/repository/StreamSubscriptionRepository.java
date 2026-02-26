package dev.simplecore.simplix.stream.persistence.repository;

import dev.simplecore.simplix.stream.persistence.entity.StreamSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for stream subscription entities.
 */
public interface StreamSubscriptionRepository extends JpaRepository<StreamSubscriptionEntity, Long> {

    /**
     * Find all subscriptions by session ID.
     *
     * @param sessionId the session ID
     * @return list of subscriptions
     */
    List<StreamSubscriptionEntity> findBySessionId(String sessionId);

    /**
     * Find all active subscriptions by session ID.
     *
     * @param sessionId the session ID
     * @return list of active subscriptions
     */
    List<StreamSubscriptionEntity> findBySessionIdAndActiveTrue(String sessionId);

    /**
     * Find a subscription by session ID and subscription key.
     *
     * @param sessionId       the session ID
     * @param subscriptionKey the subscription key
     * @return the subscription if found
     */
    Optional<StreamSubscriptionEntity> findBySessionIdAndSubscriptionKey(
            String sessionId, String subscriptionKey);

    /**
     * Find all active subscriptions by subscription key.
     *
     * @param subscriptionKey the subscription key
     * @return list of subscriptions
     */
    List<StreamSubscriptionEntity> findBySubscriptionKeyAndActiveTrue(String subscriptionKey);

    /**
     * Find all active subscriptions by resource.
     *
     * @param resource the resource name
     * @return list of subscriptions
     */
    List<StreamSubscriptionEntity> findByResourceAndActiveTrue(String resource);

    /**
     * Count active subscriptions by session ID.
     *
     * @param sessionId the session ID
     * @return the count
     */
    long countBySessionIdAndActiveTrue(String sessionId);

    /**
     * Count active subscriptions by subscription key.
     *
     * @param subscriptionKey the subscription key
     * @return the count
     */
    long countBySubscriptionKeyAndActiveTrue(String subscriptionKey);

    /**
     * Count active subscriptions by resource.
     *
     * @param resource the resource name
     * @return the count
     */
    long countByResourceAndActiveTrue(String resource);

    /**
     * Count all active subscriptions.
     *
     * @return the count
     */
    long countByActiveTrue();

    /**
     * Mark subscriptions as inactive for a session.
     *
     * @param sessionId the session ID
     * @return the number of updated subscriptions
     */
    @Modifying
    @Query("UPDATE StreamSubscriptionEntity s SET s.active = false, s.unsubscribedAt = CURRENT_TIMESTAMP WHERE s.sessionId = :sessionId AND s.active = true")
    int deactivateBySessionId(@Param("sessionId") String sessionId);

    /**
     * Mark a specific subscription as inactive.
     *
     * @param sessionId       the session ID
     * @param subscriptionKey the subscription key
     * @return the number of updated subscriptions
     */
    @Modifying
    @Query("UPDATE StreamSubscriptionEntity s SET s.active = false, s.unsubscribedAt = CURRENT_TIMESTAMP WHERE s.sessionId = :sessionId AND s.subscriptionKey = :subscriptionKey AND s.active = true")
    int deactivateBySessionIdAndSubscriptionKey(
            @Param("sessionId") String sessionId,
            @Param("subscriptionKey") String subscriptionKey);

    /**
     * Delete inactive subscriptions older than the specified retention period.
     *
     * @param sessionIds list of session IDs to delete subscriptions for
     * @return the number of deleted subscriptions
     */
    @Modifying
    @Query("DELETE FROM StreamSubscriptionEntity s WHERE s.sessionId IN :sessionIds")
    int deleteBySessionIdIn(@Param("sessionIds") List<String> sessionIds);

    /**
     * Get subscription statistics grouped by resource.
     *
     * @return list of resource and count pairs
     */
    @Query("SELECT s.resource, COUNT(s) FROM StreamSubscriptionEntity s WHERE s.active = true GROUP BY s.resource ORDER BY COUNT(s) DESC")
    List<Object[]> getResourceSubscriptionCounts();

    /**
     * Find session IDs subscribed to a specific subscription key.
     *
     * @param subscriptionKey the subscription key
     * @return list of session IDs
     */
    @Query("SELECT s.sessionId FROM StreamSubscriptionEntity s WHERE s.subscriptionKey = :subscriptionKey AND s.active = true")
    List<String> findSessionIdsBySubscriptionKey(@Param("subscriptionKey") String subscriptionKey);
}
