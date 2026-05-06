package dev.simplecore.simplix.messaging.integration;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.nats.NatsBrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for NATS JetStream scheduled message delivery.
 *
 * <p>Uses a short poll-interval (200ms) so scheduled deliveries occur within
 * a few seconds without requiring long wall-clock waits.
 */
@SpringBootTest(classes = NatsSchedulerIntegrationTest.TestApp.class,
        properties = {
                "simplix.messaging.broker=nats",
                "simplix.messaging.nats.servers=nats://app:apppass@localhost:4222",
                "simplix.messaging.nats.stream-prefix=test-",
                "simplix.messaging.nats.subject-prefix=test.",
                "simplix.messaging.nats.scheduler.poll-interval=200ms"
        })
@RequiresNats
class NatsSchedulerIntegrationTest {

    @SpringBootApplication(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    static class TestApp {}

    @Autowired NatsBrokerStrategy broker;
    @Autowired MessageScheduler scheduler;
    @Autowired JetStreamManagement jsm;

    // ------------------------------------------------------------------
    // Scenario 36: scheduled message is delivered after its delay
    // ------------------------------------------------------------------

    @Test
    void scheduledMessage_isDeliveredAfterDelay() throws Exception {
        String ch = "sched-deliver-" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("g").consumerName("c")
                .listener((m, ack) -> { latch.countDown(); ack.ack(); })
                .build());
        try {
            scheduler.publishDelayed(
                    Message.<byte[]>builder().channel(ch).payload("late".getBytes()).build(),
                    Duration.ofSeconds(1));
            assertThat(latch.await(8, TimeUnit.SECONDS)).isTrue();
        } finally {
            sub.cancel();
            try { jsm.deleteStream("test-" + ch); } catch (IOException | JetStreamApiException ignored) {}
        }
    }

    // ------------------------------------------------------------------
    // Scenario 37: scheduled message can be cancelled before delivery fires
    // ------------------------------------------------------------------

    @Test
    void scheduledMessage_canBeCancelledBeforeDelivery() throws Exception {
        String ch = "sched-cancel-" + UUID.randomUUID();
        AtomicInteger received = new AtomicInteger(0);
        Subscription sub = broker.subscribe(SubscribeRequest.builder()
                .channel(ch).groupName("g").consumerName("c")
                .listener((m, ack) -> { received.incrementAndGet(); ack.ack(); })
                .build());
        try {
            String id = scheduler.publishDelayed(
                    Message.<byte[]>builder().channel(ch).payload("late".getBytes()).build(),
                    Duration.ofSeconds(2));
            boolean cancelled = scheduler.cancel(id);
            assertThat(cancelled).isTrue();
            Thread.sleep(4000);
            assertThat(received.get()).isEqualTo(0);
        } finally {
            sub.cancel();
            try { jsm.deleteStream("test-" + ch); } catch (IOException | JetStreamApiException ignored) {}
        }
    }
}
