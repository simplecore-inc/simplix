package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.core.Message;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalMessageSchedulerTest {

    private LocalBrokerStrategy broker;
    private LocalMessageScheduler scheduler;

    @BeforeEach
    void setUp() {
        broker = new LocalBrokerStrategy();
        broker.initialize();
        scheduler = new LocalMessageScheduler(broker);
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
        broker.shutdown();
    }

    @Test
    void deliversAfterDelay() {
        scheduler.publishDelayed(
                Message.<byte[]>builder().channel("ch").payload("x".getBytes()).build(),
                Duration.ofMillis(200));

        Awaitility.await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                        assertThat(broker.getPublishedMessages("ch")).hasSize(1));
    }

    @Test
    void cancelPreventsDelivery() {
        String id = scheduler.publishDelayed(
                Message.<byte[]>builder().channel("ch").payload("x".getBytes()).build(),
                Duration.ofMillis(500));
        boolean cancelled = scheduler.cancel(id);
        assertThat(cancelled).isTrue();

        Awaitility.await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() ->
                        assertThat(broker.getPublishedMessages("ch")).isEmpty());
    }
}
