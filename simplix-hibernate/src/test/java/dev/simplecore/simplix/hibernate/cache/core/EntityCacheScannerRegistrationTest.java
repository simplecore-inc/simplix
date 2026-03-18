package dev.simplecore.simplix.hibernate.cache.core;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EntityCacheScanner internal registration logic.
 * Covers registerCachedEntity with different @Cache annotation configurations.
 */
@DisplayName("EntityCacheScanner - Registration Tests")
class EntityCacheScannerRegistrationTest {

    private EntityCacheScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new EntityCacheScanner();
    }

    @Nested
    @DisplayName("registerCachedEntity with region")
    class RegisterWithRegionTests {

        @Test
        @DisplayName("Should register entity with explicit region name")
        void shouldRegisterWithExplicitRegion() throws Exception {
            // Use reflection to call private registerCachedEntity method
            Method registerMethod = EntityCacheScanner.class
                    .getDeclaredMethod("registerCachedEntity", Class.class);
            registerMethod.setAccessible(true);

            registerMethod.invoke(scanner, CachedEntityWithRegion.class);

            assertThat(scanner.getCachedEntities()).contains(CachedEntityWithRegion.class);
            assertThat(scanner.getCacheRegions()).contains("custom-region");
        }

        @Test
        @DisplayName("Should register entity with empty region as default")
        void shouldRegisterWithEmptyRegion() throws Exception {
            Method registerMethod = EntityCacheScanner.class
                    .getDeclaredMethod("registerCachedEntity", Class.class);
            registerMethod.setAccessible(true);

            registerMethod.invoke(scanner, CachedEntityDefaultRegion.class);

            assertThat(scanner.getCachedEntities()).contains(CachedEntityDefaultRegion.class);
            // Default region (@Cache without region) has empty string region
            // which does not get added to cacheRegions
        }
    }

    @Nested
    @DisplayName("findBySimpleName after registration")
    class FindBySimpleNameTests {

        @Test
        @DisplayName("Should find registered entity by simple name")
        void shouldFindBySimpleName() throws Exception {
            Method registerMethod = EntityCacheScanner.class
                    .getDeclaredMethod("registerCachedEntity", Class.class);
            registerMethod.setAccessible(true);

            registerMethod.invoke(scanner, CachedEntityWithRegion.class);

            // Case insensitive
            assertThat(scanner.findBySimpleName("CachedEntityWithRegion")).isNotNull();
            assertThat(scanner.findBySimpleName("cachedentitywithregion")).isNotNull();
        }

        @Test
        @DisplayName("Should find entity using lambda filter")
        void shouldFindUsingLambdaFilter() throws Exception {
            Method registerMethod = EntityCacheScanner.class
                    .getDeclaredMethod("registerCachedEntity", Class.class);
            registerMethod.setAccessible(true);

            registerMethod.invoke(scanner, CachedEntityWithRegion.class);
            registerMethod.invoke(scanner, CachedEntityDefaultRegion.class);

            // Should find one at a time
            Class<?> result = scanner.findBySimpleName("CachedEntityWithRegion");
            assertThat(result).isEqualTo(CachedEntityWithRegion.class);
        }
    }

    // Test entities with different @Cache configurations

    @Entity
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "custom-region")
    static class CachedEntityWithRegion {
        @jakarta.persistence.Id
        Long id;
    }

    @Entity
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    static class CachedEntityDefaultRegion {
        @jakarta.persistence.Id
        Long id;
    }
}
