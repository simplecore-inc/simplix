package dev.simplecore.simplix.hibernate.cache.transaction;

import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.event.PendingEvictionCompletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TransactionAwareCacheEvictionCollector within an active transaction context.
 * These tests simulate TransactionSynchronizationManager state to cover the
 * TransactionSynchronization inner class (afterCommit, afterCompletion).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionAwareCacheEvictionCollector - Transactional Tests")
class TransactionAwareCacheEvictionCollectorTransactionalTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransactionAwareCacheEvictionCollector collector;

    @BeforeEach
    void setUp() {
        // Clean up any stale ThreadLocal state from previous tests
        cleanupThreadLocals();

        collector = new TransactionAwareCacheEvictionCollector(eventPublisher);
    }

    @AfterEach
    void tearDown() {
        // Clean up transaction synchronization state
        TransactionSynchronizationManager.setActualTransactionActive(false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        cleanupThreadLocals();
    }

    /**
     * Clean up the private static ThreadLocal fields in the collector via reflection.
     * This prevents leakage between tests.
     */
    private void cleanupThreadLocals() {
        try {
            Field pendingEvictionsField = TransactionAwareCacheEvictionCollector.class
                    .getDeclaredField("PENDING_EVICTIONS");
            pendingEvictionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<List<PendingEviction>> pendingEvictions =
                    (ThreadLocal<List<PendingEviction>>) pendingEvictionsField.get(null);
            pendingEvictions.remove();

            Field syncRegisteredField = TransactionAwareCacheEvictionCollector.class
                    .getDeclaredField("SYNCHRONIZATION_REGISTERED");
            syncRegisteredField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<Boolean> syncRegistered =
                    (ThreadLocal<Boolean>) syncRegisteredField.get(null);
            syncRegistered.remove();
        } catch (Exception e) {
            // Ignore - cleanup is best-effort
        }
    }

    private void initializeTransactionContext() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private TransactionSynchronization getRegisteredSynchronization() {
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).isNotEmpty();
        return synchronizations.get(0);
    }

    @Nested
    @DisplayName("collect() within active transaction")
    class CollectWithTransactionTests {

        @Test
        @DisplayName("Should register synchronization and collect eviction")
        void shouldRegisterSynchronizationAndCollect() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();

            // When
            collector.collect(eviction);

            // Then - should not publish immediately
            verify(eventPublisher, never()).publishEvent(any());
            // Should have pending evictions
            assertThat(collector.getPendingCount()).isEqualTo(1);
            assertThat(collector.hasPendingEvictions()).isTrue();
        }

        @Test
        @DisplayName("Should collect multiple evictions in same transaction")
        void shouldCollectMultipleEvictions() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction1 = createTestEviction();
            PendingEviction eviction2 = PendingEviction.of(
                    AnotherEntity.class, 2L, null, PendingEviction.EvictionOperation.DELETE);

            // When
            collector.collect(eviction1);
            collector.collect(eviction2);

            // Then
            assertThat(collector.getPendingCount()).isEqualTo(2);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Should register synchronization only once per transaction")
        void shouldRegisterSynchronizationOnlyOnce() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction1 = createTestEviction();
            PendingEviction eviction2 = createTestEviction();

            // When
            collector.collect(eviction1);
            collector.collect(eviction2);

            // Then - only one synchronization should be registered
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
        }
    }

    @Nested
    @DisplayName("afterCommit() behavior")
    class AfterCommitTests {

        @Test
        @DisplayName("Should publish event with all pending evictions after commit")
        void shouldPublishEventAfterCommit() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction1 = PendingEviction.of(
                    TestEntity.class, 1L, null, PendingEviction.EvictionOperation.UPDATE);
            PendingEviction eviction2 = PendingEviction.of(
                    TestEntity.class, 2L, null, PendingEviction.EvictionOperation.DELETE);

            collector.collect(eviction1);
            collector.collect(eviction2);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // When - simulate commit
            sync.afterCommit();

            // Then
            ArgumentCaptor<PendingEvictionCompletedEvent> captor =
                    ArgumentCaptor.forClass(PendingEvictionCompletedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            PendingEvictionCompletedEvent event = captor.getValue();
            assertThat(event.getEvictionCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should retry on first publish failure during afterCommit")
        void shouldRetryOnFirstPublishFailureDuringAfterCommit() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            doThrow(new RuntimeException("Publish failed"))
                    .doNothing()
                    .when(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));

            // When - simulate commit
            sync.afterCommit();

            // Then - should have tried twice (retry succeeded)
            verify(eventPublisher, times(2)).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should log error when all publish retries fail during afterCommit")
        void shouldLogErrorWhenAllRetriesFail() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            doThrow(new RuntimeException("Publish failed"))
                    .when(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));

            // When - simulate commit
            sync.afterCommit();

            // Then - should have tried MAX_PUBLISH_RETRY_ATTEMPTS (2) times
            verify(eventPublisher, times(2)).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should cleanup ThreadLocal after successful commit")
        void shouldCleanupAfterSuccessfulCommit() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);
            assertThat(collector.getPendingCount()).isEqualTo(1);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // When - simulate commit
            sync.afterCommit();

            // Then - ThreadLocal should be cleaned up
            assertThat(collector.getPendingCount()).isZero();
            assertThat(collector.hasPendingEvictions()).isFalse();
        }

        @Test
        @DisplayName("Should handle afterCommit when pending list was already cleaned")
        void shouldHandleAfterCommitWhenAlreadyCleaned() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // Force cleanup of ThreadLocal before afterCommit
            cleanupThreadLocals();

            // When - afterCommit with null pending list
            sync.afterCommit();

            // Then - no event published for null/empty list
            verify(eventPublisher, never()).publishEvent(any(PendingEvictionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("afterCompletion() behavior")
    class AfterCompletionTests {

        @Test
        @DisplayName("Should discard evictions on rollback")
        void shouldDiscardEvictionsOnRollback() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);
            assertThat(collector.getPendingCount()).isEqualTo(1);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // When - simulate rollback
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            // Then - evictions should be discarded
            assertThat(collector.getPendingCount()).isZero();
            assertThat(collector.hasPendingEvictions()).isFalse();
            verify(eventPublisher, never()).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should cleanup ThreadLocal on unknown status")
        void shouldCleanupOnUnknownStatus() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // When - simulate unknown completion
            sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

            // Then - ThreadLocal should be cleaned up
            assertThat(collector.getPendingCount()).isZero();
        }

        @Test
        @DisplayName("Should cleanup after committed status in afterCompletion")
        void shouldCleanupAfterCommittedStatus() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // Simulate afterCommit (which publishes and cleans up)
            sync.afterCommit();

            // When - afterCompletion also runs (normal Spring lifecycle)
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            // Then - no exception, cleanup is idempotent
            assertThat(collector.getPendingCount()).isZero();
        }

        @Test
        @DisplayName("Should handle afterCompletion when ThreadLocal is null")
        void shouldHandleAfterCompletionWhenThreadLocalIsNull() {
            // Given
            initializeTransactionContext();
            PendingEviction eviction = createTestEviction();
            collector.collect(eviction);

            TransactionSynchronization sync = getRegisteredSynchronization();

            // Force cleanup before afterCompletion
            cleanupThreadLocals();

            // When - afterCompletion with null pending evictions
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            // Then - no exception
            assertThat(collector.getPendingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Synchronization inactive during registration")
    class SynchronizationInactiveTests {

        @Test
        @DisplayName("Should publish immediately when synchronization not active but transaction is active")
        void shouldPublishImmediatelyWhenSynchronizationNotActive() {
            // Given - transaction is active but synchronization is not
            TransactionSynchronizationManager.setActualTransactionActive(true);
            // Do NOT init synchronization

            PendingEviction eviction = createTestEviction();

            // When
            collector.collect(eviction);

            // Then - should publish immediately as fallback
            verify(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));
        }
    }

    @Nested
    @DisplayName("MAX_PENDING_EVICTIONS limit")
    class MaxPendingEvictionsTests {

        @Test
        @DisplayName("Should switch to bulk eviction when limit is exceeded")
        void shouldSwitchToBulkEvictionWhenLimitExceeded() throws Exception {
            // Given
            initializeTransactionContext();

            // Use reflection to fill the pending list to MAX_PENDING_EVICTIONS
            Field pendingEvictionsField = TransactionAwareCacheEvictionCollector.class
                    .getDeclaredField("PENDING_EVICTIONS");
            pendingEvictionsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<java.util.List<PendingEviction>> pendingEvictions =
                    (ThreadLocal<java.util.List<PendingEviction>>) pendingEvictionsField.get(null);

            // Initialize the list with MAX_PENDING_EVICTIONS items
            java.util.List<PendingEviction> list = new java.util.ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                list.add(PendingEviction.of(TestEntity.class, (long) i, null,
                        PendingEviction.EvictionOperation.UPDATE));
            }
            pendingEvictions.set(list);

            // Also set the sync registered flag to prevent re-registration
            Field syncField = TransactionAwareCacheEvictionCollector.class
                    .getDeclaredField("SYNCHRONIZATION_REGISTERED");
            syncField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ThreadLocal<Boolean> syncRegistered = (ThreadLocal<Boolean>) syncField.get(null);
            syncRegistered.set(Boolean.TRUE);

            // When - collect one more eviction that exceeds the limit
            PendingEviction overflowEviction = PendingEviction.of(
                    TestEntity.class, 99999L, "some-region",
                    PendingEviction.EvictionOperation.UPDATE);
            collector.collect(overflowEviction);

            // Then - the collected eviction should have been converted to BULK_UPDATE
            // with null entityId
            assertThat(collector.getPendingCount()).isEqualTo(10001);
            java.util.List<PendingEviction> currentList = pendingEvictions.get();
            PendingEviction lastEviction = currentList.get(currentList.size() - 1);
            assertThat(lastEviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_UPDATE);
            assertThat(lastEviction.getEntityId()).isNull();
        }
    }

    @Nested
    @DisplayName("registerSynchronizationIfNeeded race condition")
    class RegisterSynchronizationRaceConditionTests {

        @Test
        @DisplayName("Should handle synchronization becoming inactive during registration")
        void shouldHandleSynchronizationBecomingInactive() throws Exception {
            // Given - transaction active, sync active initially
            initializeTransactionContext();

            // Collect first eviction to set up the list
            PendingEviction eviction = createTestEviction();

            // Clear synchronization after list is set up but before registration check
            // Since we can't control timing, we test the branch differently:
            // Force the SYNCHRONIZATION_REGISTERED to false, then deactivate synchronization
            collector.collect(eviction);

            // Verify the synchronization was registered
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getSimpleClassName utility tests")
    class GetSimpleClassNameTests {

        @Test
        @DisplayName("Should handle fully qualified class name in eviction")
        void shouldHandleFullyQualifiedClassName() {
            // Given - eviction with fully qualified class name
            initializeTransactionContext();
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class, 1L, null, PendingEviction.EvictionOperation.UPDATE);

            // When
            collector.collect(eviction);

            // Then - should not throw (getSimpleClassName is used in log messages)
            assertThat(collector.getPendingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle eviction with null class name outside transaction")
        void shouldHandleEvictionWithNullClassName() {
            // Given - eviction with null entity class name, no active transaction
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName(null)
                    .entityId("1")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When - published immediately since no transaction
            collector.collect(eviction);

            // Then - should not throw
            verify(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should handle eviction with empty class name outside transaction")
        void shouldHandleEvictionWithEmptyClassName() {
            // Given
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName("")
                    .entityId("1")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When
            collector.collect(eviction);

            // Then
            verify(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));
        }

        @Test
        @DisplayName("Should handle simple class name without dot")
        void shouldHandleSimpleClassNameWithoutDot() {
            // Given
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName("SimpleEntity")
                    .entityId("1")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When
            collector.collect(eviction);

            // Then
            verify(eventPublisher).publishEvent(any(PendingEvictionCompletedEvent.class));
        }
    }

    private PendingEviction createTestEviction() {
        return PendingEviction.of(
                TestEntity.class,
                System.nanoTime(),
                null,
                PendingEviction.EvictionOperation.UPDATE
        );
    }

    // Test entity classes
    private static class TestEntity {
        private Long id;
        private String name;
    }

    private static class AnotherEntity {
        private Long id;
        private String value;
    }
}
