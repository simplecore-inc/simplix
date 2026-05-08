package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.nats.NatsBrokerStrategy;
import dev.simplecore.simplix.messaging.broker.nats.NatsConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamPublisher;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamPullSubscriber;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamReplayService;
import dev.simplecore.simplix.messaging.broker.nats.NatsNativeIdempotencyStore;
import dev.simplecore.simplix.messaging.broker.nats.NatsScheduledMessagePublisher;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.Options;
import io.nats.client.api.KeyValueStatus;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NatsMessagingConfiguration} and the helper
 * {@link NatsOptionsBuilder}.
 *
 * <p>Tests do not open an actual NATS connection; they directly instantiate the
 * configuration class and call each {@code @Bean} method with mocked dependencies,
 * following the same pattern as {@link RedisMessagingConfigurationTest}.
 *
 * <p>TLS positive-path test is omitted because building a real SSLContext from
 * KeyStore files requires filesystem resources not available in unit tests.
 */
@DisplayName("NatsMessagingConfiguration")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NatsMessagingConfigurationTest {

    @Mock
    private Connection connection;

    @Mock
    private JetStream jetStream;

    @Mock
    private JetStreamManagement jsManagement;

    @Mock
    private KeyValueManagement kvManagement;

    @Mock
    private KeyValue keyValue;

    @Mock
    private KeyValueStatus kvStatus;

    private NatsMessagingConfiguration configuration;
    private MessagingProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new NatsMessagingConfiguration();
        properties = new MessagingProperties();
        properties.setBroker("nats");
        properties.getNats().setServers("nats://localhost:4222");
        properties.getNats().setConnectionName("test-connection");

        when(connection.keyValueManagement()).thenReturn(kvManagement);
        when(kvManagement.create(any())).thenReturn(kvStatus);
        when(connection.keyValue(any(String.class))).thenReturn(keyValue);
        // ensureKvBucket() now performs STREAM.INFO before STREAM.CREATE so an
        // existing bucket is detected without a CREATE call. The unit test only
        // needs the INFO path to succeed for natsMessageScheduler() bean creation.
        when(connection.jetStreamManagement()).thenReturn(jsManagement);
        when(jsManagement.getStreamInfo(any(String.class))).thenReturn(mock(StreamInfo.class));
    }

    // ---------------------------------------------------------------
    // Bean wiring tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Bean creation")
    class BeanCreationTests {

        @Test
        @DisplayName("natsConsumerGroupManager returns non-null NatsConsumerGroupManager")
        void natsConsumerGroupManager_returnsNonNull() {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            assertThat(manager).isNotNull();
        }

        @Test
        @DisplayName("natsJetStreamPublisher returns non-null NatsJetStreamPublisher")
        void natsJetStreamPublisher_returnsNonNull() {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            NatsJetStreamPublisher publisher =
                    configuration.natsJetStreamPublisher(jetStream, manager, properties);
            assertThat(publisher).isNotNull();
        }

        @Test
        @DisplayName("natsJetStreamPullSubscriber returns non-null NatsJetStreamPullSubscriber")
        void natsJetStreamPullSubscriber_returnsNonNull() {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            NatsJetStreamPullSubscriber subscriber =
                    configuration.natsJetStreamPullSubscriber(jetStream, manager);
            assertThat(subscriber).isNotNull();
        }

        @Test
        @DisplayName("natsBrokerStrategy returns initialized NatsBrokerStrategy")
        void natsBrokerStrategy_returnsInitializedStrategy() {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            NatsJetStreamPublisher publisher =
                    configuration.natsJetStreamPublisher(jetStream, manager, properties);
            NatsJetStreamPullSubscriber subscriber =
                    configuration.natsJetStreamPullSubscriber(jetStream, manager);

            NatsBrokerStrategy strategy = configuration.natsBrokerStrategy(
                    connection, jetStream, jsManagement, manager, publisher, subscriber, properties);

            assertThat(strategy).isNotNull();
            assertThat(strategy.isReady()).isTrue();
            strategy.shutdown();
        }

        @Test
        @DisplayName("natsReplayService returns ReplayService of NATS type")
        void natsReplayService_returnsNatsJetStreamReplayService() {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            ReplayService svc = configuration.natsReplayService(jetStream, manager);
            assertThat(svc).isNotNull().isInstanceOf(NatsJetStreamReplayService.class);
        }

        @Test
        @DisplayName("natsMessageScheduler returns MessageScheduler of NATS type")
        void natsMessageScheduler_returnsNatsScheduledMessagePublisher() throws Exception {
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            NatsJetStreamPublisher publisher =
                    configuration.natsJetStreamPublisher(jetStream, manager, properties);
            NatsJetStreamPullSubscriber subscriber =
                    configuration.natsJetStreamPullSubscriber(jetStream, manager);
            BrokerStrategy broker = configuration.natsBrokerStrategy(
                    connection, jetStream, jsManagement, manager, publisher, subscriber, properties);

            MessageScheduler scheduler =
                    configuration.natsMessageScheduler(broker, connection, properties);

            assertThat(scheduler).isNotNull().isInstanceOf(NatsScheduledMessagePublisher.class);
            ((NatsBrokerStrategy) broker).shutdown();
        }

        @Test
        @DisplayName("natsIdempotencyStore returns IdempotencyStore of NATS type")
        void natsIdempotencyStore_returnsNatsNativeIdempotencyStore() {
            IdempotencyStore store = configuration.natsIdempotencyStore(properties);
            assertThat(store).isNotNull().isInstanceOf(NatsNativeIdempotencyStore.class);
        }

        @Test
        @DisplayName("schedulerProperties.enabled defaults to false (deprecated, opt-in only since 1.1.1)")
        void schedulerProperties_enabled_defaultsFalse() {
            MessagingProperties.NatsProperties.SchedulerProperties sp =
                    new MessagingProperties.NatsProperties.SchedulerProperties();
            assertThat(sp.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("schedulerProperties.enabled is settable to true to opt into the scheduler bean")
        void schedulerProperties_enabled_settableTrue() {
            properties.getNats().getScheduler().setEnabled(true);
            assertThat(properties.getNats().getScheduler().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("consumerGroupManager resolveStreamName uses streamPrefix from properties")
        void consumerGroupManager_resolveStreamName_usesStreamPrefix() {
            properties.getNats().setStreamPrefix("simplix-");
            NatsConsumerGroupManager manager =
                    configuration.natsConsumerGroupManager(jsManagement, properties);
            assertThat(manager.resolveStreamName("orders")).isEqualTo("simplix-orders");
        }
    }

    // ---------------------------------------------------------------
    // NatsOptionsBuilder auth precedence tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("NatsOptionsBuilder.applyAuth — auth precedence")
    class AuthPrecedenceTests {

        @Test
        @DisplayName("URL-embedded credentials: no extra userInfo/token set on options")
        void optionsBuilder_urlEmbeddedAuth_noExtraCredentials() {
            MessagingProperties.NatsProperties props = new MessagingProperties.NatsProperties();
            props.setServers("nats://user:pass@localhost:4222");

            Options.Builder builder = new Options.Builder();
            NatsOptionsBuilder.applyAuth(builder, props);
            Options opts = builder.build();

            // jnats parses URL-embedded credentials when .server() is called; our helper
            // must NOT set additional userInfo / token so that URL auth remains the sole source.
            assertThat(opts.getUsername()).isNull();
            assertThat(opts.getToken()).isNull();
            assertThat(opts.getAuthHandler()).isNull();
        }

        @Test
        @DisplayName("username/password: userInfo set when URL has no auth")
        void optionsBuilder_usernamePassword_setsUserInfo() {
            MessagingProperties.NatsProperties props = new MessagingProperties.NatsProperties();
            props.setServers("nats://localhost:4222");
            props.setUsername("alice");
            props.setPassword("secret");

            Options.Builder builder = new Options.Builder();
            NatsOptionsBuilder.applyAuth(builder, props);
            Options opts = builder.build();

            assertThat(opts.getUsername()).isEqualTo("alice");
            assertThat(opts.getPassword()).isEqualTo("secret");
        }

        @Test
        @DisplayName("token: token set when no username/password")
        void optionsBuilder_token_setWhenNoUserPass() {
            MessagingProperties.NatsProperties props = new MessagingProperties.NatsProperties();
            props.setServers("nats://localhost:4222");
            props.setToken("my-token");

            Options.Builder builder = new Options.Builder();
            NatsOptionsBuilder.applyAuth(builder, props);
            Options opts = builder.build();

            assertThat(opts.getToken()).isEqualTo("my-token");
        }

        @Test
        @DisplayName("creds file: authHandler set when no username/password/token")
        void optionsBuilder_credsFile_setsAuthHandler() {
            MessagingProperties.NatsProperties props = new MessagingProperties.NatsProperties();
            props.setServers("nats://localhost:4222");
            props.setCredsFile("/tmp/test.creds");

            Options.Builder builder = new Options.Builder();
            // Nats.credentials(path) does file I/O only on first use, not at construction.
            // The returned AuthHandler object is non-null and wired into options.
            NatsOptionsBuilder.applyAuth(builder, props);
            Options opts = builder.build();

            assertThat(opts.getAuthHandler()).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // NatsOptionsBuilder TLS tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("NatsOptionsBuilder.applyTls")
    class TlsTests {

        @Test
        @DisplayName("applyTls is a no-op when TLS is disabled")
        void applyTls_noOp_whenDisabled() {
            MessagingProperties.NatsProperties.TlsProperties tls =
                    new MessagingProperties.NatsProperties.TlsProperties();
            tls.setEnabled(false);

            Options.Builder builder = new Options.Builder();
            NatsOptionsBuilder.applyTls(builder, tls);
            Options opts = builder.build();

            assertThat(opts.getSslContext()).isNull();
        }

        @Test
        @DisplayName("applyTls is a no-op when TLS properties are null")
        void applyTls_noOp_whenNull() {
            Options.Builder builder = new Options.Builder();
            NatsOptionsBuilder.applyTls(builder, null);
            Options opts = builder.build();

            assertThat(opts.getSslContext()).isNull();
        }
    }
}
