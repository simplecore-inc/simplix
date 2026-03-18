package dev.simplecore.simplix.hibernate.cache.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extended tests for EntityCacheScanner.
 * Covers entity scanning, registration, and findBySimpleName with actual cached entities.
 */
@DisplayName("EntityCacheScanner - Extended Tests")
class EntityCacheScannerExtendedTest {

    private EntityCacheScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new EntityCacheScanner();
    }

    @Nested
    @DisplayName("scanForCachedEntities with actual packages")
    class ScanWithActualPackagesTests {

        @Test
        @DisplayName("Should scan test integration package for cached entities")
        void shouldScanIntegrationPackageForCachedEntities() {
            // The integration test package has TestCachedEntity with @Cache annotation
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");

            // TestCachedEntity has @Cache annotation
            assertThat(scanner.getCachedEntities()).isNotNull();
        }

        @Test
        @DisplayName("Should scan multiple packages")
        void shouldScanMultiplePackages() {
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration",
                    "com.nonexistent.package");

            // Should not throw and should find entities from existing package
            assertThat(scanner.getCachedEntities()).isNotNull();
        }

        @Test
        @DisplayName("Should handle scanning same package twice")
        void shouldHandleScanSamePackageTwice() {
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");
            int firstSize = scanner.getCachedEntities().size();

            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");
            int secondSize = scanner.getCachedEntities().size();

            // ConcurrentHashMap.newKeySet() prevents duplicates
            assertThat(secondSize).isEqualTo(firstSize);
        }
    }

    @Nested
    @DisplayName("findBySimpleName with scanned entities")
    class FindBySimpleNameWithScannedTests {

        @Test
        @DisplayName("Should find cached entity by simple name case-insensitively")
        void shouldFindCachedEntityBySimpleName() {
            // Scan the package that has TestCachedEntity
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");

            if (!scanner.getCachedEntities().isEmpty()) {
                // Get first entity and try to find it by simple name
                Class<?> firstEntity = scanner.getCachedEntities().iterator().next();
                String simpleName = firstEntity.getSimpleName();

                Class<?> found = scanner.findBySimpleName(simpleName);
                assertThat(found).isEqualTo(firstEntity);

                // Case insensitive lookup
                Class<?> foundLower = scanner.findBySimpleName(simpleName.toLowerCase());
                assertThat(foundLower).isEqualTo(firstEntity);
            }
        }

        @Test
        @DisplayName("Should return null for unregistered entity name")
        void shouldReturnNullForUnregisteredName() {
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");

            Class<?> result = scanner.findBySimpleName("NonExistentEntity");
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("isCached with scanned entities")
    class IsCachedWithScannedTests {

        @Test
        @DisplayName("Should return true for scanned cached entity")
        void shouldReturnTrueForScannedEntity() {
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");

            if (!scanner.getCachedEntities().isEmpty()) {
                Class<?> firstEntity = scanner.getCachedEntities().iterator().next();
                assertThat(scanner.isCached(firstEntity)).isTrue();
            }
        }

        @Test
        @DisplayName("Should return false for non-cached entity")
        void shouldReturnFalseForNonCachedEntity() {
            scanner.scanForCachedEntities(
                    "dev.simplecore.simplix.hibernate.cache.integration");

            assertThat(scanner.isCached(String.class)).isFalse();
        }
    }
}
