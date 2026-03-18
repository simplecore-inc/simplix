package dev.simplecore.simplix.hibernate.cache.config;

import org.hibernate.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HibernateCacheHolder")
@ExtendWith(MockitoExtension.class)
class HibernateCacheHolderTest {

    @Mock
    private Cache mockCache;

    @Mock
    private Cache anotherCache;

    @BeforeEach
    void setUp() {
        // Ensure clean state before each test
        HibernateCacheHolder.reset();
    }

    @AfterEach
    void tearDown() {
        HibernateCacheHolder.reset();
    }

    @Nested
    @DisplayName("getCache")
    class GetCacheTests {

        @Test
        @DisplayName("should return null when not initialized")
        void shouldReturnNullWhenNotInitialized() {
            assertThat(HibernateCacheHolder.getCache()).isNull();
        }

        @Test
        @DisplayName("should return cache after setCache")
        void shouldReturnCacheAfterSet() {
            HibernateCacheHolder.setCache(mockCache);
            assertThat(HibernateCacheHolder.getCache()).isEqualTo(mockCache);
        }
    }

    @Nested
    @DisplayName("setCache")
    class SetCacheTests {

        @Test
        @DisplayName("should return true when setting cache for the first time")
        void shouldReturnTrueOnFirstSet() {
            boolean result = HibernateCacheHolder.setCache(mockCache);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when cache is already set")
        void shouldReturnFalseWhenAlreadySet() {
            HibernateCacheHolder.setCache(mockCache);
            boolean result = HibernateCacheHolder.setCache(anotherCache);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should keep the first cache when set twice")
        void shouldKeepFirstCache() {
            HibernateCacheHolder.setCache(mockCache);
            HibernateCacheHolder.setCache(anotherCache);
            assertThat(HibernateCacheHolder.getCache()).isEqualTo(mockCache);
        }
    }

    @Nested
    @DisplayName("reset")
    class ResetTests {

        @Test
        @DisplayName("should clear cache reference")
        void shouldClearCacheReference() {
            HibernateCacheHolder.setCache(mockCache);
            HibernateCacheHolder.reset();
            assertThat(HibernateCacheHolder.getCache()).isNull();
        }

        @Test
        @DisplayName("should allow setting cache again after reset")
        void shouldAllowSettingAfterReset() {
            HibernateCacheHolder.setCache(mockCache);
            HibernateCacheHolder.reset();

            boolean result = HibernateCacheHolder.setCache(anotherCache);
            assertThat(result).isTrue();
            assertThat(HibernateCacheHolder.getCache()).isEqualTo(anotherCache);
        }
    }

    @Nested
    @DisplayName("isReset")
    class IsResetTests {

        @Test
        @DisplayName("should return true when not initialized")
        void shouldReturnTrueWhenNotInitialized() {
            assertThat(HibernateCacheHolder.isReset()).isTrue();
        }

        @Test
        @DisplayName("should return false when cache is set")
        void shouldReturnFalseWhenCacheSet() {
            HibernateCacheHolder.setCache(mockCache);
            assertThat(HibernateCacheHolder.isReset()).isFalse();
        }

        @Test
        @DisplayName("should return true after reset")
        void shouldReturnTrueAfterReset() {
            HibernateCacheHolder.setCache(mockCache);
            HibernateCacheHolder.reset();
            assertThat(HibernateCacheHolder.isReset()).isTrue();
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("should throw UnsupportedOperationException when instantiated via reflection")
        void shouldThrowWhenInstantiatedViaReflection() {
            try {
                java.lang.reflect.Constructor<HibernateCacheHolder> constructor =
                        HibernateCacheHolder.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                org.junit.jupiter.api.Assertions.assertThrows(
                        java.lang.reflect.InvocationTargetException.class,
                        constructor::newInstance
                );
            } catch (NoSuchMethodException e) {
                org.junit.jupiter.api.Assertions.fail("Constructor should exist");
            }
        }
    }
}
