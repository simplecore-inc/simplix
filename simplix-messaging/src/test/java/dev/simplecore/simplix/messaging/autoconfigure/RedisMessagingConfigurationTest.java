package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.redis.PayloadEncoding;
import dev.simplecore.simplix.messaging.broker.redis.RedisConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamPublisher;
import dev.simplecore.simplix.messaging.broker.redis.RedisStreamSubscriber;
import dev.simplecore.simplix.messaging.subscriber.IdempotentGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisMessagingConfiguration")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisMessagingConfigurationTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisMessagingConfiguration configuration;
    private MessagingProperties properties;

    @BeforeEach
    void setUp() {
        configuration = new RedisMessagingConfiguration();
        properties = new MessagingProperties();
        properties.getRedis().setKeyPrefix("test:");
        properties.getRedis().setPollTimeout(Duration.ofSeconds(3));
        properties.getRedis().setBatchSize(20);
        properties.getRedis().setPayloadEncoding(PayloadEncoding.BASE64);
        properties.getRedis().setPendingCheckInterval(Duration.ofSeconds(30));
        properties.getRedis().setClaimMinIdleTime(Duration.ofMinutes(5));
    }

    @Nested
    @DisplayName("Bean creation")
    class BeanCreationTests {

        @Test
        @DisplayName("should create RedisConsumerGroupManager")
        void shouldCreateConsumerGroupManager() {
            RedisConsumerGroupManager manager = configuration.redisConsumerGroupManager(redisTemplate, properties);
            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("should create RedisStreamPublisher")
        void shouldCreatePublisher() {
            RedisStreamPublisher publisher = configuration.redisStreamPublisher(redisTemplate, properties);
            assertThat(publisher).isNotNull();
        }

        @Test
        @DisplayName("should create RedisStreamSubscriber")
        void shouldCreateSubscriber() {
            RedisStreamSubscriber subscriber = configuration.redisStreamSubscriber(redisTemplate, properties);
            assertThat(subscriber).isNotNull();
        }

        @Test
        @DisplayName("should create RedisBrokerStrategy")
        void shouldCreateBrokerStrategy() {
            RedisConsumerGroupManager manager = configuration.redisConsumerGroupManager(redisTemplate, properties);
            RedisStreamPublisher publisher = configuration.redisStreamPublisher(redisTemplate, properties);
            RedisStreamSubscriber subscriber = configuration.redisStreamSubscriber(redisTemplate, properties);

            var strategy = configuration.redisBrokerStrategy(
                    redisTemplate, properties, manager, publisher, subscriber);

            assertThat(strategy).isNotNull();
            assertThat(strategy.isReady()).isTrue();
            assertThat(strategy.name()).isEqualTo("redis");
            strategy.shutdown();
        }

        @Test
        @DisplayName("should create IdempotentGuard")
        void shouldCreateIdempotentGuard() {
            IdempotentGuard guard = configuration.idempotentGuard(redisTemplate, properties);
            assertThat(guard).isNotNull();
        }

        @Test
        @DisplayName("should use minimum max length from channel configs")
        void shouldUseMinMaxLengthFromChannels() {
            MessagingProperties.ChannelProperties ch1 = new MessagingProperties.ChannelProperties();
            ch1.setMaxLength(30_000L);
            MessagingProperties.ChannelProperties ch2 = new MessagingProperties.ChannelProperties();
            ch2.setMaxLength(20_000L);

            Map<String, MessagingProperties.ChannelProperties> channels = new LinkedHashMap<>();
            channels.put("ch1", ch1);
            channels.put("ch2", ch2);
            properties.setChannels(channels);

            RedisStreamPublisher publisher = configuration.redisStreamPublisher(redisTemplate, properties);
            assertThat(publisher).isNotNull();
        }
    }
}
