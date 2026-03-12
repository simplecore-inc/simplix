package dev.simplecore.simplix.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks pending asynchronous requests and correlates them with responses.
 *
 * <p>When a request is issued, a {@link CompletableFuture} is registered under a unique ID.
 * When the corresponding response arrives, {@link #complete(String)} resolves the future.
 *
 * <p>Futures automatically time out and are removed from the tracking map after the
 * configured timeout period.
 */
public class AsyncQueryTracker {

    private static final Logger log = LoggerFactory.getLogger(AsyncQueryTracker.class);

    private final ConcurrentMap<String, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;

    /**
     * Create a new tracker with the specified timeout.
     *
     * @param timeoutMs timeout in milliseconds for pending queries
     */
    public AsyncQueryTracker(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Register a query for tracking and return a future that completes when
     * the corresponding response is received.
     *
     * @param queryId the unique query identifier
     * @return a future that completes when the response arrives or times out
     */
    public CompletableFuture<Void> track(String queryId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        pending.put(queryId, future);

        future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .whenComplete((result, ex) -> {
                    pending.remove(queryId);
                    if (ex != null) {
                        log.warn("Query timed out after {}ms [queryId={}]", timeoutMs, queryId);
                    }
                });

        log.debug("Tracking query [queryId={}, timeout={}ms]", queryId, timeoutMs);
        return future;
    }

    /**
     * Complete a pending query. Called when a response with a matching query ID arrives.
     *
     * <p>If the query ID is not found (already completed or timed out), this is a no-op.
     *
     * @param queryId the query identifier from the response
     */
    public void complete(String queryId) {
        if (queryId == null || queryId.isEmpty()) {
            return;
        }

        CompletableFuture<Void> future = pending.remove(queryId);
        if (future != null) {
            future.complete(null);
            log.debug("Query completed [queryId={}]", queryId);
        }
    }

    /**
     * Get the number of currently pending (unresolved) queries.
     *
     * @return the count of pending queries
     */
    public int getPendingCount() {
        return pending.size();
    }
}
