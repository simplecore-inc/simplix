package dev.simplecore.simplix.stream.infrastructure.distributed;

import java.util.function.Consumer;

/**
 * Per-subscription-key leader election abstraction for distributed scheduler
 * coordination.
 *
 * <p>Implementations ensure that, for a given subscription key, only one
 * instance acts as the leader at any time. The leader is responsible for
 * running the scheduler that produces data; non-leaders track local
 * subscribers but rely on cross-instance broadcast for delivery.
 */
public interface LeaderElection {

    /**
     * Attempt to become leader for a subscription key.
     *
     * @param subscriptionKey the subscription key
     * @param callback        callback invoked with {@code true} when leadership is
     *                        acquired and {@code false} when it is lost; may be
     *                        {@code null}
     * @return {@code true} if this instance is now the leader
     */
    boolean tryBecomeLeader(String subscriptionKey, Consumer<Boolean> callback);

    /**
     * Check if this instance currently holds leadership for the given key.
     */
    boolean isLeader(String subscriptionKey);

    /**
     * Release leadership for a subscription key, if held.
     */
    void releaseLeadership(String subscriptionKey);

    /**
     * Release every key for which this instance holds leadership.
     */
    void releaseAll();

    /**
     * Get the current leader instance ID for a subscription key, or
     * {@code null} when no leader is recorded.
     */
    String getLeader(String subscriptionKey);

    /**
     * Number of keys for which this instance currently holds leadership.
     */
    int getLeadershipCount();
}
