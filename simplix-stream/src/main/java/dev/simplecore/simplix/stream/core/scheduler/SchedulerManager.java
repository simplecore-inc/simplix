package dev.simplecore.simplix.stream.core.scheduler;

import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollector;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisLeaderElection;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages dynamic schedulers for data collection.
 * <p>
 * Creates schedulers when subscriptions are added, shares schedulers for
 * identical subscriptions, and terminates schedulers when no subscribers remain.
 * <p>
 * In distributed mode, uses leader election to ensure only one instance
 * runs the scheduler for each subscription key.
 */
@Slf4j
public class SchedulerManager {

    private final SimpliXStreamDataCollectorRegistry collectorRegistry;
    private final BroadcastService broadcastService;
    private final StreamProperties properties;
    private final ScheduledExecutorService scheduledExecutor;
    private final RedisLeaderElection leaderElection;

    private final Map<SubscriptionKey, SubscriptionScheduler> schedulers = new ConcurrentHashMap<>();

    // Track local subscribers even when not leader (for distributed mode)
    private final Map<SubscriptionKey, Set<String>> localSubscribers = new ConcurrentHashMap<>();

    // Track pending intervals for non-leader subscriptions
    private final Map<SubscriptionKey, Duration> pendingIntervals = new ConcurrentHashMap<>();

    public SchedulerManager(
            SimpliXStreamDataCollectorRegistry collectorRegistry,
            BroadcastService broadcastService,
            StreamProperties properties,
            ScheduledExecutorService scheduledExecutor) {
        this(collectorRegistry, broadcastService, properties, scheduledExecutor, null);
    }

    public SchedulerManager(
            SimpliXStreamDataCollectorRegistry collectorRegistry,
            BroadcastService broadcastService,
            StreamProperties properties,
            ScheduledExecutorService scheduledExecutor,
            RedisLeaderElection leaderElection) {
        this.collectorRegistry = collectorRegistry;
        this.broadcastService = broadcastService;
        this.properties = properties;
        this.scheduledExecutor = scheduledExecutor;
        this.leaderElection = leaderElection;

        if (leaderElection != null) {
            log.info("SchedulerManager initialized with leader election (distributed mode)");
        } else {
            log.info("SchedulerManager initialized in local mode");
        }
        log.info("  - Thread pool size: {}", properties.getScheduler().getThreadPoolSize());
    }

    /**
     * Add a subscriber to a scheduler.
     * <p>
     * Creates a new scheduler if one doesn't exist for this subscription key.
     * In distributed mode, attempts to become leader before creating scheduler.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     * @param interval  the requested interval
     */
    public synchronized void addSubscriber(SubscriptionKey key, String sessionId, Duration interval) {
        // Always track local subscribers (for distributed mode, they receive data via Pub/Sub)
        localSubscribers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        SubscriptionScheduler scheduler = schedulers.get(key);

        if (scheduler == null) {
            // Check max scheduler limit
            int maxSchedulers = properties.getScheduler().getMaxTotalSchedulers();
            if (maxSchedulers > 0 && schedulers.size() >= maxSchedulers) {
                log.warn("Maximum scheduler limit reached: {}", maxSchedulers);
                return;
            }

            // Clamp interval to configured bounds
            Duration actualInterval = clampInterval(interval);

            // Distributed mode: try to become leader
            if (leaderElection != null) {
                pendingIntervals.put(key, actualInterval);

                boolean isLeader = leaderElection.tryBecomeLeader(
                        key.toKeyString(),
                        acquired -> handleLeadershipChange(key, acquired)
                );

                if (!isLeader) {
                    log.debug("Not leader for {}, waiting for data via Pub/Sub", key.toKeyString());
                    return;
                }
            }

            scheduler = new SubscriptionScheduler(key, actualInterval);
            schedulers.put(key, scheduler);
            startScheduler(scheduler);

            log.info("Scheduler created: {} (interval={}ms, leader={})",
                    key.toKeyString(), actualInterval.toMillis(), leaderElection != null);
        }

        scheduler.addSubscriber(sessionId);
        log.debug("Subscriber added to scheduler: {} -> {} (total={})",
                sessionId, key.toKeyString(), scheduler.getSubscriberCount());
    }

    /**
     * Remove a subscriber from a scheduler.
     * <p>
     * Stops and removes the scheduler if no subscribers remain.
     * In distributed mode, releases leadership when last subscriber leaves.
     *
     * @param key       the subscription key
     * @param sessionId the subscriber session ID
     */
    public synchronized void removeSubscriber(SubscriptionKey key, String sessionId) {
        // Remove from local subscribers tracking
        Set<String> subscribers = localSubscribers.get(key);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) {
                localSubscribers.remove(key);
                pendingIntervals.remove(key);
            }
        }

        SubscriptionScheduler scheduler = schedulers.get(key);

        if (scheduler != null) {
            scheduler.removeSubscriber(sessionId);
            log.debug("Subscriber removed from scheduler: {} <- {} (remaining={})",
                    sessionId, key.toKeyString(), scheduler.getSubscriberCount());

            if (!scheduler.hasSubscribers()) {
                stopScheduler(scheduler);
                schedulers.remove(key);

                // Release leadership in distributed mode
                if (leaderElection != null) {
                    leaderElection.releaseLeadership(key.toKeyString());
                }

                log.info("Scheduler stopped (no subscribers): {}", key.toKeyString());
            }
        } else if (subscribers != null && subscribers.isEmpty() && leaderElection != null) {
            // No local scheduler but had local subscribers - ensure leadership is released
            leaderElection.releaseLeadership(key.toKeyString());
        }
    }

    /**
     * Handle leadership status change for a subscription key.
     * <p>
     * Called by RedisLeaderElection when leadership is acquired or lost.
     *
     * @param key      the subscription key
     * @param acquired true if leadership acquired, false if lost
     */
    private void handleLeadershipChange(SubscriptionKey key, boolean acquired) {
        synchronized (this) {
            if (acquired) {
                // Became leader - create scheduler if we have local subscribers
                Set<String> subscribers = localSubscribers.get(key);
                if (subscribers != null && !subscribers.isEmpty() && !schedulers.containsKey(key)) {
                    Duration interval = pendingIntervals.getOrDefault(key, properties.getScheduler().getDefaultInterval());

                    SubscriptionScheduler scheduler = new SubscriptionScheduler(key, interval);
                    schedulers.put(key, scheduler);
                    startScheduler(scheduler);

                    // Add all local subscribers to the scheduler
                    subscribers.forEach(scheduler::addSubscriber);

                    log.info("Scheduler created after acquiring leadership: {} (subscribers={})",
                            key.toKeyString(), subscribers.size());
                }
            } else {
                // Lost leadership - stop local scheduler
                SubscriptionScheduler scheduler = schedulers.remove(key);
                if (scheduler != null) {
                    stopScheduler(scheduler);
                    log.info("Scheduler stopped due to leadership loss: {}", key.toKeyString());
                }
            }
        }
    }

    /**
     * Get a scheduler by key.
     *
     * @param key the subscription key
     * @return the scheduler if found
     */
    public Optional<SubscriptionScheduler> getScheduler(SubscriptionKey key) {
        return Optional.ofNullable(schedulers.get(key));
    }

    /**
     * Get a scheduler by key string.
     *
     * @param keyString the subscription key string
     * @return the scheduler if found
     */
    public Optional<SubscriptionScheduler> getScheduler(String keyString) {
        return schedulers.entrySet().stream()
                .filter(e -> e.getKey().toKeyString().equals(keyString))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Get all active schedulers.
     *
     * @return collection of schedulers
     */
    public Collection<SubscriptionScheduler> getAllSchedulers() {
        return schedulers.values();
    }

    /**
     * Get the number of active schedulers.
     *
     * @return the count
     */
    public int getSchedulerCount() {
        return schedulers.size();
    }

    /**
     * Force stop a scheduler by key.
     *
     * @param key the subscription key
     * @return true if stopped
     */
    public synchronized boolean forceStopScheduler(SubscriptionKey key) {
        SubscriptionScheduler scheduler = schedulers.remove(key);
        if (scheduler != null) {
            stopScheduler(scheduler);
            log.info("Scheduler force stopped: {}", key.toKeyString());
            return true;
        }
        return false;
    }

    /**
     * Stop a scheduler by key string.
     *
     * @param keyString the subscription key string
     * @return true if stopped
     */
    public synchronized boolean stopScheduler(String keyString) {
        Optional<Map.Entry<SubscriptionKey, SubscriptionScheduler>> entry = schedulers.entrySet().stream()
                .filter(e -> e.getKey().toKeyString().equals(keyString))
                .findFirst();

        if (entry.isPresent()) {
            SubscriptionKey key = entry.get().getKey();
            SubscriptionScheduler scheduler = schedulers.remove(key);
            if (scheduler != null) {
                stopScheduler(scheduler);
                log.info("Scheduler stopped by admin: {}", keyString);
                return true;
            }
        }
        return false;
    }

    /**
     * Trigger immediate execution of a scheduler.
     *
     * @param keyString the subscription key string
     */
    public void triggerNow(String keyString) {
        getScheduler(keyString).ifPresent(scheduler -> {
            scheduledExecutor.execute(() -> executeScheduler(scheduler));
            log.info("Scheduler triggered immediately: {}", keyString);
        });
    }

    /**
     * Check if this instance is leader for a subscription key.
     *
     * @param key the subscription key
     * @return true if leader (or local mode)
     */
    public boolean isLeader(SubscriptionKey key) {
        if (leaderElection == null) {
            return true; // Local mode - always leader
        }
        return leaderElection.isLeader(key.toKeyString());
    }

    /**
     * Get the count of local subscribers for a key.
     *
     * @param key the subscription key
     * @return the count
     */
    public int getLocalSubscriberCount(SubscriptionKey key) {
        Set<String> subscribers = localSubscribers.get(key);
        return subscribers != null ? subscribers.size() : 0;
    }

    /**
     * Get all local subscriber session IDs for a given key.
     * <p>
     * Used by RedisBroadcaster (via SubscriberLookup) to find local subscribers
     * when handling cross-instance broadcast messages in distributed mode.
     *
     * @param key the subscription key
     * @return unmodifiable set of session IDs (empty if none)
     */
    public Set<String> getLocalSubscribers(SubscriptionKey key) {
        Set<String> subscribers = localSubscribers.get(key);
        return subscribers != null ? Collections.unmodifiableSet(subscribers) : Collections.emptySet();
    }

    /**
     * Create a SubscriberLookup that delegates to this manager's local subscribers.
     *
     * @return a SubscriberLookup backed by this manager
     */
    public SubscriberLookup asSubscriberLookup() {
        return this::getLocalSubscribers;
    }

    /**
     * Check if distributed mode is enabled.
     *
     * @return true if using leader election
     */
    public boolean isDistributedMode() {
        return leaderElection != null;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SchedulerManager with {} active schedulers", schedulers.size());

        // Release all leadership in distributed mode
        if (leaderElection != null) {
            leaderElection.releaseAll();
        }

        schedulers.values().forEach(this::stopScheduler);
        schedulers.clear();
        localSubscribers.clear();
        pendingIntervals.clear();
    }

    private void startScheduler(SubscriptionScheduler scheduler) {
        long intervalMs = scheduler.getIntervalMs();

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(
                () -> executeScheduler(scheduler),
                0, // Initial delay
                intervalMs,
                TimeUnit.MILLISECONDS
        );

        scheduler.setScheduledFuture(future);
    }

    private void stopScheduler(SubscriptionScheduler scheduler) {
        scheduler.stop();
    }

    private void executeScheduler(SubscriptionScheduler scheduler) {
        if (!scheduler.isActive() || !scheduler.hasSubscribers()) {
            return;
        }

        SubscriptionKey key = scheduler.getKey();

        try {
            // Get collector
            SimpliXStreamDataCollector collector = collectorRegistry.getCollector(key.getResource());

            // Collect data
            Object data = collector.collect(key.getParams());

            // Create message
            StreamMessage message = StreamMessage.data(key, data);

            // Broadcast to subscribers
            broadcastService.broadcast(key, message, scheduler.getSubscribers());

            // Record success
            scheduler.recordSuccess();

        } catch (Exception e) {
            int maxErrors = properties.getScheduler().getMaxConsecutiveErrors();
            scheduler.recordError(e.getMessage(), maxErrors);

            log.error("Scheduler execution error: {} - {}", key.toKeyString(), e.getMessage());

            // Optionally notify subscribers of error
            StreamMessage errorMessage = StreamMessage.error(key, "SCHEDULER_ERROR", e.getMessage());
            broadcastService.broadcast(key, errorMessage, scheduler.getSubscribers());
        }
    }

    private Duration clampInterval(Duration interval) {
        Duration min = properties.getScheduler().getMinInterval();
        Duration max = properties.getScheduler().getMaxInterval();

        if (interval.compareTo(min) < 0) {
            return min;
        }
        if (interval.compareTo(max) > 0) {
            return max;
        }
        return interval;
    }
}
