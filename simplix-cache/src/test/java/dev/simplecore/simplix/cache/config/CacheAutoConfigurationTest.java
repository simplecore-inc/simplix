package dev.simplecore.simplix.cache.config;

import dev.simplecore.simplix.cache.config.SimpliXCacheAutoConfiguration;
import dev.simplecore.simplix.cache.config.CacheHealthIndicator;
import dev.simplecore.simplix.cache.config.CacheProperties;
import dev.simplecore.simplix.cache.service.CacheService;
import dev.simplecore.simplix.cache.strategy.LocalCacheStrategy;
import dev.simplecore.simplix.cache.strategy.RedisCacheStrategy;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Cache Auto Configuration Tests")
class CacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SimpliXCacheAutoConfiguration.class));

    @Test
    @DisplayName("Should create local cache strategy by default")
    void shouldCreateLocalCacheStrategyByDefault() {
        contextRunner
            .run(context -> {
                assertThat(context).hasSingleBean(LocalCacheStrategy.class);
                assertThat(context).hasSingleBean(CacheService.class);
                assertThat(context).doesNotHaveBean(RedisCacheStrategy.class);

                CacheService cacheService = context.getBean(CacheService.class);
                assertThat(cacheService.getStrategyName()).isEqualTo("LocalCacheStrategy");
            });
    }

    @Test
    @DisplayName("Should create local cache strategy when mode is local")
    void shouldCreateLocalCacheStrategyWhenModeIsLocal() {
        contextRunner
            .withPropertyValues("simplix.cache.mode=local")
            .run(context -> {
                assertThat(context).hasSingleBean(LocalCacheStrategy.class);
                assertThat(context).doesNotHaveBean(RedisCacheStrategy.class);
            });
    }

    @Test
    @DisplayName("Should create Redis cache strategy when mode is redis")
    void shouldCreateRedisCacheStrategyWhenModeIsRedis() {
        contextRunner
            .withPropertyValues("simplix.cache.mode=redis")
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(RedisTemplate.class, () -> mock(RedisTemplate.class))
            .run(context -> {
                assertThat(context).hasSingleBean(RedisCacheStrategy.class);
                assertThat(context).doesNotHaveBean(LocalCacheStrategy.class);

                CacheService cacheService = context.getBean(CacheService.class);
                assertThat(cacheService.getStrategyName()).isEqualTo("RedisCacheStrategy");
            });
    }

    @Test
    @DisplayName("Should configure cache properties")
    void shouldConfigureCacheProperties() {
        contextRunner
            .withPropertyValues(
                "simplix.cache.default-ttl-seconds=7200",
                "simplix.cache.cache-null-values=true"
            )
            .run(context -> {
                CacheProperties properties = context.getBean(CacheProperties.class);
                assertThat(properties.getDefaultTtlSeconds()).isEqualTo(7200);
                assertThat(properties.isCacheNullValues()).isTrue();
            });
    }

    @Test
    @DisplayName("Should configure per-cache settings")
    void shouldConfigurePerCacheSettings() {
        contextRunner
            .withPropertyValues(
                "simplix.cache.cache-configs.testCache.ttl-seconds=1800",
                "simplix.cache.cache-configs.userCache.ttl-seconds=3600"
            )
            .run(context -> {
                CacheProperties properties = context.getBean(CacheProperties.class);

                // Check that our specific caches are present with correct values
                assertThat(properties.getCacheConfigs()).containsKeys("testCache", "userCache");

                CacheProperties.CacheConfig testConfig = properties.getCacheConfigs().get("testCache");
                assertThat(testConfig.getTtlSeconds()).isEqualTo(1800);

                CacheProperties.CacheConfig userConfig = properties.getCacheConfigs().get("userCache");
                assertThat(userConfig.getTtlSeconds()).isEqualTo(3600);
            });
    }

    @Test
    @DisplayName("Should configure Redis properties")
    void shouldConfigureRedisProperties() {
        contextRunner
            .withPropertyValues(
                "simplix.cache.mode=redis",
                "simplix.cache.redis.key-prefix=myapp:",
                "simplix.cache.redis.use-key-prefix=true"
            )
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
            .withBean(RedisTemplate.class, () -> mock(RedisTemplate.class))
            .run(context -> {
                CacheProperties properties = context.getBean(CacheProperties.class);
                CacheProperties.RedisConfig redisConfig = properties.getRedis();

                assertThat(redisConfig.getKeyPrefix()).isEqualTo("myapp:");
                assertThat(redisConfig.isUseKeyPrefix()).isTrue();
            });
    }

    @Test
    @Disabled("Actuator not available in test scope")
    @DisplayName("Should create health indicator")
    void shouldCreateHealthIndicator() {
        contextRunner
            .run(context -> {
                assertThat(context).hasSingleBean(CacheHealthIndicator.class);
            });
    }

    @Test
    @DisplayName("Should handle missing Redis beans gracefully")
    void shouldHandleMissingRedisBeansGracefully() {
        contextRunner
            .withPropertyValues("simplix.cache.mode=redis")
            .run(context -> {
                // Should fall back to local cache when Redis beans are missing
                assertThat(context).hasSingleBean(LocalCacheStrategy.class);
                assertThat(context).doesNotHaveBean(RedisCacheStrategy.class);
            });
    }

    @Test
    @DisplayName("Should respect conditional on property")
    void shouldRespectConditionalOnProperty() {
        contextRunner
            .withPropertyValues("simplix.cache.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(CacheService.class);
                assertThat(context).doesNotHaveBean(LocalCacheStrategy.class);
                assertThat(context).doesNotHaveBean(RedisCacheStrategy.class);
            });
    }
}