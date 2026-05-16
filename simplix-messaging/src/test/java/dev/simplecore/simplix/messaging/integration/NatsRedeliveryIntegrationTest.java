package dev.simplecore.simplix.messaging.integration;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.nats.NatsBrokerStrategy;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NATS JetStream redelivery behavior.
 *
 * <p>Uses a short ack-wait (2s) and max-deliver=3 to exercise redelivery and
 * message exhaustion scenarios within a reasonable wall-clock budget.
 */
@SpringBootTest(classes = NatsRedeliveryIntegrationTest.TestApp.class,
        properties = {
                "simplix.messaging.broker=nats",
                "simplix.messaging.nats.servers=nats://app:apppass@localhost:4222",
                "simplix.messaging.nats.stream-prefix=test-",
                "simplix.messaging.nats.subject-prefix=test.",
                "simplix.messaging.nats.ack-wait=2s",
                "simplix.messaging.nats.max-deliver=3"
        })
@RequiresNats
class NatsRedeliveryIntegrationTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    static class TestApp {}

    @Autowired NatsBrokerStrategy broker;
    @Autowired JetStreamManagement jsm;

    // ------------------------------------------------------------------
    // Scenario 32: unacked message is redelivered after ack-wait
    // ------------------------------------------------------------------

    @Test
    void unackedMessage_isRedeliveredAfterAckWait() throws Exception {
        String ch = "redelivery-" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger attempts = new AtomicInteger(0);
        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("g").consumerName("c")
                .listener((m, ack) -> {
                    int n = attempts.incrementAndGet();
                    latch.countDown();
                    if (n == 1) {
                        // Intentionally do NOT ack — JetStream redelivers after ack-wait
                        return;
                    }
                    ack.ack();
                })
                .build());
        try {
            broker.send(ch, "x".getBytes(),
                    MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-1"));
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts.get()).isGreaterThanOrEqualTo(2);
        } finally {
            sub.cancel();
            try { jsm.deleteStream("test-" + ch); } catch (IOException | JetStreamApiException ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Scenario 33: message exhausting max-deliver is dropped from consumer
    // ------------------------------------------------------------------

    @Test
    void messageExceedingMaxDeliver_isDroppedFromConsumer() throws Exception {
        // ack-wait=2s, max-deliver=3 — after 3 attempts NATS stops redelivering
        String ch = "dlq-" + UUID.randomUUID();
        AtomicInteger attempts = new AtomicInteger(0);
        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("g").consumerName("c")
                .listener((m, ack) -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("intentional failure");
                })
                .build());
        try {
            broker.send(ch, "x".getBytes(),
                    MessageHeaders.empty().with(MessageHeaders.MESSAGE_ID, "id-1"));
            // Wait long enough for max-deliver (3) attempts at ack-wait (2s) each, plus margin
            Thread.sleep(10000);
            int finalAttempts = attempts.get();
            // With max-deliver=3 the consumer receives the message at most 3 times
            assertThat(finalAttempts).isBetween(2, 4);
            // Confirm no further attempts are made after max-deliver is exhausted
            Thread.sleep(3000);
            assertThat(attempts.get()).isEqualTo(finalAttempts);
        } finally {
            sub.cancel();
            try { jsm.deleteStream("test-" + ch); } catch (IOException | JetStreamApiException ignored) {}
        }
    }
}
