package dev.simplecore.simplix.messaging.broker.common;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks polling health for a single subscription. Counts consecutive errors
 * and applies exponential backoff (500ms to 30s) during error storms to
 * prevent tight polling loops that flood logs and waste CPU.
 *
 * <p>After {@code UNHEALTHY_THRESHOLD} consecutive errors, {@link #isHealthy()}
 * returns false. The owning broker is expected to detect this and trigger
 * a resubscribe.
 */
@Slf4j
public class SubscriptionHealthTracker {

    public static final int UNHEALTHY_THRESHOLD = 5;
    private static final long INITIAL_BACKOFF_MS = 500;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final String streamKey;
    private final String encoding;
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

    public SubscriptionHealthTracker(String streamKey, String encoding) {
        this.streamKey = streamKey;
        this.encoding = encoding;
    }

    public void onError(Throwable t) {
        int errorCount = consecutiveErrors.incrementAndGet();
        long backoffMs = Math.min(
                INITIAL_BACKOFF_MS * (1L << Math.min(errorCount - 1, 16)),
                MAX_BACKOFF_MS);
        log.warn("Stream polling error [stream={}, encoding={}, consecutiveErrors={}, backoffMs={}]: {}",
                streamKey, encoding, errorCount, backoffMs, t.getMessage());
        try { Thread.sleep(backoffMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void recordSuccess() {
        if (consecutiveErrors.get() > 0) {
            int prev = consecutiveErrors.getAndSet(0);
            log.info("Stream polling recovered [stream={}, encoding={}, previousErrors={}]",
                    streamKey, encoding, prev);
        }
    }

    public boolean isHealthy() {
        return consecutiveErrors.get() < UNHEALTHY_THRESHOLD;
    }
}
