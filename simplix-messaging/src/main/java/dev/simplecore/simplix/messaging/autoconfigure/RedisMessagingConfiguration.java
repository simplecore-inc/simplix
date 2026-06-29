package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.redis.RedisBrokerStrategy;
import dev.simplecore.simplix.messaging.broker.redis.RedisConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.redis.RedisIdempotencyStore;
import dev.simplecore.simplix.messaging.broker.redis.RedisScheduledMessagePublisher;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamPublisher;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamReplayService;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
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
    public IdempotencyStore idempotencyStore(StringRedisTemplate redisTemplate,
                                              MessagingProperties properties) {
        return new RedisIdempotencyStore(redisTemplate, properties.getIdempotent().getTtl());
    }

    @Bean
    public ReplayService replayService(StringRedisTemplate redisTemplate,
                                       MessagingProperties properties) {
        return new RedisStreamReplayService(redisTemplate, properties.getRedis().getKeyPrefix());
    }

    /**
     * @deprecated see {@link MessageScheduler}. Disabled by default since 1.1.1;
     *             opt in via {@code simplix.messaging.redis.scheduler.enabled=true}.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "simplix.messaging.redis.scheduler",
            name = "enabled", havingValue = "true", matchIfMissing = false)
    @Deprecated(since = "1.1.1", forRemoval = true)
    public MessageScheduler messageScheduler(BrokerStrategy brokerStrategy,
                                              StringRedisTemplate redisTemplate,
                                              MessagingProperties properties) {
        return new RedisScheduledMessagePublisher(brokerStrategy, redisTemplate,
                properties.getRedis().getKeyPrefix(), java.time.Duration.ofSeconds(5));
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
