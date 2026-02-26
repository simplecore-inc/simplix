package dev.simplecore.simplix.stream.core.scheduler;

import dev.simplecore.simplix.stream.core.enums.SchedulerState;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a scheduler for a specific subscription (resource + params).
 * <p>
 * Tracks subscribers, execution statistics, and state transitions.
 */
@Getter
@ToString(exclude = {"subscribers", "scheduledFuture"})
public class SubscriptionScheduler {

    private final SubscriptionKey key;
    private final Duration interval;
    private final Instant createdAt;

    private final Set<String> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicLong executionCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

    private volatile SchedulerState state = SchedulerState.CREATED;
    private volatile Instant lastExecutedAt;
    private volatile Instant lastSuccessAt;
    private volatile String lastError;

    private volatile ScheduledFuture<?> scheduledFuture;

    public SubscriptionScheduler(SubscriptionKey key, Duration interval) {
        this.key = key;
        this.interval = interval;
        this.createdAt = Instant.now();
    }

    /**
     * Add a subscriber to this scheduler.
     *
     * @param sessionId the session ID
     * @return true if added (not already present)
     */
    public boolean addSubscriber(String sessionId) {
        return subscribers.add(sessionId);
    }

    /**
     * Remove a subscriber from this scheduler.
     *
     * @param sessionId the session ID
     * @return true if removed
     */
    public boolean removeSubscriber(String sessionId) {
        return subscribers.remove(sessionId);
    }

    /**
     * Get the current subscriber count.
     *
     * @return the count
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    /**
     * Get the subscribers.
     *
     * @return unmodifiable set of session IDs
     */
    public Set<String> getSubscribers() {
        return Collections.unmodifiableSet(subscribers);
    }

    /**
     * Check if this scheduler has any subscribers.
     *
     * @return true if has subscribers
     */
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    /**
     * Record a successful execution.
     */
    public void recordSuccess() {
        executionCount.incrementAndGet();
        successCount.incrementAndGet();
        consecutiveErrors.set(0);
        lastExecutedAt = Instant.now();
        lastSuccessAt = lastExecutedAt;
        lastError = null;

        if (state == SchedulerState.CREATED || state == SchedulerState.ERROR) {
            state = SchedulerState.RUNNING;
        }
    }

    /**
     * Record a failed execution.
     *
     * @param error        the error message
     * @param maxConsecutive maximum consecutive errors before ERROR state
     */
    public void recordError(String error, int maxConsecutive) {
        executionCount.incrementAndGet();
        errorCount.incrementAndGet();
        int consecutive = consecutiveErrors.incrementAndGet();
        lastExecutedAt = Instant.now();
        lastError = error;

        if (consecutive >= maxConsecutive && state == SchedulerState.RUNNING) {
            state = SchedulerState.ERROR;
        }
    }

    /**
     * Mark the scheduler as stopped.
     */
    public void stop() {
        state = SchedulerState.STOPPED;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    /**
     * Set the scheduled future.
     *
     * @param future the future
     */
    public void setScheduledFuture(ScheduledFuture<?> future) {
        this.scheduledFuture = future;
    }

    /**
     * Get interval in milliseconds.
     *
     * @return the interval in ms
     */
    public long getIntervalMs() {
        return interval.toMillis();
    }

    /**
     * Check if scheduler is active (not stopped).
     *
     * @return true if active
     */
    public boolean isActive() {
        return state != SchedulerState.STOPPED;
    }

    /**
     * Get the execution count.
     *
     * @return the count
     */
    public long getExecutionCount() {
        return executionCount.get();
    }

    /**
     * Get the success count.
     *
     * @return the count
     */
    public long getSuccessCount() {
        return successCount.get();
    }

    /**
     * Get the error count.
     *
     * @return the count
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Get the consecutive error count.
     *
     * @return the count
     */
    public int getConsecutiveErrors() {
        return consecutiveErrors.get();
    }
}
