package dev.simplecore.simplix.hibernate.cache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.hibernate.cache.provider.HazelcastCacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.InfinispanCacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.RedisCacheProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Auto-configuration for distributed cache providers.
 * <p>
 * Conditionally registers cache providers based on available dependencies:
 * <ul>
 *   <li>Redis - when spring-data-redis is available</li>
 *   <li>Hazelcast - when hazelcast is available</li>
 *   <li>Infinispan - when infinispan-core is available</li>
 * </ul>
 */
@Slf4j
// Explicit ordering - run after HibernateJpa but before SimpliXHibernateCache
@AutoConfiguration(
    after = org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    before = SimpliXHibernateCacheAutoConfiguration.class
)
// Respect global disable property
@ConditionalOnProperty(prefix = "simplix.hibernate.cache", name = "disabled", havingValue = "false", matchIfMissing = true)
public class DistributedCacheAutoConfiguration {

    // ============================================
    // Redis Configuration
    // ============================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    static class RedisConfiguration {

        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean(name = "hibernateCacheRedisTemplate")
        public RedisTemplate<String, String> hibernateCacheRedisTemplate(
                RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, String> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(new StringRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(new StringRedisSerializer());
            template.afterPropertiesSet();
            return template;
        }

        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
        public RedisMessageListenerContainer redisMessageListenerContainer(
                RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            // Initialize the container - Spring will call start() via SmartLifecycle
            container.afterPropertiesSet();
            return container;
        }

        @Bean
        @ConditionalOnBean({RedisTemplate.class, RedisMessageListenerContainer.class})
        @ConditionalOnMissingBean(RedisCacheProvider.class)
        public RedisCacheProvider redisCacheProvider(
                RedisTemplate<String, String> hibernateCacheRedisTemplate,
                RedisMessageListenerContainer redisMessageListenerContainer,
                @Autowired(required = false) ObjectMapper objectMapper) {
            // Use provided ObjectMapper or create a default one
            ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper();
            log.info("✔ Configuring Redis Cache Provider (ObjectMapper: {})",
                    objectMapper != null ? "injected" : "default");
            return new RedisCacheProvider(
                hibernateCacheRedisTemplate,
                redisMessageListenerContainer,
                mapper);
        }
    }

    // ============================================
    // Hazelcast Configuration
    // ============================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.hazelcast.core.HazelcastInstance")
    static class HazelcastConfiguration {

        @Bean
        @ConditionalOnBean(name = "hazelcastInstance")
        @ConditionalOnMissingBean(HazelcastCacheProvider.class)
        public HazelcastCacheProvider hazelcastCacheProvider(
                com.hazelcast.core.HazelcastInstance hazelcastInstance) {
            log.info("✔ Configuring Hazelcast Cache Provider");
            return new HazelcastCacheProvider(hazelcastInstance);
        }
    }

    // ============================================
    // Infinispan Configuration
    // ============================================

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.infinispan.manager.EmbeddedCacheManager")
    static class InfinispanConfiguration {

        @Bean
        @ConditionalOnBean(name = "cacheManager", value = org.infinispan.manager.EmbeddedCacheManager.class)
        @ConditionalOnMissingBean(InfinispanCacheProvider.class)
        public InfinispanCacheProvider infinispanCacheProvider(
                org.infinispan.manager.EmbeddedCacheManager cacheManager) {
            log.info("✔ Configuring Infinispan Cache Provider");
            return new InfinispanCacheProvider(cacheManager);
        }
    }
}
