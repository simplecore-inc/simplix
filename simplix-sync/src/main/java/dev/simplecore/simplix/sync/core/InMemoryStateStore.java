package dev.simplecore.simplix.sync.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Thread-safe in-memory state store keyed by String identifier.
 *
 * <p>Provides synchronized mutation via {@link #mutate} and {@link #compute}
 * to prevent concurrent threads from partially applying state changes.
 *
 * <p>Typical usage:
 * <pre>{@code
 * InMemoryStateStore<DeviceState> store = new InMemoryStateStore<>(
 *     id -> new DeviceState(id));
 *
 * // Synchronized state mutation
 * store.mutate("device-1", state -> state.applyReading(reading));
 *
 * // Synchronized computation with result
 * boolean applied = store.compute("device-1", state -> {
 *     if (state.isFresh(reading)) {
 *         state.apply(reading);
 *         return true;
 *     }
 *     return false;
 * });
 * }</pre>
 *
 * @param <S> the state snapshot type
 */
public class InMemoryStateStore<S> {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStateStore.class);

    private final ConcurrentMap<String, S> store = new ConcurrentHashMap<>();
    private final Function<String, S> factory;

    /**
     * Create a new state store with the specified factory for lazy initialization.
     *
     * @param factory function that creates a new state for a given key
     */
    public InMemoryStateStore(Function<String, S> factory) {
        this.factory = factory;
    }

    /**
     * Get an existing state or lazily create a new one for the given key.
     *
     * @param key the state identifier
     * @return the state (never null)
     */
    public S getOrCreate(String key) {
        return store.computeIfAbsent(key, factory);
    }

    /**
     * Get an existing state, or null if none exists.
     *
     * @param key the state identifier
     * @return the state, or null
     */
    public S get(String key) {
        return store.get(key);
    }

    /**
     * Return an unmodifiable view of all current states.
     *
     * @return unmodifiable collection of all states
     */
    public Collection<S> getAll() {
        return Collections.unmodifiableCollection(store.values());
    }

    /**
     * Return all keys currently in the store.
     *
     * @return unmodifiable set of keys
     */
    public Set<String> getAllKeys() {
        return Collections.unmodifiableSet(store.keySet());
    }

    /**
     * Remove a state by key.
     *
     * @param key the state identifier to remove
     * @return the removed state, or null if not found
     */
    public S remove(String key) {
        S removed = store.remove(key);
        if (removed != null) {
            log.trace("Removed state for key={}", key);
        }
        return removed;
    }

    /**
     * Get the number of entries in the store.
     *
     * @return the store size
     */
    public int size() {
        return store.size();
    }

    /**
     * Synchronized mutation on the state object for the given key.
     *
     * <p>Guarantees that concurrent calls for the same key are serialized.
     * The state is lazily created if it does not exist.
     *
     * @param key     the state identifier
     * @param mutator the mutation to apply
     */
    public void mutate(String key, Consumer<S> mutator) {
        S state = getOrCreate(key);
        synchronized (state) {
            mutator.accept(state);
        }
    }

    /**
     * Synchronized computation on the state object for the given key.
     * Returns the computed result.
     *
     * <p>Guarantees that concurrent calls for the same key are serialized.
     * The state is lazily created if it does not exist.
     *
     * @param key      the state identifier
     * @param computer the computation to apply
     * @param <R>      the result type
     * @return the computed result
     */
    public <R> R compute(String key, Function<S, R> computer) {
        S state = getOrCreate(key);
        synchronized (state) {
            return computer.apply(state);
        }
    }
}
