package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.infrastructure.distributed.NatsBroadcaster;
import dev.simplecore.simplix.stream.infrastructure.distributed.NatsLeaderElection;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisBroadcaster;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisLeaderElection;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.List;
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
     * Local broadcaster for distributed mode without a cross-server broker.
     * <p>
     * Activated when {@code simplix.stream.distributed.broker=NONE} (default).
     * Each server operates independently, broadcasting only to its own local
     * sessions.
     */
    @Bean
    @ConditionalOnProperty(name = "simplix.stream.distributed.broker", havingValue = "NONE", matchIfMissing = true)
    @ConditionalOnMissingBean(BroadcastService.class)
    public LocalBroadcaster distributedLocalBroadcaster() {
        LocalBroadcaster broadcaster = new LocalBroadcaster();
        broadcaster.initialize();
        log.info("Created local broadcaster for distributed mode (broker=NONE)");
        return broadcaster;
    }

    /**
     * Redis-enabled configuration for distributed mode.
     * <p>
     * Activated when {@code simplix.stream.distributed.broker=REDIS} and a
     * {@code StringRedisTemplate} bean is available. Provides leader election
     * and cross-server broadcasting via Redis Pub/Sub.
     */
    @Configuration
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    @ConditionalOnProperty(name = "simplix.stream.distributed.broker", havingValue = "REDIS")
    @ConditionalOnBean(StringRedisTemplate.class)
    public static class RedisEnabledConfiguration {

        /**
         * Redis broadcaster bean.
         * <p>
         * Broadcasts data to all server instances via Redis Pub/Sub.
         * Injects all available SubscriberLookup beans so that the receiving
         * instance can resolve its own local subscribers for cross-instance delivery.
         */
        @Bean
        @Primary
        @ConditionalOnMissingBean(BroadcastService.class)
        public RedisBroadcaster redisBroadcaster(
                StringRedisTemplate redisTemplate,
                ObjectMapper objectMapper,
                StreamProperties properties,
                String streamInstanceId,
                @Autowired(required = false) List<SubscriberLookup> subscriberLookups) {

            List<SubscriberLookup> lookups = subscriberLookups != null ? subscriberLookups : List.of();
            RedisBroadcaster broadcaster = new RedisBroadcaster(
                    redisTemplate, objectMapper, properties, streamInstanceId, lookups);
            broadcaster.initialize();
            log.info("Created Redis broadcaster for cross-server messaging (subscriberLookups={})",
                    lookups.size());
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
     * NATS-enabled configuration for distributed mode. Activated when
     * {@code simplix.stream.distributed.broker=NATS} and an
     * {@code io.nats.client.Connection} bean is available.
     *
     * <p>Provides leader election + cross-server broadcasting purely on NATS,
     * removing the runtime dependency on Redis.
     */
    @Configuration
    @ConditionalOnClass(name = "io.nats.client.Connection")
    @ConditionalOnProperty(name = "simplix.stream.distributed.broker", havingValue = "NATS")
    public static class NatsEnabledConfiguration {

        @Bean
        @Primary
        @ConditionalOnMissingBean(BroadcastService.class)
        @ConditionalOnBean(io.nats.client.Connection.class)
        public NatsBroadcaster natsBroadcaster(
                io.nats.client.Connection connection,
                ObjectMapper objectMapper,
                StreamProperties properties,
                String streamInstanceId,
                @Autowired(required = false) List<SubscriberLookup> subscriberLookups) {

            List<SubscriberLookup> lookups = subscriberLookups != null ? subscriberLookups : List.of();
            NatsBroadcaster broadcaster = new NatsBroadcaster(
                    connection, objectMapper, properties, streamInstanceId, lookups);
            broadcaster.initialize();
            log.info("Created NATS broadcaster for cross-server messaging (subscriberLookups={})",
                    lookups.size());
            return broadcaster;
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(io.nats.client.Connection.class)
        public NatsLeaderElection natsLeaderElection(
                io.nats.client.Connection connection,
                ScheduledExecutorService streamScheduledExecutor,
                String streamInstanceId,
                StreamProperties properties) {
            log.info("Created NATS leader election");
            return new NatsLeaderElection(
                    connection, streamScheduledExecutor, streamInstanceId, properties);
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
