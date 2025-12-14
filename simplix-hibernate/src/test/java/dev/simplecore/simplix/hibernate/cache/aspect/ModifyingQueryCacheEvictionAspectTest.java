package dev.simplecore.simplix.hibernate.cache.aspect;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import dev.simplecore.simplix.hibernate.cache.event.PendingEviction;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import org.aspectj.lang.ProceedingJoinPoint;
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

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ModifyingQueryCacheEvictionAspect.
 *
 * <p>This aspect handles cache eviction via @EvictCache annotation only.
 * JPQL parsing was removed for reliability - developers must explicitly
 * declare which entities to evict using @EvictCache annotation.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ModifyingQueryCacheEvictionAspect Tests")
class ModifyingQueryCacheEvictionAspectTest {

    @Mock
    private TransactionAwareCacheEvictionCollector evictionCollector;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private ModifyingQueryCacheEvictionAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new ModifyingQueryCacheEvictionAspect(evictionCollector);

        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.toShortString()).thenReturn("Repository.updateEntity()");
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

        @Test
        @DisplayName("Should handle single entity class")
        void shouldHandleSingleEntityClass() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictSingleEntity");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn(1);

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector, times(1)).collect(captor.capture());

            PendingEviction eviction = captor.getValue();
            assertThat(eviction.getEntityClassName()).isEqualTo(User.class.getName());
        }
    }

    @Nested
    @DisplayName("Operation type detection tests")
    class OperationTypeDetectionTests {

        @Test
        @DisplayName("Should detect BULK_DELETE from method name containing 'delete'")
        void shouldDetectBulkDeleteFromMethodName() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictSingleEntity");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.deleteAllUsers()");
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_DELETE);
        }

        @Test
        @DisplayName("Should detect BULK_DELETE from method name containing 'remove'")
        void shouldDetectBulkDeleteFromRemove() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictSingleEntity");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.removeOldUsers()");
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_DELETE);
        }

        @Test
        @DisplayName("Should default to BULK_UPDATE for other method names")
        void shouldDefaultToBulkUpdateForOtherMethodNames() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictSingleEntity");
            when(methodSignature.getMethod()).thenReturn(method);
            when(methodSignature.toShortString()).thenReturn("Repository.updateUserStatus()");
            when(joinPoint.proceed()).thenReturn(5);

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector).collect(captor.capture());
            assertThat(captor.getValue().getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_UPDATE);
        }
    }

    @Nested
    @DisplayName("Multiple regions tests")
    class MultipleRegionsTests {

        @Test
        @DisplayName("Should use corresponding regions for multiple entities")
        void shouldUseCorrespondingRegionsForMultipleEntities() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictMultipleWithRegions");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector, times(2)).collect(captor.capture());

            var evictions = captor.getAllValues();
            // First entity should have first region
            assertThat(evictions.get(0).getEntityClassName()).isEqualTo(User.class.getName());
            assertThat(evictions.get(0).getRegion()).isEqualTo("user-region");
            // Second entity should have second region
            assertThat(evictions.get(1).getEntityClassName()).isEqualTo(Order.class.getName());
            assertThat(evictions.get(1).getRegion()).isEqualTo("order-region");
        }

        @Test
        @DisplayName("Should handle fewer regions than entities")
        void shouldHandleFewerRegionsThanEntities() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("evictMultipleWithPartialRegions");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            aspect.handleEvictCache(joinPoint);

            // Then
            ArgumentCaptor<PendingEviction> captor = ArgumentCaptor.forClass(PendingEviction.class);
            verify(evictionCollector, times(2)).collect(captor.capture());

            var evictions = captor.getAllValues();
            // First entity should have region
            assertThat(evictions.get(0).getRegion()).isEqualTo("user-region");
            // Second entity should have null region (bounds checking)
            assertThat(evictions.get(1).getRegion()).isNull();
        }
    }

    @Nested
    @DisplayName("Edge case tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle method without @EvictCache annotation gracefully")
        void shouldHandleMethodWithoutEvictCacheAnnotation() throws Throwable {
            // Given
            Method method = TestRepository.class.getMethod("noAnnotationMethod");
            when(methodSignature.getMethod()).thenReturn(method);
            when(joinPoint.proceed()).thenReturn("result");

            // When
            Object result = aspect.handleEvictCache(joinPoint);

            // Then
            assertThat(result).isEqualTo("result");
            verify(evictionCollector, never()).collect(any());
        }

        @Test
        @DisplayName("Should handle exception when getting annotation")
        void shouldHandleExceptionWhenGettingAnnotation() throws Throwable {
            // Given
            when(joinPoint.getSignature()).thenThrow(new RuntimeException("Signature error"));
            when(joinPoint.proceed()).thenReturn("result");

            // When
            Object result = aspect.handleEvictCache(joinPoint);

            // Then - should return result without collecting eviction
            assertThat(result).isEqualTo("result");
            verify(evictionCollector, never()).collect(any());
        }
    }

    // Test repository interface with @EvictCache annotations
    interface TestRepository {

        @EvictCache({User.class, Order.class})
        String evictCacheMethod();

        @EvictCache(value = User.class, evictQueryCache = false)
        String evictCacheNoQueryCache();

        @EvictCache(value = User.class, regions = "custom-region")
        String evictCacheWithRegion();

        @EvictCache(User.class)
        int evictSingleEntity();

        @EvictCache(value = {User.class, Order.class}, regions = {"user-region", "order-region"})
        String evictMultipleWithRegions();

        @EvictCache(value = {User.class, Order.class}, regions = {"user-region"})
        String evictMultipleWithPartialRegions();

        String noAnnotationMethod();
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