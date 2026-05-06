package dev.simplecore.simplix.messaging.integration;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.nats.NatsBrokerStrategy;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.replay.ReplayService;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NatsJetStreamIntegrationTest.TestApp.class,
        properties = {
                "simplix.messaging.broker=nats",
                "simplix.messaging.nats.servers=nats://app:apppass@localhost:4222",
                "simplix.messaging.nats.stream-prefix=test-",
                "simplix.messaging.nats.subject-prefix=test."
        })
@RequiresNats
class NatsJetStreamIntegrationTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    static class TestApp { }

    @Autowired NatsBrokerStrategy broker;
    @Autowired JetStreamManagement jsm;
    @Autowired ReplayService replayService;

    private final String channel = "scenario-publish-" + UUID.randomUUID();

    @AfterEach
    void cleanupStreams() {
        try { jsm.deleteStream("test-" + channel); }
        catch (IOException | JetStreamApiException ignored) {}
    }

    // ------------------------------------------------------------------
    // Scenario 30: basic publish/consume
    // ------------------------------------------------------------------

    @Test
    void publishAndConsume_withDurableConsumerGroup_deliversAndAcks() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> seen = new AtomicReference<>();

        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(channel)
                .groupName("g1")
                .consumerName("c1")
                .listener((m, ack) -> {
                    seen.set(new String(m.getPayload()));
                    ack.ack();
                    latch.countDown();
                })
                .build());

        broker.send(channel, "hello".getBytes(),
                MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "m-1"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen.get()).isEqualTo("hello");
        sub.cancel();
    }

    // ------------------------------------------------------------------
    // Scenario 31: native dedup via duplicate window
    // ------------------------------------------------------------------

    @Test
    void publishWithSameMsgId_isDeduplicatedByNativeWindow() throws Exception {
        String ch = "dedup-" + UUID.randomUUID();
        AtomicInteger received = new AtomicInteger(0);
        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("g").consumerName("c")
                .listener((m, ack) -> { received.incrementAndGet(); ack.ack(); })
                .build());
        try {
            MessageHeaders h = MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "dup-id-1");
            broker.send(ch, "hello".getBytes(), h);
            broker.send(ch, "hello".getBytes(), h);
            Thread.sleep(2000);
            assertThat(received.get()).isEqualTo(1);
        } finally {
            sub.cancel();
            try { jsm.deleteStream("test-" + ch); } catch (Exception ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Scenario 34: replay by start sequence
    // ------------------------------------------------------------------

    @Test
    void replayByStartSequence_replaysHistoricalMessages() throws Exception {
        String ch = "replay-seq-" + UUID.randomUUID();
        // Subscribe first to ensure stream creation, then cancel; replay uses ephemeral consumer
        Subscription seed = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("seed-g").consumerName("seed-c")
                .listener((m, ack) -> ack.ack())
                .build());
        seed.cancel();
        broker.send(ch, "m0".getBytes(), MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-0"));
        broker.send(ch, "m1".getBytes(), MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-1"));
        broker.send(ch, "m2".getBytes(), MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-2"));
        broker.send(ch, "m3".getBytes(), MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-3"));
        broker.send(ch, "m4".getBytes(), MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-4"));
        Thread.sleep(500);

        AtomicInteger seen = new AtomicInteger(0);
        // fromId="2" (seq 2) to toId="+" (no upper bound) -> sequences 2,3,4,5 = 4 messages
        long count = replayService.replay(ch, "2", "+", (m, ack) -> seen.incrementAndGet());
        assertThat(count).isGreaterThanOrEqualTo(4);
        assertThat(seen.get()).isGreaterThanOrEqualTo(4);
        try { jsm.deleteStream("test-" + ch); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Scenario 35: replay by start time (time range)
    // ------------------------------------------------------------------

    @Test
    void replayByStartTime_replaysWithinTimeRange() throws Exception {
        String ch = "replay-time-" + UUID.randomUUID();
        // Subscribe first to ensure stream creation, then cancel; replay uses ephemeral consumer
        Subscription seed = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("seed-g").consumerName("seed-c")
                .listener((m, ack) -> ack.ack())
                .build());
        seed.cancel();
        Instant t0 = Instant.now();
        // Publish 3 messages in the "before" window
        broker.send(ch, "a0".getBytes(), MessageHeaders.empty());
        Thread.sleep(100);
        broker.send(ch, "a1".getBytes(), MessageHeaders.empty());
        Thread.sleep(100);
        broker.send(ch, "a2".getBytes(), MessageHeaders.empty());
        Thread.sleep(500);
        Instant t1 = Instant.now();
        Thread.sleep(500);
        // Publish 2 messages "after" the range
        broker.send(ch, "b0".getBytes(), MessageHeaders.empty());
        broker.send(ch, "b1".getBytes(), MessageHeaders.empty());
        Thread.sleep(500);

        AtomicInteger seen = new AtomicInteger(0);
        long count = replayService.replay(ch, t0, t1, (m, ack) -> seen.incrementAndGet());
        assertThat(count).isEqualTo(3);
        assertThat(seen.get()).isEqualTo(3);
        try { jsm.deleteStream("test-" + ch); } catch (Exception ignored) {}
    }
}
