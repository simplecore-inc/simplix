package dev.simplecore.simplix.messaging.autoconfigure;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.nats.NatsBrokerStrategy;
import dev.simplecore.simplix.messaging.broker.nats.NatsConsumerGroupManager;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamPublisher;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamPullSubscriber;
import dev.simplecore.simplix.messaging.broker.nats.NatsJetStreamReplayService;
import dev.simplecore.simplix.messaging.broker.nats.NatsLeaderElection;
import dev.simplecore.simplix.messaging.broker.nats.NatsNativeIdempotencyStore;
import dev.simplecore.simplix.messaging.broker.nats.NatsScheduledMessagePublisher;
import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.KeyValue;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Auto-configuration for the NATS JetStream broker.
 *
 * <p>Activated when {@code simplix.messaging.broker=nats} and the jnats client
 * ({@code io.nats.client.Connection}) is on the classpath. Wires the NATS
 * connection, JetStream APIs, broker strategy, and the broker-specific SPI
 * implementations (replay, scheduler, idempotency).
 *
 * <p>The NATS connection is destroyed via {@link Connection#close()} when the
 * Spring context shuts down.
 */
@Configuration
@ConditionalOnClass(name = "io.nats.client.Connection")
@ConditionalOnProperty(prefix = "simplix.messaging", name = "broker", havingValue = "nats")
@Slf4j
public class NatsMessagingConfiguration {

    /**
     * Creates and opens the NATS connection. Auth and TLS are applied via
     * {@link NatsOptionsBuilder} helper methods.
     *
     * @param props the messaging configuration
     * @return an open {@link Connection}
     * @throws Exception if the connection cannot be established
     */
    @Bean(destroyMethod = "close")
    public Connection natsConnection(MessagingProperties props) throws Exception {
        MessagingProperties.NatsProperties n = props.getNats();
        Options.Builder builder = new Options.Builder()
                .server(n.getServers())
                .connectionName(n.getConnectionName())
                .connectionTimeout(n.getConnectionTimeout())
                .reconnectWait(n.getReconnectWait())
                .maxReconnects(n.getMaxReconnects());
        NatsOptionsBuilder.applyAuth(builder, n);
        NatsOptionsBuilder.applyTls(builder, n.getTls());
        return Nats.connect(builder.build());
    }

    /**
     * Exposes the {@link JetStream} API from the shared connection.
     *
     * @param conn the NATS connection
     * @return a {@link JetStream} instance
     * @throws IOException if the JetStream context cannot be obtained
     */
    @Bean
    public JetStream jetStream(Connection conn) throws IOException {
        return conn.jetStream();
    }

    /**
     * Exposes the {@link JetStreamManagement} API for stream/consumer lifecycle management.
     *
     * @param conn the NATS connection
     * @return a {@link JetStreamManagement} instance
     * @throws IOException if the management context cannot be obtained
     */
    @Bean
    public JetStreamManagement jsManagement(Connection conn) throws IOException {
        return conn.jetStreamManagement();
    }

    /**
     * Creates the {@link NatsConsumerGroupManager} used to resolve stream/subject names
     * and to ensure durable consumers are created before subscriptions begin.
     *
     * @param jsm   the JetStream management API
     * @param props the messaging configuration
     * @return a configured {@link NatsConsumerGroupManager}
     */
    @Bean
    public NatsConsumerGroupManager natsConsumerGroupManager(JetStreamManagement jsm,
                                                              MessagingProperties props) {
        return new NatsConsumerGroupManager(jsm, props);
    }

    /**
     * Creates the {@link NatsJetStreamPublisher} responsible for publishing messages
     * to NATS JetStream subjects.
     *
     * @param js  the JetStream publish API
     * @param mgr the consumer group manager (used for subject resolution)
     * @return a configured publisher
     */
    @Bean
    public NatsJetStreamPublisher natsJetStreamPublisher(JetStream js,
                                                         NatsConsumerGroupManager mgr,
                                                         MessagingProperties props) {
        return new NatsJetStreamPublisher(js, mgr, props);
    }

    /**
     * Creates the {@link NatsJetStreamPullSubscriber} responsible for polling
     * messages from durable JetStream consumers.
     *
     * @param js  the JetStream subscribe API
     * @param mgr the consumer group manager
     * @return a configured pull subscriber
     */
    @Bean
    public NatsJetStreamPullSubscriber natsJetStreamPullSubscriber(JetStream js,
                                                                    NatsConsumerGroupManager mgr) {
        return new NatsJetStreamPullSubscriber(js, mgr);
    }

    /**
     * Creates and initializes the {@link NatsBrokerStrategy} that ties together all
     * NATS components into the broker-strategy abstraction.
     *
     * @param conn  the NATS connection
     * @param js    the JetStream publish API
     * @param jsm   the JetStream management API
     * @param mgr   the consumer group manager
     * @param pub   the JetStream publisher
     * @param sub   the JetStream pull subscriber
     * @param props the messaging configuration
     * @return an initialized {@link NatsBrokerStrategy}
     */
    @Bean(destroyMethod = "shutdown")
    public NatsBrokerStrategy natsBrokerStrategy(Connection conn,
                                                  JetStream js,
                                                  JetStreamManagement jsm,
                                                  NatsConsumerGroupManager mgr,
                                                  NatsJetStreamPublisher pub,
                                                  NatsJetStreamPullSubscriber sub,
                                                  MessagingProperties props) {
        NatsBrokerStrategy strategy =
                new NatsBrokerStrategy(conn, js, jsm, mgr, pub, sub, props);
        strategy.initialize();
        return strategy;
    }

    /**
     * Creates the NATS-backed {@link ReplayService} that supports replaying historical
     * messages via ephemeral JetStream push consumers.
     *
     * @param js  the JetStream subscribe API
     * @param mgr the consumer group manager
     * @return a {@link ReplayService} implementation
     */
    @Bean
    public ReplayService natsReplayService(JetStream js, NatsConsumerGroupManager mgr) {
        return new NatsJetStreamReplayService(js, mgr);
    }

    /**
     * Creates the NATS-backed {@link MessageScheduler} that uses a KV bucket for
     * deferred message storage and leader-elected polling for delivery.
     *
     * <p>The KV bucket is created idempotently (an "already exists" error is ignored).
     *
     * <p>Disabled by setting {@code simplix.messaging.nats.scheduler.enabled=false}
     * for deployments whose NATS user is not granted KV permissions
     * ({@code $JS.API.STREAM.INFO.KV_<bucket>}, {@code $KV.<bucket>.>}). When
     * disabled this bean is not registered and applications using only direct
     * publish/subscribe can boot without KV ACL grants.
     *
     * @param broker the broker strategy used to publish due messages
     * @param conn   the NATS connection for KV access
     * @param props  the messaging configuration
     * @return a configured (but not yet started) {@link MessageScheduler}
     * @throws IllegalStateException if the KV bucket cannot be accessed
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "simplix.messaging.nats.scheduler",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public MessageScheduler natsMessageScheduler(BrokerStrategy broker,
                                                  Connection conn,
                                                  MessagingProperties props) {
        MessagingProperties.NatsProperties.SchedulerProperties sp =
                props.getNats().getScheduler();
        ensureKvBucket(conn, sp.getKvBucket());

        KeyValue kv = openKvBucket(conn, sp.getKvBucket());
        NatsLeaderElection leader = new NatsLeaderElection(
                kv, "leader-" + sp.getKvBucket(), props.getInstanceId());
        return new NatsScheduledMessagePublisher(broker, kv, leader, sp.getPollInterval());
    }

    /**
     * Creates the NATS-native {@link IdempotencyStore} that relies on the JetStream
     * duplicate-window for publisher-side deduplication.
     *
     * @param props the messaging configuration
     * @return an {@link IdempotencyStore} implementation
     */
    @Bean
    public IdempotencyStore natsIdempotencyStore(MessagingProperties props) {
        return new NatsNativeIdempotencyStore(props.getNats().getDuplicateWindow());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Creates the KV bucket if it does not already exist.
     * A {@link JetStreamApiException} indicating the bucket already exists is silently ignored.
     */
    private void ensureKvBucket(Connection conn, String bucketName) {
        try {
            conn.keyValueManagement().create(
                    KeyValueConfiguration.builder().name(bucketName).build());
        } catch (JetStreamApiException e) {
            // Bucket already exists — this is expected in normal operation.
            log.debug("KV bucket '{}' already exists or could not be created: {}",
                    bucketName, e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create KV bucket '" + bucketName + "'", e);
        }
    }

    /**
     * Opens (binds to) an existing KV bucket by name.
     */
    private KeyValue openKvBucket(Connection conn, String bucketName) {
        try {
            return conn.keyValue(bucketName);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to access KV bucket '" + bucketName + "'", e);
        }
    }
}
