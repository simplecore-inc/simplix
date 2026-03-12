package dev.simplecore.simplix.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages back-pressure based on connection count limits and load awareness.
 *
 * <p>Provides two safety mechanisms:
 * <ul>
 *   <li><b>Connection limit</b>: Rejects new connections when the
 *       maximum is reached, preventing resource exhaustion.</li>
 *   <li><b>Load signal</b>: Signals when operations should be throttled
 *       based on current connection load (above 80% of max).</li>
 * </ul>
 */
public class BackpressureGuard {

    private static final Logger log = LoggerFactory.getLogger(BackpressureGuard.class);

    private final int maxConnections;
    private final int maxEventsPerSecond;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public BackpressureGuard(int maxConnections, int maxEventsPerSecond) {
        this.maxConnections = maxConnections;
        this.maxEventsPerSecond = maxEventsPerSecond;
    }

    /**
     * Try to register a new connection.
     *
     * @return true if the connection is allowed, false if the limit is reached
     */
    public boolean tryRegister() {
        int current = activeConnections.get();
        while (current < maxConnections) {
            if (activeConnections.compareAndSet(current, current + 1)) {
                log.debug("Connection registered: active={}/{}", current + 1, maxConnections);
                return true;
            }
            current = activeConnections.get();
        }
        log.warn("Connection rejected: max connections reached ({}/{})", current, maxConnections);
        return false;
    }

    /**
     * Unregister a connection.
     */
    public void unregister() {
        int current = activeConnections.updateAndGet(c -> Math.max(0, c - 1));
        log.debug("Connection unregistered: active={}/{}", current, maxConnections);
    }

    /**
     * Check whether operations should proceed given the current load.
     * When connection count is high (above 80% of max), signals that
     * non-critical operations can be dropped.
     *
     * @return true if operations should proceed normally, false if throttling is advised
     */
    public boolean shouldProceed() {
        int current = activeConnections.get();
        return current * 5 < maxConnections * 4;
    }

    /**
     * Get the current number of active connections.
     *
     * @return active connection count
     */
    public int getActiveConnections() {
        return activeConnections.get();
    }

    /**
     * Get the maximum allowed connections.
     *
     * @return max connection limit
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Get the configured maximum events per second.
     *
     * @return max events per second
     */
    public int getMaxEventsPerSecond() {
        return maxEventsPerSecond;
    }
}
