package dev.simplecore.simplix.core.resilience;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin batch selector that cycles through a list of items.
 *
 * <p>Maintains an internal cursor that advances after each batch selection,
 * wrapping around when reaching the end. Thread-safe via {@link AtomicInteger}.
 *
 * <p>Handles dynamic list sizes gracefully — if the list shrinks between calls,
 * the cursor is normalized to prevent index-out-of-bounds errors.
 */
public class RoundRobinSelector {

    private final AtomicInteger cursor = new AtomicInteger(0);

    /**
     * Select the next batch of items using round-robin ordering.
     *
     * @param items     the full list of items to select from
     * @param batchSize the maximum number of items to select
     * @param <R>       the item type
     * @return a list of selected items (may be smaller than batchSize if items is smaller)
     */
    public <R> List<R> selectBatch(List<R> items, int batchSize) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        int total = items.size();
        int effectiveBatch = Math.min(batchSize, total);
        int start = cursor.getAndUpdate(c -> ((c % total) + effectiveBatch) % total) % total;

        List<R> batch = new ArrayList<>(effectiveBatch);
        for (int i = 0; i < effectiveBatch; i++) {
            int index = (start + i) % total;
            batch.add(items.get(index));
        }

        return batch;
    }

    /**
     * Get the current cursor position (for diagnostics).
     *
     * @return the current cursor index
     */
    public int getCursorPosition() {
        return cursor.get();
    }

    /**
     * Reset the cursor to position 0.
     */
    public void resetCursor() {
        cursor.set(0);
    }
}
