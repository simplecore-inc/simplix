package dev.simplecore.simplix.cache.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CacheProperties")
class CachePropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should have local mode by default")
        void shouldHaveLocalModeByDefault() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.getMode()).isEqualTo("local");
        }

        @Test
        @DisplayName("should have default TTL of 3600 seconds")
        void shouldHaveDefaultTtl() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.getDefaultTtlSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should not cache null values by default")
        void shouldNotCacheNullValuesByDefault() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.isCacheNullValues()).isFalse();
        }

        @Test
        @DisplayName("should have default cache config with 3600 TTL")
        void shouldHaveDefaultCacheConfig() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.getCacheConfigs()).containsKey("default");
            assertThat(properties.getCacheConfigs().get("default").getTtlSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should have metrics enabled by default")
        void shouldHaveMetricsEnabled() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.getMetrics()).isNotNull();
            assertThat(properties.getMetrics().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have redis config with default key prefix")
        void shouldHaveRedisConfigDefaults() {
            CacheProperties properties = new CacheProperties();
            assertThat(properties.getRedis()).isNotNull();
            assertThat(properties.getRedis().getKeyPrefix()).isEqualTo("cache:");
            assertThat(properties.getRedis().isUseKeyPrefix()).isTrue();
        }
    }

    @Nested
    @DisplayName("CacheConfig")
    class CacheConfigTests {

        @Test
        @DisplayName("should create CacheConfig with default TTL")
        void shouldCreateWithDefaultTtl() {
            CacheProperties.CacheConfig config = new CacheProperties.CacheConfig();
            assertThat(config.getTtlSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("should create CacheConfig with custom TTL")
        void shouldCreateWithCustomTtl() {
            CacheProperties.CacheConfig config = new CacheProperties.CacheConfig(600L);
            assertThat(config.getTtlSeconds()).isEqualTo(600L);
        }

        @Test
        @DisplayName("should set TTL via setter")
        void shouldSetTtlViaSetter() {
            CacheProperties.CacheConfig config = new CacheProperties.CacheConfig();
            config.setTtlSeconds(120L);
            assertThat(config.getTtlSeconds()).isEqualTo(120L);
        }
    }

    @Nested
    @DisplayName("MetricsConfig")
    class MetricsConfigTests {

        @Test
        @DisplayName("should allow disabling metrics")
        void shouldAllowDisablingMetrics() {
            CacheProperties.MetricsConfig config = new CacheProperties.MetricsConfig();
            config.setEnabled(false);
            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("RedisConfig")
    class RedisConfigTests {

        @Test
        @DisplayName("should allow setting custom key prefix")
        void shouldSetKeyPrefix() {
            CacheProperties.RedisConfig config = new CacheProperties.RedisConfig();
            config.setKeyPrefix("myapp:");
            assertThat(config.getKeyPrefix()).isEqualTo("myapp:");
        }

        @Test
        @DisplayName("should allow disabling key prefix")
        void shouldDisableKeyPrefix() {
            CacheProperties.RedisConfig config = new CacheProperties.RedisConfig();
            config.setUseKeyPrefix(false);
            assertThat(config.isUseKeyPrefix()).isFalse();
        }
    }
}
