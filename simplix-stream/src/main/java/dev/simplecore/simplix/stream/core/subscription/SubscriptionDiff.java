package dev.simplecore.simplix.stream.core.subscription;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents the difference between current and requested subscriptions.
 */
public record SubscriptionDiff(
        Set<SubscriptionKey> added,
        Set<SubscriptionKey> removed,
        Set<SubscriptionKey> unchanged
) {

    /**
     * Calculate the diff between current and requested subscriptions.
     *
     * @param current   the current subscriptions
     * @param requested the requested subscriptions
     * @return the diff
     */
    public static SubscriptionDiff calculate(Set<SubscriptionKey> current, Set<SubscriptionKey> requested) {
        Set<SubscriptionKey> added = new HashSet<>(requested);
        added.removeAll(current);

        Set<SubscriptionKey> removed = new HashSet<>(current);
        removed.removeAll(requested);

        Set<SubscriptionKey> unchanged = new HashSet<>(current);
        unchanged.retainAll(requested);

        return new SubscriptionDiff(added, removed, unchanged);
    }

    /**
     * Check if there are any changes.
     *
     * @return true if there are additions or removals
     */
    public boolean hasChanges() {
        return !added.isEmpty() || !removed.isEmpty();
    }

    /**
     * Get the total number of changes.
     *
     * @return the count of additions plus removals
     */
    public int changeCount() {
        return added.size() + removed.size();
    }
}
