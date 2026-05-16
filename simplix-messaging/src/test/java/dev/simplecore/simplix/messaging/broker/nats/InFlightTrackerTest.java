package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InFlightTrackerTest {

    private InFlightTracker tracker;
    private Message natsMsg;

    @BeforeEach
    void setUp() {
        tracker = new InFlightTracker();
        natsMsg = mock(Message.class);
    }

    @Test
    void put_then_get_returnsMessage() {
        tracker.put("m1", natsMsg);
        assertThat(tracker.get("m1")).isSameAs(natsMsg);
    }

    @Test
    void remove_makesGetReturnNull() {
        tracker.put("m1", natsMsg);
        tracker.remove("m1");
        assertThat(tracker.get("m1")).isNull();
    }

    @Test
    void size_reflectsCurrentEntries() {
        assertThat(tracker.size()).isEqualTo(0);
        tracker.put("m1", natsMsg);
        assertThat(tracker.size()).isEqualTo(1);
        tracker.put("m2", mock(Message.class));
        assertThat(tracker.size()).isEqualTo(2);
        tracker.remove("m1");
        assertThat(tracker.size()).isEqualTo(1);
    }
}
