package dev.simplecore.simplix.hibernate.cache.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spring ApplicationEvent published when a transaction commits and pending cache evictions
 * need to be executed.
 *
 * <p>This event is consumed by {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * to ensure cache eviction only happens after the transaction successfully commits.</p>
 *
 * <h3>Why use Spring Events?</h3>
 * <ul>
 *   <li>Decouples transaction handling from cache eviction logic</li>
 *   <li>Enables @TransactionalEventListener for guaranteed post-commit execution</li>
 *   <li>Provides a clear integration point for monitoring and testing</li>
 * </ul>
 *
 * @see PendingEviction
 * @see dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler
 */
@Getter
public class PendingEvictionCompletedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * List of pending evictions to be executed after commit.
     */
    private final List<PendingEviction> pendingEvictions;

    /**
     * Creates a new event with the specified pending evictions.
     *
     * <p>A defensive copy is made to ensure true immutability - modifications
     * to the original list after event creation won't affect this event.</p>
     *
     * @param source the object on which the event initially occurred
     * @param pendingEvictions list of evictions to execute
     */
    public PendingEvictionCompletedEvent(Object source, List<PendingEviction> pendingEvictions) {
        super(source);
        // Defensive copy + unmodifiable wrapper for true immutability
        this.pendingEvictions = Collections.unmodifiableList(
                pendingEvictions != null ? new ArrayList<>(pendingEvictions) : List.of());
    }

    /**
     * Returns the number of pending evictions in this event.
     *
     * @return the count of pending evictions
     */
    public int getEvictionCount() {
        return pendingEvictions.size();
    }
}
