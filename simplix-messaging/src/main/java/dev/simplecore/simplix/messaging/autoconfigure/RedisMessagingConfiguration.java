package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.redis.RedisBrokerStrategy;
import dev.simplecore.simplix.messaging.broker.redis.RedisConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamPublisher;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber;
import dev.simplecore.simplix.messaging.subscriber.IdempotentGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration for Redis-based messaging broker.
 *
 * <p>Activated when {@code simplix.messaging.broker=redis} and Spring Data Redis
 * is on the classpath. Provides all Redis infrastructure beans required by
 * {@link RedisBrokerStrategy}.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "simplix.messaging", name = "broker", havingValue = "redis")
@Slf4j
public class RedisMessagingConfiguration {

    @Bean
    public RedisConsumerGroupManager redisConsumerGroupManager(StringRedisTemplate redisTemplate,
                                                               MessagingProperties properties) {
        return new RedisConsumerGroupManager(redisTemplate, properties.getRedis().getKeyPrefix());
    }

    @Bean
    public RedisStreamPublisher redisStreamPublisher(StringRedisTemplate redisTemplate,
                                                     MessagingProperties properties) {
        long defaultMaxLength = resolveDefaultMaxLength(properties);
        return new RedisStreamPublisher(
                redisTemplate,
                properties.getRedis().getKeyPrefix(),
                defaultMaxLength,
                properties.getRedis().getPayloadEncoding()
        );
    }

    @Bean
    public RedisStreamSubscriber redisStreamSubscriber(StringRedisTemplate redisTemplate,
                                                       MessagingProperties properties) {
        return new RedisStreamSubscriber(
                redisTemplate,
                properties.getRedis().getKeyPrefix(),
                properties.getRedis().getPollTimeout(),
                properties.getRedis().getBatchSize(),
                properties.getRedis().getPayloadEncoding()
        );
    }

    @Bean
    public RedisBrokerStrategy redisBrokerStrategy(StringRedisTemplate redisTemplate,
                                                    MessagingProperties properties,
                                                    RedisConsumerGroupManager consumerGroupManager,
                                                    RedisStreamPublisher streamPublisher,
                                                    RedisStreamSubscriber streamSubscriber) {
        RedisBrokerStrategy strategy = new RedisBrokerStrategy(
                redisTemplate,
                properties.getRedis().getKeyPrefix(),
                consumerGroupManager,
                streamPublisher,
                streamSubscriber,
                properties.getRedis().getPendingCheckInterval(),
                properties.getRedis().getClaimMinIdleTime()
        );
        strategy.initialize();
        return strategy;
    }

    @Bean
    public IdempotentGuard idempotentGuard(StringRedisTemplate redisTemplate,
                                           MessagingProperties properties) {
        return new IdempotentGuard(redisTemplate, properties.getIdempotent().getTtl());
    }

    /**
     * Resolve the default max length for stream trimming.
     * Falls back to 50000 if no channels are configured.
     */
    private long resolveDefaultMaxLength(MessagingProperties properties) {
        // Use the minimum maxLength across all configured channels as the default,
        // or fall back to 50000 if no channels are configured.
        return properties.getChannels().values().stream()
                .mapToLong(MessagingProperties.ChannelProperties::getMaxLength)
                .min()
                .orElse(50_000L);
    }
}
