package dev.simplecore.simplix.core.event;

import java.util.function.Supplier;

/**
 * Thread-local context for controlling entity event publishing behavior.
 * <p>
 * Provides event suppression for bulk operations, full downloads, and other scenarios
 * where automatic event publishing should be temporarily disabled.
 *
 * <p>
 * Usage:
 * <pre>{@code
 * // Suppress events during bulk import
 * EventContext.suppressEvents(() -> {
 *     bulkImportService.importCardholders(list);
 * });
 *
 * // Suppress events with return value
 * int count = EventContext.suppressEvents(() -> {
 *     return bulkImportService.importAndCount(list);
 * });
 * }</pre>
 */
public final class EventContext {

    private static final ThreadLocal<Boolean> EVENTS_SUPPRESSED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private EventContext() {
        // Utility class
    }

    /**
     * Check if events are currently suppressed on this thread.
     *
     * @return true if events should not be published
     */
    public static boolean isEventsSuppressed() {
        return EVENTS_SUPPRESSED.get();
    }

    /**
     * Execute a runnable with event publishing suppressed.
     * Events are re-enabled after the runnable completes (even if it throws).
     *
     * @param runnable the code to execute without event publishing
     */
    public static void suppressEvents(Runnable runnable) {
        Boolean previous = EVENTS_SUPPRESSED.get();
        EVENTS_SUPPRESSED.set(Boolean.TRUE);
        try {
            runnable.run();
        } finally {
            EVENTS_SUPPRESSED.set(previous);
        }
    }

    /**
     * Execute a supplier with event publishing suppressed and return its result.
     * Events are re-enabled after the supplier completes (even if it throws).
     *
     * @param supplier the code to execute without event publishing
     * @param <T>      the return type
     * @return the result of the supplier
     */
    public static <T> T suppressEvents(Supplier<T> supplier) {
        Boolean previous = EVENTS_SUPPRESSED.get();
        EVENTS_SUPPRESSED.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            EVENTS_SUPPRESSED.set(previous);
        }
    }

    /**
     * Clear the thread-local state. Should be called at request boundaries
     * to prevent memory leaks in thread pools.
     */
    public static void clear() {
        EVENTS_SUPPRESSED.remove();
    }
}
