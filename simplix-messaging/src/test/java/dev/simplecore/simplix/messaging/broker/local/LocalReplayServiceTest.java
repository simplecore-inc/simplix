package dev.simplecore.simplix.messaging.broker.local;

import dev.simplecore.simplix.messaging.core.MessageHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalReplayServiceTest {

    private LocalBrokerStrategy broker;
    private LocalReplayService replay;

    @BeforeEach
    void setUp() {
        broker = new LocalBrokerStrategy();
        broker.initialize();
        replay = new LocalReplayService(broker);
    }

    @Test
    void replaysAllPublishedMessagesById() {
        broker.send("ch", "a".getBytes(), MessageHeaders.empty());
        broker.send("ch", "b".getBytes(), MessageHeaders.empty());
        broker.send("ch", "c".getBytes(), MessageHeaders.empty());

        List<String> seen = new ArrayList<>();
        long count = replay.replay("ch", "0", "+", (msg, ack) -> seen.add(new String(msg.getPayload())));

        assertThat(count).isEqualTo(3);
        assertThat(seen).containsExactly("a", "b", "c");
    }
}
