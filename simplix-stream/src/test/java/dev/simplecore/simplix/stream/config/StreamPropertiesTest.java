package dev.simplecore.simplix.stream.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamProperties defaults and nested configs.
 */
@DisplayName("StreamProperties")
class StreamPropertiesTest {

    @Test
    @DisplayName("should have correct top-level defaults")
    void shouldHaveCorrectTopLevelDefaults() {
        StreamProperties props = new StreamProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMode()).isEqualTo(StreamProperties.Mode.LOCAL);
    }

    @Nested
    @DisplayName("Mode enum")
    class ModeEnum {

        @Test
        @DisplayName("should have LOCAL and DISTRIBUTED values")
        void shouldHaveExpectedValues() {
            assertThat(StreamProperties.Mode.values()).containsExactly(
                    StreamProperties.Mode.LOCAL,
                    StreamProperties.Mode.DISTRIBUTED
            );
        }
    }

    @Nested
    @DisplayName("SessionConfig")
    class SessionConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.SessionConfig config = new StreamProperties().getSession();

            assertThat(config.getTimeout()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getGracePeriod()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getCleanupInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getMaxPerUser()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("SchedulerConfig")
    class SchedulerConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.SchedulerConfig config = new StreamProperties().getScheduler();

            assertThat(config.getThreadPoolSize()).isEqualTo(10);
            assertThat(config.getDefaultInterval()).isEqualTo(Duration.ofMillis(1000));
            assertThat(config.getMinInterval()).isEqualTo(Duration.ofMillis(100));
            assertThat(config.getMaxInterval()).isEqualTo(Duration.ofMillis(60000));
            assertThat(config.getMaxConsecutiveErrors()).isEqualTo(5);
            assertThat(config.getMaxTotalSchedulers()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("SubscriptionConfig")
    class SubscriptionConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.SubscriptionConfig config = new StreamProperties().getSubscription();

            assertThat(config.getMaxPerSession()).isEqualTo(20);
            assertThat(config.isPartialSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("BroadcastConfig")
    class BroadcastConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.BroadcastConfig config = new StreamProperties().getBroadcast();

            assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(config.getBatchSize()).isZero();
        }
    }

    @Nested
    @DisplayName("DistributedConfig")
    class DistributedConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.DistributedConfig config = new StreamProperties().getDistributed();

            assertThat(config.isRedisEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("LeaderElectionConfig")
    class LeaderElectionConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.LeaderElectionConfig config =
                    new StreamProperties().getDistributed().getLeaderElection();

            assertThat(config.getTtl()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getRenewInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(config.getRetryInterval()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Nested
    @DisplayName("PubSubConfig")
    class PubSubConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.PubSubConfig config =
                    new StreamProperties().getDistributed().getPubsub();

            assertThat(config.getChannelPrefix()).isEqualTo("stream:data:");
        }
    }

    @Nested
    @DisplayName("RegistryConfig")
    class RegistryConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.RegistryConfig config =
                    new StreamProperties().getDistributed().getRegistry();

            assertThat(config.getKeyPrefix()).isEqualTo("stream:");
            assertThat(config.getTtl()).isEqualTo(Duration.ofHours(1));
        }
    }

    @Nested
    @DisplayName("ServerConfig")
    class ServerConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.ServerConfig config = new StreamProperties().getServer();

            assertThat(config.getInstanceId()).isNull();
            assertThat(config.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(config.getDeadThreshold()).isEqualTo(Duration.ofMinutes(2));
            assertThat(config.getCleanupInterval()).isEqualTo(Duration.ofSeconds(60));
        }
    }

    @Nested
    @DisplayName("MonitoringConfig")
    class MonitoringConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.MonitoringConfig config = new StreamProperties().getMonitoring();

            assertThat(config.isMetricsEnabled()).isTrue();
            assertThat(config.getMetricsPrefix()).isEqualTo("simplix.stream");
            assertThat(config.getHealthCheckInterval()).isEqualTo(Duration.ofSeconds(10));
        }
    }

    @Nested
    @DisplayName("SecurityConfig")
    class SecurityConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.SecurityConfig config = new StreamProperties().getSecurity();

            assertThat(config.isEnforceAuthorization()).isFalse();
            assertThat(config.isRequireAuthentication()).isFalse();
        }
    }

    @Nested
    @DisplayName("WebSocketConfig")
    class WebSocketConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.WebSocketConfig config = new StreamProperties().getWebsocket();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getEndpoint()).isEqualTo("/ws/stream");
            assertThat(config.getAllowedOrigins()).isEqualTo("*");
            assertThat(config.isSockjsEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("AdminConfig")
    class AdminConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.AdminConfig config = new StreamProperties().getAdmin();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getPollingInterval()).isEqualTo(Duration.ofSeconds(2));
            assertThat(config.getCommandTimeout()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.getRetentionPeriod()).isEqualTo(Duration.ofDays(7));
            assertThat(config.getCleanupCron()).isEqualTo("0 0 3 * * ?");
        }
    }

    @Nested
    @DisplayName("EventPublishingConfig")
    class EventPublishingConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.EventPublishingConfig config = new StreamProperties().getEventPublishing();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getEvents()).isNotNull();
        }
    }

    @Nested
    @DisplayName("EventsConfig")
    class EventsConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.EventsConfig config = new StreamProperties().getEventPublishing().getEvents();

            assertThat(config.isSessionConnected()).isTrue();
            assertThat(config.isSessionDisconnected()).isTrue();
            assertThat(config.isSubscriptionChanged()).isFalse();
        }
    }

    @Nested
    @DisplayName("EventSourceConfig")
    class EventSourceConfigDefaults {

        @Test
        @DisplayName("should have correct defaults")
        void shouldHaveCorrectDefaults() {
            StreamProperties.EventSourceConfig config = new StreamProperties().getEventSource();

            assertThat(config.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("mutability")
    class Mutability {

        @Test
        @DisplayName("should allow setting properties")
        void shouldAllowSettingProperties() {
            StreamProperties props = new StreamProperties();
            props.setEnabled(false);
            props.setMode(StreamProperties.Mode.DISTRIBUTED);
            props.getSession().setMaxPerUser(10);
            props.getScheduler().setThreadPoolSize(20);

            assertThat(props.isEnabled()).isFalse();
            assertThat(props.getMode()).isEqualTo(StreamProperties.Mode.DISTRIBUTED);
            assertThat(props.getSession().getMaxPerUser()).isEqualTo(10);
            assertThat(props.getScheduler().getThreadPoolSize()).isEqualTo(20);
        }
    }
}
