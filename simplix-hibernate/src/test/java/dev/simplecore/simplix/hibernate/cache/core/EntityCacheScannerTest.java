package dev.simplecore.simplix.hibernate.cache.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityCacheScanner")
class EntityCacheScannerTest {

    private EntityCacheScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new EntityCacheScanner();
    }

    @Nested
    @DisplayName("Initial state")
    class InitialStateTests {

        @Test
        @DisplayName("should have empty cached entities before scanning")
        void shouldHaveEmptyCachedEntities() {
            assertThat(scanner.getCachedEntities()).isEmpty();
        }

        @Test
        @DisplayName("should have empty cache regions before scanning")
        void shouldHaveEmptyCacheRegions() {
            assertThat(scanner.getCacheRegions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("isCached")
    class IsCachedTests {

        @Test
        @DisplayName("should return false for unscanned entity")
        void shouldReturnFalseForUnscannedEntity() {
            assertThat(scanner.isCached(String.class)).isFalse();
        }
    }

    @Nested
    @DisplayName("findBySimpleName")
    class FindBySimpleNameTests {

        @Test
        @DisplayName("should return null for null name")
        void shouldReturnNullForNullName() {
            assertThat(scanner.findBySimpleName(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty name")
        void shouldReturnNullForEmptyName() {
            assertThat(scanner.findBySimpleName("")).isNull();
        }

        @Test
        @DisplayName("should return null when no entities scanned")
        void shouldReturnNullWhenNoEntitiesScanned() {
            assertThat(scanner.findBySimpleName("User")).isNull();
        }
    }

    @Nested
    @DisplayName("getCachedEntities")
    class GetCachedEntitiesTests {

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            var entities = scanner.getCachedEntities();
            assertThat(entities).isNotNull();
            // Returned set should be a copy (unmodifiable)
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> entities.add(Object.class)
            );
        }
    }

    @Nested
    @DisplayName("getCacheRegions")
    class GetCacheRegionsTests {

        @Test
        @DisplayName("should return unmodifiable set")
        void shouldReturnUnmodifiableSet() {
            var regions = scanner.getCacheRegions();
            assertThat(regions).isNotNull();
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> regions.add("test-region")
            );
        }
    }

    @Nested
    @DisplayName("scanForCachedEntities")
    class ScanForCachedEntitiesTests {

        @Test
        @DisplayName("should handle null base packages")
        void shouldHandleNullBasePackages() {
            // Should not throw, defaults to scanning all
            scanner.scanForCachedEntities((String[]) null);
            // After scan, results depend on classpath entities
            assertThat(scanner.getCachedEntities()).isNotNull();
        }

        @Test
        @DisplayName("should handle empty base packages array")
        void shouldHandleEmptyBasePackages() {
            scanner.scanForCachedEntities();
            assertThat(scanner.getCachedEntities()).isNotNull();
        }

        @Test
        @DisplayName("should scan nonexistent package without error")
        void shouldScanNonexistentPackageWithoutError() {
            scanner.scanForCachedEntities("com.nonexistent.package.that.does.not.exist");
            assertThat(scanner.getCachedEntities()).isEmpty();
        }
    }
}
