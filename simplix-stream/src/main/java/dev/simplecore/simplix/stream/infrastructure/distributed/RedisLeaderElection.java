package dev.simplecore.simplix.stream.infrastructure.distributed;

import dev.simplecore.simplix.stream.config.StreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis-based leader election for distributed scheduler coordination.
 * <p>
 * Uses Redis SETNX (SET if Not eXists) for leader election. Each subscription key
 * can have one leader that is responsible for running the scheduler.
 */
@Slf4j
public class RedisLeaderElection {

    private static final String LEADER_KEY_PREFIX = "stream:leader:";

    private final StringRedisTemplate redisTemplate;
    private final ScheduledExecutorService scheduler;
    private final String instanceId;
    private final Duration leaderTtl;
    private final Duration renewInterval;
    private final String keyPrefix;

    // Track which keys this instance is leader for
    private final Map<String, ScheduledFuture<?>> leadershipRenewals = new ConcurrentHashMap<>();
    private final Map<String, Consumer<Boolean>> leadershipCallbacks = new ConcurrentHashMap<>();

    public RedisLeaderElection(
            StringRedisTemplate redisTemplate,
            ScheduledExecutorService scheduler,
            String instanceId,
            StreamProperties properties) {
        this.redisTemplate = redisTemplate;
        this.scheduler = scheduler;
        this.instanceId = instanceId;
        this.leaderTtl = properties.getDistributed().getLeaderElection().getTtl();
        this.renewInterval = properties.getDistributed().getLeaderElection().getRenewInterval();
        this.keyPrefix = properties.getDistributed().getRegistry().getKeyPrefix();
    }

    /**
     * Attempt to become leader for a subscription key.
     *
     * @param subscriptionKey the subscription key
     * @param callback        callback invoked when leadership status changes
     * @return true if this instance became leader
     */
    public boolean tryBecomeLeader(String subscriptionKey, Consumer<Boolean> callback) {
        String redisKey = getLeaderKey(subscriptionKey);

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, instanceId, leaderTtl);

            if (Boolean.TRUE.equals(acquired)) {
                log.info("Acquired leadership for: {} (instance: {})", subscriptionKey, instanceId);

                // Store callback and start renewal
                leadershipCallbacks.put(subscriptionKey, callback);
                startLeadershipRenewal(subscriptionKey);

                if (callback != null) {
                    callback.accept(true);
                }
                return true;
            }

            // Check if we're already the leader
            String currentLeader = redisTemplate.opsForValue().get(redisKey);
            if (instanceId.equals(currentLeader)) {
                return true;
            }

            log.debug("Failed to acquire leadership for {}: current leader is {}",
                    subscriptionKey, currentLeader);
            return false;

        } catch (Exception e) {
            log.error("Error during leader election for {}: {}", subscriptionKey, e.getMessage());
            return false;
        }
    }

    /**
     * Check if this instance is leader for a subscription key.
     *
     * @param subscriptionKey the subscription key
     * @return true if this instance is leader
     */
    public boolean isLeader(String subscriptionKey) {
        String redisKey = getLeaderKey(subscriptionKey);

        try {
            String currentLeader = redisTemplate.opsForValue().get(redisKey);
            return instanceId.equals(currentLeader);
        } catch (Exception e) {
            log.error("Error checking leadership for {}: {}", subscriptionKey, e.getMessage());
            return false;
        }
    }

    /**
     * Release leadership for a subscription key.
     *
     * @param subscriptionKey the subscription key
     */
    public void releaseLeadership(String subscriptionKey) {
        String redisKey = getLeaderKey(subscriptionKey);

        // Stop renewal task
        ScheduledFuture<?> renewalTask = leadershipRenewals.remove(subscriptionKey);
        if (renewalTask != null) {
            renewalTask.cancel(false);
        }

        // Remove callback
        Consumer<Boolean> callback = leadershipCallbacks.remove(subscriptionKey);

        try {
            // Only delete if we're the current leader
            String currentLeader = redisTemplate.opsForValue().get(redisKey);
            if (instanceId.equals(currentLeader)) {
                redisTemplate.delete(redisKey);
                log.info("Released leadership for: {} (instance: {})", subscriptionKey, instanceId);
            }
        } catch (Exception e) {
            log.error("Error releasing leadership for {}: {}", subscriptionKey, e.getMessage());
        }

        if (callback != null) {
            callback.accept(false);
        }
    }

    /**
     * Release all leadership held by this instance.
     */
    public void releaseAll() {
        log.info("Releasing all leadership (instance: {})", instanceId);

        // Copy keys to avoid concurrent modification
        var keys = leadershipRenewals.keySet().toArray(new String[0]);
        for (String key : keys) {
            releaseLeadership(key);
        }
    }

    /**
     * Get the current leader for a subscription key.
     *
     * @param subscriptionKey the subscription key
     * @return the leader instance ID, or null if no leader
     */
    public String getLeader(String subscriptionKey) {
        String redisKey = getLeaderKey(subscriptionKey);

        try {
            return redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("Error getting leader for {}: {}", subscriptionKey, e.getMessage());
            return null;
        }
    }

    private void startLeadershipRenewal(String subscriptionKey) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> renewLeadership(subscriptionKey),
                renewInterval.toMillis(),
                renewInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );

        leadershipRenewals.put(subscriptionKey, task);
    }

    private void renewLeadership(String subscriptionKey) {
        String redisKey = getLeaderKey(subscriptionKey);

        try {
            // Only renew if we're still the leader
            String currentLeader = redisTemplate.opsForValue().get(redisKey);
            if (instanceId.equals(currentLeader)) {
                redisTemplate.expire(redisKey, leaderTtl);
                log.trace("Renewed leadership for: {}", subscriptionKey);
            } else {
                // Lost leadership
                log.warn("Lost leadership for: {} (current leader: {})",
                        subscriptionKey, currentLeader);

                ScheduledFuture<?> task = leadershipRenewals.remove(subscriptionKey);
                if (task != null) {
                    task.cancel(false);
                }

                Consumer<Boolean> callback = leadershipCallbacks.remove(subscriptionKey);
                if (callback != null) {
                    callback.accept(false);
                }
            }
        } catch (Exception e) {
            log.error("Error renewing leadership for {}: {}", subscriptionKey, e.getMessage());
        }
    }

    private String getLeaderKey(String subscriptionKey) {
        return keyPrefix + LEADER_KEY_PREFIX + subscriptionKey;
    }

    /**
     * Get the number of keys this instance is leader for.
     *
     * @return the count
     */
    public int getLeadershipCount() {
        return leadershipRenewals.size();
    }
}
