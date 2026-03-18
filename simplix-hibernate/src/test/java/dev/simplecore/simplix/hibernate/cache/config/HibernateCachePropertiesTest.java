package dev.simplecore.simplix.hibernate.cache.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HibernateCacheProperties")
class HibernateCachePropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should not be disabled by default")
        void shouldNotBeDisabledByDefault() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            assertThat(properties.isDisabled()).isFalse();
        }

        @Test
        @DisplayName("should have query cache auto-eviction enabled by default")
        void shouldHaveQueryCacheAutoEvictionEnabled() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            assertThat(properties.isQueryCacheAutoEviction()).isTrue();
        }

        @Test
        @DisplayName("should have null scan packages by default")
        void shouldHaveNullScanPackages() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            assertThat(properties.getScanPackages()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter methods")
    class SetterMethods {

        @Test
        @DisplayName("should set disabled to true")
        void shouldSetDisabled() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            properties.setDisabled(true);
            assertThat(properties.isDisabled()).isTrue();
        }

        @Test
        @DisplayName("should disable query cache auto-eviction")
        void shouldDisableQueryCacheAutoEviction() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            properties.setQueryCacheAutoEviction(false);
            assertThat(properties.isQueryCacheAutoEviction()).isFalse();
        }

        @Test
        @DisplayName("should set scan packages")
        void shouldSetScanPackages() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            String[] packages = {"com.example.entity", "com.example.model"};
            properties.setScanPackages(packages);
            assertThat(properties.getScanPackages()).containsExactly("com.example.entity", "com.example.model");
        }

        @Test
        @DisplayName("should set empty scan packages array")
        void shouldSetEmptyScanPackages() {
            HibernateCacheProperties properties = new HibernateCacheProperties();
            properties.setScanPackages(new String[]{});
            assertThat(properties.getScanPackages()).isEmpty();
        }
    }
}
