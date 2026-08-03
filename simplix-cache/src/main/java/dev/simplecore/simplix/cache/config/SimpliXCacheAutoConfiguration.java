package dev.simplecore.simplix.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.cache.provider.CoreCacheProviderImpl;
import dev.simplecore.simplix.cache.service.CacheService;
import dev.simplecore.simplix.cache.strategy.CacheStrategy;
import dev.simplecore.simplix.cache.strategy.LocalCacheStrategy;
import dev.simplecore.simplix.cache.strategy.NatsCacheStrategy;
import dev.simplecore.simplix.cache.strategy.RedisCacheStrategy;
import dev.simplecore.simplix.core.cache.CacheProvider;
import io.nats.client.Connection;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SimpliX Cache Auto Configuration
 * Automatically configures caching based on available dependencies and properties
 */
@AutoConfiguration
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(name = "simplix.cache.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SimpliXCacheAutoConfiguration {

    /**
     * Local Cache Manager
     * Used when mode=local or as fallback when Redis is not available
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager localCacheManager(CacheProperties properties) {
        log.info("Configuring local cache manager (mode={}, fallback={})",
            properties.getMode(),
            !"redis".equals(properties.getMode()));
        return new ConcurrentMapCacheManager(
            properties.getCacheConfigs().keySet().toArray(new String[0])
        );
    }

    /**
     * Local cache strategy
     * Default strategy when mode=local or when Redis is not available
     */
    @Bean
    @ConditionalOnMissingBean(CacheStrategy.class)
    public CacheStrategy localCacheStrategy() {
        log.info("Using local cache strategy");
        LocalCacheStrategy strategy = new LocalCacheStrategy();
        strategy.initialize();
        return strategy;
    }

    /**
     * Cache service bean
     */
    @Bean
    @ConditionalOnMissingBean(CacheService.class)
    public CacheService cacheService(CacheStrategy cacheStrategy) {
        return new CacheService(cacheStrategy);
    }

    /**
     * Core Cache Provider implementation for SPI
     */
    @Bean
    @ConditionalOnClass(name = "dev.simplecore.simplix.core.cache.CacheProvider")
    public CacheProvider coreCacheProvider(CacheService cacheService) {
        return new CoreCacheProviderImpl(cacheService);
    }

    /**
     * Cache metrics collector
     */
    @Bean
    @ConditionalOnProperty(name = "simplix.cache.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public CacheMetricsCollector cacheMetricsCollector(CacheStrategy cacheStrategy, CacheProperties properties) {
        return new CacheMetricsCollector(cacheStrategy, properties);
    }

    /**
     * Redis Configuration
     * Configures Redis cache when Redis is available on the classpath
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    @ConditionalOnProperty(name = "simplix.cache.mode", havingValue = "redis")
    public static class RedisConfiguration {

        /**
         * Redis Cache Manager for Spring Cache abstraction
         */
        @Bean
        @Primary
        @ConditionalOnBean(
            type = "org.springframework.data.redis.connection.RedisConnectionFactory"
        )
        public CacheManager redisCacheManager(
                RedisConnectionFactory connectionFactory,
                CacheProperties properties) {
            log.info("Configuring Redis cache manager");

            RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofSeconds(properties.getDefaultTtlSeconds()))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

            if (!properties.isCacheNullValues()) {
                defaultConfig = defaultConfig.disableCachingNullValues();
            }

            RedisCacheManager.RedisCacheManagerBuilder builder =
                RedisCacheManager.RedisCacheManagerBuilder
                    .fromConnectionFactory(connectionFactory)
                    .cacheDefaults(defaultConfig);

            // Configure specific caches with custom TTLs
            final RedisCacheConfiguration finalConfig = defaultConfig;
            properties.getCacheConfigs().forEach((cacheName, cacheConfig) -> {
                RedisCacheConfiguration customConfig = finalConfig
                    .entryTtl(Duration.ofSeconds(cacheConfig.getTtlSeconds()));
                builder.withCacheConfiguration(cacheName, customConfig);
            });

            return builder.build();
        }

        /**
         * String Redis Template for Redis strategy
         */
        @Bean
        @ConditionalOnMissingBean(StringRedisTemplate.class)
        @ConditionalOnBean(
            type = "org.springframework.data.redis.connection.RedisConnectionFactory"
        )
        public StringRedisTemplate stringRedisTemplate(
                RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }

        /**
         * Redis cache strategy
         */
        @Bean
        @ConditionalOnMissingBean(CacheStrategy.class)
        @ConditionalOnBean(
            StringRedisTemplate.class
        )
        public CacheStrategy redisCacheStrategy(
                StringRedisTemplate redisTemplate,
                CacheProperties properties) {
            log.info("Using Redis cache strategy");
            RedisCacheStrategy strategy =
                new RedisCacheStrategy(redisTemplate, properties);
            // Initialize lazily to support testing with mocks
            try {
                strategy.initialize();
            } catch (Exception e) {
                log.warn("Failed to initialize Redis cache strategy during bean creation, will retry on first use: {}", e.getMessage());
            }
            return strategy;
        }
    }

    /**
     * NATS Configuration
     * Activates a NATS JetStream KV-backed cache strategy when
     * {@code simplix.cache.mode=nats} and an {@link io.nats.client.Connection}
     * bean is available (typically supplied by simplix-messaging).
     */
    @Slf4j
    @Configuration
    @ConditionalOnClass(name = "io.nats.client.Connection")
    @ConditionalOnProperty(name = "simplix.cache.mode", havingValue = "nats")
    public static class NatsConfiguration {

        @Bean
        @ConditionalOnMissingBean(CacheStrategy.class)
        @ConditionalOnBean(Connection.class)
        public CacheStrategy natsCacheStrategy(Connection connection,
                                                CacheProperties properties,
                                                ObjectProvider<ObjectMapper> objectMapperProvider) {
            log.info("Using NATS KV cache strategy");
            ObjectMapper objectMapper = objectMapperProvider
                    .getIfAvailable(() -> {
                        ObjectMapper fallback =
                                new ObjectMapper();
                        fallback.registerModule(new JavaTimeModule());
                        return fallback;
                    });
            NatsCacheStrategy strategy =
                    new NatsCacheStrategy(connection, properties, objectMapper);
            try {
                strategy.initialize();
            } catch (Exception e) {
                log.warn("Failed to initialize NATS cache strategy during bean creation, will retry on first use: {}", e.getMessage());
            }
            return strategy;
        }
    }

    /**
     * Health Configuration
     * Configures health indicator when Spring Boot Actuator is available
     */
    @Configuration
    @ConditionalOnClass(HealthIndicator.class)
    public static class HealthConfiguration {

        /**
         * Cache health indicator
         */
        @Bean
        public CacheHealthIndicator cacheHealthIndicator(CacheStrategy cacheStrategy) {
            return new CacheHealthIndicator(cacheStrategy);
        }
    }

    /**
     * Metrics Configuration
     * Enables scheduling for cache metrics collection when metrics are enabled
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "simplix.cache.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public static class MetricsConfiguration {
        // Scheduling is enabled for CacheMetricsCollector @Scheduled methods
    }
}