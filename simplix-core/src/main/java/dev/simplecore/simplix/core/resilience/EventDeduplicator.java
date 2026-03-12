package dev.simplecore.simplix.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Filters duplicate events using a composite key within a sliding time window.
 *
 * <p>Events within the deduplication window that share the same composite key
 * (id + timestamp + eventKey) are considered duplicates and should be filtered out.
 *
 * <p>Expired entries are lazily cleaned up at throttled intervals to prevent
 * unbounded memory growth while avoiding excessive iteration on every call.
 */
public class EventDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(EventDeduplicator.class);
    private static final long CLEANUP_INTERVAL_MS = 1000L;

    private final long windowMs;
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(0);

    /**
     * Create a new deduplicator with the specified time window.
     *
     * @param windowSeconds the deduplication window in seconds
     */
    public EventDeduplicator(long windowSeconds) {
        this.windowMs = windowSeconds * 1000L;
    }

    /**
     * Check whether the given event is a duplicate.
     *
     * @param id             the source identifier (e.g., device ID)
     * @param eventTimestamp  the event timestamp in epoch millis
     * @param eventKey        a distinguishing key (e.g., category + action + source)
     * @return true if this event was already seen within the deduplication window
     */
    public boolean isDuplicate(String id, long eventTimestamp, String eventKey) {
        cleanupIfNeeded();
        String key = id + ":" + eventTimestamp + ":" + eventKey;
        long now = System.currentTimeMillis();
        Long previous = seen.putIfAbsent(key, now);
        if (previous != null) {
            log.trace("Duplicate event detected: id={}, ts={}, key={}", id, eventTimestamp, eventKey);
            return true;
        }
        return false;
    }

    /**
     * Get the number of entries currently tracked in the deduplication window.
     *
     * @return the size of the deduplication cache
     */
    public int getTrackedCount() {
        return seen.size();
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        long last = lastCleanupTime.get();
        if (now - last < CLEANUP_INTERVAL_MS) {
            return;
        }
        if (!lastCleanupTime.compareAndSet(last, now)) {
            return;
        }
        long cutoff = now - windowMs;
        seen.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }
}
