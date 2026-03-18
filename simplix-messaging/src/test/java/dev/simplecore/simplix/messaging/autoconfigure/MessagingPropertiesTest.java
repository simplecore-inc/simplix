package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.redis.PayloadEncoding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessagingProperties")
class MessagingPropertiesTest {

    private MessagingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MessagingProperties();
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValueTests {

        @Test
        @DisplayName("should have 'local' as default broker")
        void shouldDefaultToLocalBroker() {
            assertThat(properties.getBroker()).isEqualTo("local");
        }

        @Test
        @DisplayName("should have a non-blank default instance ID")
        void shouldHaveDefaultInstanceId() {
            assertThat(properties.getInstanceId()).isNotBlank();
        }

        @Test
        @DisplayName("should have empty channels map by default")
        void shouldHaveEmptyChannels() {
            assertThat(properties.getChannels()).isEmpty();
        }

        @Test
        @DisplayName("should have default subscriber startup delay of zero")
        void shouldHaveZeroStartupDelay() {
            assertThat(properties.getSubscriberStartupDelay()).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("RedisProperties defaults")
    class RedisPropertiesTests {

        @Test
        @DisplayName("should have default Redis properties")
        void shouldHaveDefaults() {
            MessagingProperties.RedisProperties redis = properties.getRedis();
            assertThat(redis.getKeyPrefix()).isEmpty();
            assertThat(redis.getPendingCheckInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(redis.getClaimMinIdleTime()).isEqualTo(Duration.ofMinutes(5));
            assertThat(redis.getPollTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(redis.getBatchSize()).isEqualTo(10);
            assertThat(redis.getPayloadEncoding()).isEqualTo(PayloadEncoding.BASE64);
        }

        @Test
        @DisplayName("should allow setting Redis properties")
        void shouldAllowSettingProperties() {
            MessagingProperties.RedisProperties redis = properties.getRedis();
            redis.setKeyPrefix("pacs:");
            redis.setPendingCheckInterval(Duration.ofSeconds(60));
            redis.setClaimMinIdleTime(Duration.ofMinutes(10));
            redis.setPollTimeout(Duration.ofSeconds(5));
            redis.setBatchSize(50);
            redis.setPayloadEncoding(PayloadEncoding.RAW);

            assertThat(redis.getKeyPrefix()).isEqualTo("pacs:");
            assertThat(redis.getPendingCheckInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(redis.getClaimMinIdleTime()).isEqualTo(Duration.ofMinutes(10));
            assertThat(redis.getPollTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(redis.getBatchSize()).isEqualTo(50);
            assertThat(redis.getPayloadEncoding()).isEqualTo(PayloadEncoding.RAW);
        }
    }

    @Nested
    @DisplayName("IdempotentProperties defaults")
    class IdempotentPropertiesTests {

        @Test
        @DisplayName("should have 24h default TTL")
        void shouldHaveDefaultTtl() {
            assertThat(properties.getIdempotent().getTtl()).isEqualTo(Duration.ofHours(24));
        }

        @Test
        @DisplayName("should allow setting TTL")
        void shouldAllowSettingTtl() {
            properties.getIdempotent().setTtl(Duration.ofHours(48));
            assertThat(properties.getIdempotent().getTtl()).isEqualTo(Duration.ofHours(48));
        }
    }

    @Nested
    @DisplayName("ErrorProperties defaults")
    class ErrorPropertiesTests {

        @Test
        @DisplayName("should have default error properties")
        void shouldHaveDefaults() {
            MessagingProperties.ErrorProperties error = properties.getError();
            assertThat(error.getMaxRetries()).isEqualTo(3);
            assertThat(error.getRetryBackoff()).isEqualTo(Duration.ofSeconds(1));
            assertThat(error.getDeadLetter().isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should allow setting error properties")
        void shouldAllowSettingProperties() {
            MessagingProperties.ErrorProperties error = properties.getError();
            error.setMaxRetries(5);
            error.setRetryBackoff(Duration.ofSeconds(2));
            error.getDeadLetter().setEnabled(true);

            assertThat(error.getMaxRetries()).isEqualTo(5);
            assertThat(error.getRetryBackoff()).isEqualTo(Duration.ofSeconds(2));
            assertThat(error.getDeadLetter().isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("ChannelProperties")
    class ChannelPropertiesTests {

        @Test
        @DisplayName("should have default channel properties")
        void shouldHaveDefaults() {
            MessagingProperties.ChannelProperties channelProps = new MessagingProperties.ChannelProperties();
            assertThat(channelProps.getContentType()).isEqualTo("application/json");
            assertThat(channelProps.getMaxLength()).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("should allow setting channel properties")
        void shouldAllowSettingProperties() {
            MessagingProperties.ChannelProperties channelProps = new MessagingProperties.ChannelProperties();
            channelProps.setContentType("application/protobuf");
            channelProps.setMaxLength(100_000L);

            assertThat(channelProps.getContentType()).isEqualTo("application/protobuf");
            assertThat(channelProps.getMaxLength()).isEqualTo(100_000L);
        }
    }

    @Nested
    @DisplayName("resolveMaxLength")
    class ResolveMaxLengthTests {

        @Test
        @DisplayName("should return configured max length for known channel")
        void shouldReturnConfiguredMaxLength() {
            MessagingProperties.ChannelProperties channelProps = new MessagingProperties.ChannelProperties();
            channelProps.setMaxLength(75_000L);

            Map<String, MessagingProperties.ChannelProperties> channels = new LinkedHashMap<>();
            channels.put("orders", channelProps);
            properties.setChannels(channels);

            assertThat(properties.resolveMaxLength("orders")).isEqualTo(75_000L);
        }

        @Test
        @DisplayName("should return default max length for unknown channel")
        void shouldReturnDefaultMaxLength() {
            assertThat(properties.resolveMaxLength("unknown-channel")).isEqualTo(50_000L);
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("should allow setting broker")
        void shouldSetBroker() {
            properties.setBroker("redis");
            assertThat(properties.getBroker()).isEqualTo("redis");
        }

        @Test
        @DisplayName("should allow setting instanceId")
        void shouldSetInstanceId() {
            properties.setInstanceId("my-instance");
            assertThat(properties.getInstanceId()).isEqualTo("my-instance");
        }

        @Test
        @DisplayName("should allow setting subscriber startup delay")
        void shouldSetStartupDelay() {
            properties.setSubscriberStartupDelay(Duration.ofSeconds(5));
            assertThat(properties.getSubscriberStartupDelay()).isEqualTo(Duration.ofSeconds(5));
        }
    }
}
