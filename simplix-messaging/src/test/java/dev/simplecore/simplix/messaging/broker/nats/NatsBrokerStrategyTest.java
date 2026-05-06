package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NatsBrokerStrategyTest {

    private Connection connection;
    private JetStream jetStream;
    private JetStreamManagement jsManagement;
    private NatsConsumerGroupManager groupManager;
    private NatsJetStreamPublisher publisher;
    private NatsJetStreamPullSubscriber subscriber;
    private MessagingProperties messagingProperties;
    private NatsBrokerStrategy strategy;

    @BeforeEach
    void setUp() {
        connection = mock(Connection.class);
        jetStream = mock(JetStream.class);
        jsManagement = mock(JetStreamManagement.class);
        groupManager = mock(NatsConsumerGroupManager.class);
        publisher = mock(NatsJetStreamPublisher.class);
        subscriber = mock(NatsJetStreamPullSubscriber.class);
        messagingProperties = new MessagingProperties();
        // Disable recovery scheduler by default to prevent background threads in tests
        messagingProperties.getNats().setPendingCheckInterval(Duration.ZERO);

        strategy = new NatsBrokerStrategy(
                connection, jetStream, jsManagement,
                groupManager, publisher, subscriber, messagingProperties);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Ensure strategy is shut down cleanly to avoid thread leaks
        if (strategy.isReady()) {
            strategy.shutdown();
        }
    }

    @Test
    @DisplayName("name() returns 'nats'")
    void name_returnsNats() {
        assertThat(strategy.name()).isEqualTo("nats");
    }

    @Test
    @DisplayName("capabilities() returns all-true BrokerCapabilities")
    void capabilities_areAllTrue() {
        BrokerCapabilities caps = strategy.capabilities();
        assertThat(caps).isEqualTo(new BrokerCapabilities(true, true, true, true, true, true, true));
    }

    @Test
    @DisplayName("send() delegates to the publisher")
    void send_delegatesToPublisher() {
        byte[] payload = "hello".getBytes();
        MessageHeaders headers = MessageHeaders.empty();
        PublishResult expected = new PublishResult("stream-1", "orders", Instant.now());
        when(publisher.send("orders", payload, headers)).thenReturn(expected);

        PublishResult result = strategy.send("orders", payload, headers);

        assertThat(result).isSameAs(expected);
        verify(publisher).send("orders", payload, headers);
    }

    @Test
    @DisplayName("subscribe() registers entry in activeSubscriptions, cancel removes it")
    void subscribe_registersInActiveMap_andTrackedSubscriptionRemovesOnCancel() {
        Subscription delegateSub = mock(Subscription.class);
        when(delegateSub.channel()).thenReturn("orders");
        when(delegateSub.groupName()).thenReturn("group-a");
        when(delegateSub.isActive()).thenReturn(true);
        when(subscriber.subscribe(any())).thenReturn(delegateSub);

        SubscribeRequest request = SubscribeRequest.builder()
                .channel("orders")
                .groupName("group-a")
                .consumerName("consumer-1")
                .listener((msg, ack) -> {})
                .build();

        Subscription result = strategy.subscribe(request);

        // Verify entry is in the activeSubscriptions map with expected key
        Map<String, Subscription> active = strategy.activeSubscriptions();
        assertThat(active).containsKey("orders:group-a:consumer-1");

        // Cancel the returned wrapper and verify entry is removed
        result.cancel();
        assertThat(active).doesNotContainKey("orders:group-a:consumer-1");
    }

    @Test
    @DisplayName("acknowledge() is a no-op and does not throw")
    void acknowledge_isNoOp_logsDebug() {
        assertThatNoException().isThrownBy(
                () -> strategy.acknowledge("orders", "group-a", "m1"));

        // Verify no interactions on collaborators (aside from potential logger internals)
        verifyNoInteractions(jetStream, publisher, jsManagement, groupManager, subscriber);
    }

    @Test
    @DisplayName("initialize() sets isReady() to true")
    void initialize_setsReady_andStartsRecoveryScheduler() {
        // pendingCheckInterval is ZERO, so no scheduler thread is started
        assertThat(strategy.isReady()).isFalse();

        strategy.initialize();

        assertThat(strategy.isReady()).isTrue();
    }

    @Test
    @DisplayName("shutdown() cancels all subscriptions, closes connection, sets isReady to false")
    void shutdown_cancelsAllSubscriptions_andClosesConnection_andSetsReadyFalse()
            throws InterruptedException {

        // Set up two delegate subscriptions that report as active
        Subscription delegate1 = mock(Subscription.class);
        when(delegate1.channel()).thenReturn("ch1");
        when(delegate1.groupName()).thenReturn("grp");
        when(delegate1.isActive()).thenReturn(true);

        Subscription delegate2 = mock(Subscription.class);
        when(delegate2.channel()).thenReturn("ch2");
        when(delegate2.groupName()).thenReturn("grp");
        when(delegate2.isActive()).thenReturn(true);

        when(subscriber.subscribe(any()))
                .thenReturn(delegate1)
                .thenReturn(delegate2);

        SubscribeRequest req1 = SubscribeRequest.builder()
                .channel("ch1").groupName("grp").consumerName("c1")
                .listener((m, a) -> {})
                .build();
        SubscribeRequest req2 = SubscribeRequest.builder()
                .channel("ch2").groupName("grp").consumerName("c2")
                .listener((m, a) -> {})
                .build();

        strategy.initialize();
        strategy.subscribe(req1);
        strategy.subscribe(req2);

        strategy.shutdown();

        // Both delegate cancel() calls must have been invoked
        verify(delegate1).cancel();
        verify(delegate2).cancel();

        // Connection must have been closed
        verify(connection).close();

        // isReady must be false after shutdown
        assertThat(strategy.isReady()).isFalse();
    }
}
