package dev.simplecore.simplix.sync.core;

import dev.simplecore.simplix.core.resilience.CircuitBreaker;
import dev.simplecore.simplix.core.resilience.RateLimiter;
import dev.simplecore.simplix.core.resilience.RoundRobinSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Round-robin batch reconciliation scheduler.
 *
 * <p>Cycles through a dynamic list of resources, processing a configurable
 * batch per cycle. Integrates optional {@link CircuitBreaker} and
 * {@link RateLimiter} for safe resource access.
 *
 * <p>This class does NOT own the {@code @Scheduled} annotation.
 * The consumer calls {@link #reconcile(Consumer)} from their own scheduled method,
 * allowing flexible cron/fixedDelay configuration and distributed lock integration.
 *
 * <p>Typical usage:
 * <pre>{@code
 * ReconciliationScheduler<DeviceState> scheduler =
 *     ReconciliationScheduler.<DeviceState>builder()
 *         .resourceProvider(stateStore::getAll)
 *         .keyExtractor(DeviceState::getDeviceId)
 *         .batchSize(10)
 *         .circuitBreaker(circuitBreaker)
 *         .rateLimiter(rateLimiter)
 *         .build();
 *
 * @Scheduled(fixedDelay = 30000)
 * public void reconcile() {
 *     scheduler.reconcile(device -> queryDevice(device));
 * }
 * }</pre>
 *
 * @param <R> the resource type
 */
public class ReconciliationScheduler<R> {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final Supplier<Collection<R>> resourceProvider;
    private final Function<R, String> keyExtractor;
    private final int batchSize;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final RoundRobinSelector selector = new RoundRobinSelector();

    private ReconciliationScheduler(Builder<R> builder) {
        this.resourceProvider = builder.resourceProvider;
        this.keyExtractor = builder.keyExtractor;
        this.batchSize = builder.batchSize;
        this.circuitBreaker = builder.circuitBreaker;
        this.rateLimiter = builder.rateLimiter;
    }

    /**
     * Execute one reconciliation cycle.
     *
     * <p>Selects the next batch via round-robin, then processes each resource
     * that passes circuit breaker and rate limiter checks.
     *
     * @param processor per-resource processing logic
     */
    public void reconcile(Consumer<R> processor) {
        List<R> allResources = new ArrayList<>(resourceProvider.get());
        if (allResources.isEmpty()) {
            log.trace("No resources available, skipping reconciliation cycle");
            return;
        }

        List<R> batch = selector.selectBatch(allResources, batchSize);
        log.debug("Reconciliation cycle: processing {} of {} resources",
                batch.size(), allResources.size());

        for (R resource : batch) {
            String key = keyExtractor.apply(resource);

            if (circuitBreaker != null && !circuitBreaker.allowRequest(key)) {
                log.debug("Circuit breaker open for key={}, skipping", key);
                continue;
            }

            if (rateLimiter != null && !rateLimiter.tryAcquire()) {
                log.debug("Rate limited, stopping reconciliation cycle");
                break;
            }

            try {
                processor.accept(resource);
                if (circuitBreaker != null) {
                    circuitBreaker.recordSuccess(key);
                }
            } catch (Exception e) {
                log.warn("Reconciliation failed for key={}: {}", key, e.getMessage());
                if (circuitBreaker != null) {
                    circuitBreaker.recordFailure(key);
                }
            }
        }
    }

    /**
     * Get the current round-robin cursor position (for diagnostics).
     *
     * @return the current cursor index
     */
    public int getCursorPosition() {
        return selector.getCursorPosition();
    }

    /**
     * Create a new builder.
     *
     * @param <R> the resource type
     * @return a new builder instance
     */
    public static <R> Builder<R> builder() {
        return new Builder<>();
    }

    /**
     * Builder for {@link ReconciliationScheduler}.
     *
     * @param <R> the resource type
     */
    public static class Builder<R> {
        private Supplier<Collection<R>> resourceProvider;
        private Function<R, String> keyExtractor;
        private int batchSize = 5;
        private CircuitBreaker circuitBreaker;
        private RateLimiter rateLimiter;

        /**
         * Set the supplier that provides the current list of resources.
         *
         * @param provider resource provider
         * @return this builder
         */
        public Builder<R> resourceProvider(Supplier<Collection<R>> provider) {
            this.resourceProvider = provider;
            return this;
        }

        /**
         * Set the function that extracts a unique key from a resource.
         * Used for circuit breaker tracking.
         *
         * @param extractor key extractor function
         * @return this builder
         */
        public Builder<R> keyExtractor(Function<R, String> extractor) {
            this.keyExtractor = extractor;
            return this;
        }

        /**
         * Set the maximum batch size per reconciliation cycle.
         *
         * @param batchSize batch size (default: 5)
         * @return this builder
         */
        public Builder<R> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Set an optional circuit breaker for per-resource failure tracking.
         *
         * @param circuitBreaker the circuit breaker (nullable)
         * @return this builder
         */
        public Builder<R> circuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
            return this;
        }

        /**
         * Set an optional rate limiter for throttling.
         *
         * @param rateLimiter the rate limiter (nullable)
         * @return this builder
         */
        public Builder<R> rateLimiter(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
            return this;
        }

        /**
         * Build the reconciliation scheduler.
         *
         * @return a new ReconciliationScheduler
         * @throws IllegalArgumentException if required fields are missing
         */
        public ReconciliationScheduler<R> build() {
            if (resourceProvider == null) {
                throw new IllegalArgumentException("resourceProvider is required");
            }
            if (keyExtractor == null) {
                throw new IllegalArgumentException("keyExtractor is required");
            }
            return new ReconciliationScheduler<>(this);
        }
    }
}
