package dev.simplecore.simplix.sync.core;

import dev.simplecore.simplix.core.resilience.CircuitBreaker;
import dev.simplecore.simplix.core.resilience.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationSchedulerTest {

    @Nested
    @DisplayName("Basic reconciliation")
    class BasicReconciliation {

        @Test
        @DisplayName("should process batch of resources")
        void shouldProcessBatch() {
            List<String> resources = List.of("A", "B", "C", "D", "E");
            List<String> processed = new ArrayList<>();

            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(() -> resources)
                    .keyExtractor(s -> s)
                    .batchSize(3)
                    .build();

            scheduler.reconcile(processed::add);
            assertThat(processed).hasSize(3);
            assertThat(processed).containsExactly("A", "B", "C");
        }

        @Test
        @DisplayName("should advance cursor across cycles")
        void shouldAdvanceCursor() {
            List<String> resources = List.of("A", "B", "C", "D", "E");
            List<String> processed = new ArrayList<>();

            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(() -> resources)
                    .keyExtractor(s -> s)
                    .batchSize(2)
                    .build();

            scheduler.reconcile(processed::add);
            assertThat(processed).containsExactly("A", "B");

            processed.clear();
            scheduler.reconcile(processed::add);
            assertThat(processed).containsExactly("C", "D");
        }

        @Test
        @DisplayName("should skip when no resources")
        void shouldSkipWhenEmpty() {
            List<String> processed = new ArrayList<>();
            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(List::of)
                    .keyExtractor(s -> s)
                    .build();

            scheduler.reconcile(processed::add);
            assertThat(processed).isEmpty();
        }
    }

    @Nested
    @DisplayName("Safety integration")
    class SafetyIntegration {

        @Test
        @DisplayName("should skip resources with open circuit breaker")
        void shouldSkipOpenCircuit() {
            CircuitBreaker cb = new CircuitBreaker(1, 60_000);
            cb.recordFailure("B");

            List<String> resources = List.of("A", "B", "C");
            List<String> processed = new ArrayList<>();

            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(() -> resources)
                    .keyExtractor(s -> s)
                    .batchSize(3)
                    .circuitBreaker(cb)
                    .build();

            scheduler.reconcile(processed::add);
            assertThat(processed).containsExactly("A", "C");
        }

        @Test
        @DisplayName("should stop when rate limited")
        void shouldStopWhenRateLimited() {
            RateLimiter rl = new RateLimiter(2);

            List<String> resources = List.of("A", "B", "C", "D", "E");
            List<String> processed = new ArrayList<>();

            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(() -> resources)
                    .keyExtractor(s -> s)
                    .batchSize(5)
                    .rateLimiter(rl)
                    .build();

            scheduler.reconcile(processed::add);
            assertThat(processed).hasSize(2);
        }

        @Test
        @DisplayName("should record failure to circuit breaker on exception")
        void shouldRecordFailureOnException() {
            CircuitBreaker cb = new CircuitBreaker(1, 60_000);
            List<String> resources = List.of("A");

            ReconciliationScheduler<String> scheduler = ReconciliationScheduler.<String>builder()
                    .resourceProvider(() -> resources)
                    .keyExtractor(s -> s)
                    .batchSize(1)
                    .circuitBreaker(cb)
                    .build();

            scheduler.reconcile(r -> { throw new RuntimeException("fail"); });
            assertThat(cb.getStatus("A")).isEqualTo(CircuitBreaker.Status.OPEN);
        }
    }

    @Nested
    @DisplayName("Builder validation")
    class BuilderValidation {

        @Test
        @DisplayName("should throw when resourceProvider is missing")
        void shouldThrowWithoutProvider() {
            assertThatThrownBy(() ->
                    ReconciliationScheduler.<String>builder()
                            .keyExtractor(s -> s)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("resourceProvider");
        }

        @Test
        @DisplayName("should throw when keyExtractor is missing")
        void shouldThrowWithoutKeyExtractor() {
            assertThatThrownBy(() ->
                    ReconciliationScheduler.<String>builder()
                            .resourceProvider(List::of)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("keyExtractor");
        }
    }
}
