package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisBroadcaster;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisLeaderElection;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Auto-configuration for distributed mode.
 * <p>
 * In distributed mode:
 * <ul>
 *   <li>Session management is always DB-based (via SimpliXStreamPersistenceConfiguration)</li>
 *   <li>When Redis is enabled (redis.enabled=true): Redis leader election + Redis Pub/Sub broadcast</li>
 *   <li>When Redis is disabled: Each server runs independently with local broadcast only</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplix.stream.mode", havingValue = "distributed")
public class SimpliXStreamDistributedConfiguration {

    @Value("${simplix.stream.instance-id:#{T(java.util.UUID).randomUUID().toString()}}")
    private String instanceId;

    /**
     * Generate a unique instance ID if not configured.
     */
    @Bean
    @ConditionalOnMissingBean(name = "streamInstanceId")
    public String streamInstanceId() {
        String id = instanceId != null ? instanceId : UUID.randomUUID().toString();
        log.info("Stream instance ID: {}", id);
        return id;
    }

    /**
     * Local broadcaster for distributed mode without Redis.
     * <p>
     * When Redis is not enabled, each server operates independently,
     * broadcasting only to its own local sessions.
     */
    @Bean
    @ConditionalOnProperty(name = "simplix.stream.distributed.redis-enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(BroadcastService.class)
    public LocalBroadcaster distributedLocalBroadcaster() {
        LocalBroadcaster broadcaster = new LocalBroadcaster();
        broadcaster.initialize();
        log.info("Created local broadcaster for distributed mode (Redis not enabled)");
        return broadcaster;
    }

    /**
     * Redis-enabled configuration for distributed mode.
     * <p>
     * Provides leader election and cross-server broadcasting when Redis is available.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnProperty(name = "simplix.stream.distributed.redis-enabled", havingValue = "true")
    @ConditionalOnBean(StringRedisTemplate.class)
    public static class RedisEnabledConfiguration {

        /**
         * Redis broadcaster bean.
         * <p>
         * Broadcasts data to all server instances via Redis Pub/Sub.
         */
        @Bean
        @Primary
        @ConditionalOnMissingBean(BroadcastService.class)
        public RedisBroadcaster redisBroadcaster(
                StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper,
                StreamProperties properties,
                String streamInstanceId) {

            RedisBroadcaster broadcaster = new RedisBroadcaster(
                    redisTemplate, objectMapper, properties, streamInstanceId);
            broadcaster.initialize();
            log.info("Created Redis broadcaster for cross-server messaging");
            return broadcaster;
        }

        /**
         * Redis leader election bean.
         * <p>
         * Ensures only one server executes schedulers for each subscription key.
         */
        @Bean
        @ConditionalOnMissingBean
        public RedisLeaderElection redisLeaderElection(
                StringRedisTemplate redisTemplate,
                ScheduledExecutorService streamScheduledExecutor,
                String streamInstanceId,
                StreamProperties properties) {

            log.info("Created Redis leader election");
            return new RedisLeaderElection(
                    redisTemplate, streamScheduledExecutor, streamInstanceId, properties);
        }

        /**
         * Redis message listener container for Pub/Sub.
         */
        @Bean
        @ConditionalOnMissingBean(name = "streamRedisMessageListenerContainer")
        public RedisMessageListenerContainer streamRedisMessageListenerContainer(
                RedisConnectionFactory connectionFactory,
                RedisBroadcaster redisBroadcaster,
                ObjectMapper objectMapper,
                StreamProperties properties) {

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);

            String channelPrefix = properties.getDistributed().getPubsub().getChannelPrefix();

            // Listener for broadcast messages
            MessageListenerAdapter broadcastListener = new MessageListenerAdapter(
                    new BroadcastMessageHandler(redisBroadcaster, objectMapper));
            broadcastListener.setDefaultListenerMethod("handleMessage");
            container.addMessageListener(broadcastListener, new PatternTopic(channelPrefix + "*"));

            log.info("Configured Redis message listener for channel pattern: {}*", channelPrefix);
            return container;
        }
    }

    /**
     * Handler for Redis broadcast messages.
     */
    @Slf4j
    public static class BroadcastMessageHandler {

        private final RedisBroadcaster broadcaster;
        private final ObjectMapper objectMapper;

        public BroadcastMessageHandler(RedisBroadcaster broadcaster, ObjectMapper objectMapper) {
            this.broadcaster = broadcaster;
            this.objectMapper = objectMapper;
        }

        public void handleMessage(String message, String channel) {
            try {
                if (channel.contains(":direct:")) {
                    // Direct message
                    RedisBroadcaster.DirectMessage directMessage =
                            objectMapper.readValue(message, RedisBroadcaster.DirectMessage.class);
                    broadcaster.handleDirectMessage(directMessage);
                } else {
                    // Broadcast message
                    RedisBroadcaster.BroadcastMessage broadcastMessage =
                            objectMapper.readValue(message, RedisBroadcaster.BroadcastMessage.class);
                    broadcaster.handleBroadcastMessage(broadcastMessage);
                }
            } catch (Exception e) {
                log.error("Error handling Redis message from channel {}: {}", channel, e.getMessage());
            }
        }
    }
}
