package dev.simplecore.simplix.sync.autoconfigure;

import dev.simplecore.simplix.sync.config.SyncProperties;
import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import dev.simplecore.simplix.sync.core.NoOpInstanceSyncBroadcaster;
import dev.simplecore.simplix.sync.infrastructure.redis.RedisInstanceSyncBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Auto-configuration for the SimpliX Sync module.
 *
 * <p>Provides an {@link InstanceSyncBroadcaster} bean based on the configured mode:
 * <ul>
 *   <li>{@code simplix.sync.mode=LOCAL} (default) — {@link NoOpInstanceSyncBroadcaster}</li>
 *   <li>{@code simplix.sync.mode=DISTRIBUTED} — {@link RedisInstanceSyncBroadcaster}
 *       (requires Redis on classpath and a {@link RedisConnectionFactory} bean)</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(SyncProperties.class)
@ConditionalOnProperty(name = "simplix.sync.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXSyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXSyncAutoConfiguration.class);

    /**
     * Local mode: no-op broadcaster (default fallback).
     */
    @Bean
    @ConditionalOnMissingBean(InstanceSyncBroadcaster.class)
    public InstanceSyncBroadcaster noOpInstanceSyncBroadcaster() {
        log.info("SimpliX Sync: local mode (NoOp broadcaster)");
        return new NoOpInstanceSyncBroadcaster();
    }

    /**
     * Distributed mode with Redis Pub/Sub.
     */
    @Configuration
    @ConditionalOnProperty(name = "simplix.sync.mode", havingValue = "DISTRIBUTED")
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.RedisConnectionFactory")
    static class RedisConfiguration {

        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        public InstanceSyncBroadcaster redisInstanceSyncBroadcaster(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, byte[]> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(RedisSerializer.string());
            template.setValueSerializer(RedisSerializer.byteArray());
            template.afterPropertiesSet();

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setErrorHandler(t ->
                    LoggerFactory.getLogger(RedisInstanceSyncBroadcaster.class)
                            .error("Redis Pub/Sub sync listener error", t));
            container.setRecoveryInterval(5000L);
            container.afterPropertiesSet();
            container.start();

            LoggerFactory.getLogger(SimpliXSyncAutoConfiguration.class)
                    .info("SimpliX Sync: distributed mode (Redis Pub/Sub broadcaster)");
            return new RedisInstanceSyncBroadcaster(template, container);
        }
    }
}
