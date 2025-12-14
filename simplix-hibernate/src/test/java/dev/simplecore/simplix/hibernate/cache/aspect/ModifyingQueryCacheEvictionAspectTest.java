package dev.simplecore.simplix.hibernate.cache.aspect;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
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
import org.springframework.data.jpa.repository.Query;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ModifyingQueryCacheEvictionAspect.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ModifyingQueryCacheEvictionAspect Tests")
class ModifyingQueryCacheEvictionAspectTest {

    @Mock
    private TransactionAwareCacheEvictionCollector evictionCollector;

    @Mock
    private EntityCacheScanner entityCacheScanner;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private ModifyingQueryCacheEvictionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ModifyingQueryCacheEvictionAspect(evictionCollector, entityCacheScanner);

        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.toShortString()).thenReturn("Repository.updateEntity()");
    }

    @Nested
    @DisplayName("handleModifyingQuery() tests - UPDATE queries")
    class HandleModifyingQueryUpdateTests {

        @Test
        @DisplayName("Should extract entity from UPDATE JPQL and collect eviction")
        void shouldExtractEntityFromUpdateJpql() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("updateUser");
            when(methodSignature.getMethod()).thenReturn(method);
            doReturn(User.class).when(entityCacheScanner).findBySimpleName("User");
            when(entityCacheScanner.isCached(User.class)).thenReturn(true);
            when(joinPoint.proceed()).thenReturn(5);

            // When
            Object result = aspect.handleModifyingQuery(joinPoint);

            // Then
            assertThat(result).isEqualTo(5);
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());

            PendingEviction eviction = captor.getValue();
            assertThat(eviction.getEntityClassName()).isEqualTo(User.class.getName());
            assertThat(eviction.getEntityId()).isNull(); // Bulk operation
            assertThat(eviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_UPDATE);
        }

        @Test
        @DisplayName("Should not collect when entity is not cached")
        void shouldNotCollectWhenEntityNotCached() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("updateUser");
            when(methodSignature.getMethod()).thenReturn(method);
            doReturn(User.class).when(entityCacheScanner).findBySimpleName("User");
            when(entityCacheScanner.isCached(User.class)).thenReturn(false);
            when(joinPoint.proceed()).thenReturn(5);

            // When
            Object result = aspect.handleModifyingQuery(joinPoint);

            // Then
            assertThat(result).isEqualTo(5);
            verify(evictionCollector, never()).collect(any());
        }
    }

    @Nested
    @DisplayName("handleModifyingQuery() tests - DELETE queries")
    class HandleModifyingQueryDeleteTests {

        @Test
        @DisplayName("Should extract entity from DELETE JPQL and collect eviction")
        void shouldExtractEntityFromDeleteJpql() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("deleteUser");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.deleteUser()");
            doReturn(User.class).when(entityCacheScanner).findBySimpleName("User");
            when(entityCacheScanner.isCached(User.class)).thenReturn(true);
            when(joinPoint.proceed()).thenReturn(3);

            // When
            Object result = aspect.handleModifyingQuery(joinPoint);

            // Then
            assertThat(result).isEqualTo(3);
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());

            PendingEviction eviction = captor.getValue();
            assertThat(eviction.getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_DELETE);
        }

        @Test
        @DisplayName("Should handle DELETE FROM syntax")
        void shouldHandleDeleteFromSyntax() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("deleteFromOrder");
            when(methodSignature.getMethod()).thenReturn(method);
            doReturn(Order.class).when(entityCacheScanner).findBySimpleName("Order");
            when(entityCacheScanner.isCached(Order.class)).thenReturn(true);
            when(joinPoint.proceed()).thenReturn(1);

            // When
            aspect.handleModifyingQuery(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getEntityClassName()).isEqualTo(Order.class.getName());
        }
    }

    @Nested
    @DisplayName("handleModifyingQuery() tests - edge cases")
    class HandleModifyingQueryEdgeCaseTests {

        @Test
        @DisplayName("Should not collect when no @Query annotation")
        void shouldNotCollectWhenNoQueryAnnotation() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("noQueryMethod");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn(null);

            // When
            aspect.handleModifyingQuery(joinPoint);

            // Then
            verify(evictionCollector, never()).collect(any());
        }

        @Test
        @DisplayName("Should not collect when entity not found in scanner")
        void shouldNotCollectWhenEntityNotFound() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("updateUser");
            when(methodSignature.getMethod()).thenReturn(method);
            when(entityCacheScanner.findBySimpleName("User")).thenReturn(null);
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleModifyingQuery(joinPoint);

            // Then
            verify(evictionCollector, never()).collect(any());
        }

        @Test
        @DisplayName("Should propagate exception from original method")
        void shouldPropagateExceptionFromOriginalMethod() throws Throwable {
            // Given
            when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

            // When/Then
            assertThatThrownBy(() -> aspect.handleModifyingQuery(joinPoint))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");
        }
    }

    @Nested
    @DisplayName("handleEvictCache() tests")
    class HandleEvictCacheTests {

        @Test
        @DisplayName("Should collect eviction for annotated entity classes")
        void shouldCollectEvictionForAnnotatedEntityClasses() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictCacheMethod");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            Object result = aspect.handleEvictCache(joinPoint);

            // Then
            assertThat(result).isEqualTo("result");
            verify(evictionCollector, times(2)).collect(any(PendingEviction.class));
        }

        @Test
        @DisplayName("Should handle evictQueryCache=false setting")
        void shouldHandleEvictQueryCacheFalseSetting() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictCacheNoQueryCache");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());

            PendingEviction eviction = captor.getValue();
            assertThat(eviction.isEvictQueryCache()).isFalse();
        }

        @Test
        @DisplayName("Should use region from annotation")
        void shouldUseRegionFromAnnotation() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictCacheWithRegion");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());

            PendingEviction eviction = captor.getValue();
            assertThat(eviction.getRegion()).isEqualTo("custom-region");
        }

        @Test
        @DisplayName("Should propagate exception from original method")
        void shouldPropagateExceptionFromOriginalMethod() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictCacheMethod");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenThrow(new RuntimeException("Method error"));

            // When/Then
            assertThatThrownBy(() -> aspect.handleEvictCache(joinPoint))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Method error");

            // Should not collect eviction on exception
            verify(evictionCollector, never()).collect(any());
        }
    }

    @Nested
    @DisplayName("Operation type detection tests")
    class OperationTypeDetectionTests {

        @Test
        @DisplayName("Should detect BULK_DELETE from method name containing 'delete'")
        void shouldDetectBulkDeleteFromMethodName() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("updateUser");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.deleteAllUsers()");
            doReturn(User.class).when(entityCacheScanner).findBySimpleName("User");
            when(entityCacheScanner.isCached(User.class)).thenReturn(true);
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleModifyingQuery(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_DELETE);
        }

        @Test
        @DisplayName("Should detect BULK_DELETE from method name containing 'remove'")
        void shouldDetectBulkDeleteFromRemove() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("updateUser");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.removeOldUsers()");
            doReturn(User.class).when(entityCacheScanner).findBySimpleName("User");
            when(entityCacheScanner.isCached(User.class)).thenReturn(true);
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleModifyingQuery(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_DELETE);
        }
    }

    // Test repository interface with various annotation combinations
    interface TestRepository {
        @Query("UPDATE User u SET u.active = false WHERE u.lastLogin < :date")
        int updateUser();

        @Query("DELETE User u WHERE u.active = false")
        int deleteUser();

        @Query("DELETE FROM Order o WHERE o.status = 'CANCELLED'")
        int deleteFromOrder();

        void noQueryMethod();

        @EvictCache({User.class, Order.class})
        String evictCacheMethod();

        @EvictCache(value = User.class, evictQueryCache = false)
        String evictCacheNoQueryCache();

        @EvictCache(value = User.class, regions = "custom-region")
        String evictCacheWithRegion();
    }

    // Test entity classes
    static class User {
        private Long id;
        private String name;
    }

    static class Order {
        private Long id;
        private String status;
    }
}
