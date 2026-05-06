package dev.simplecore.simplix.messaging.broker.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionHealthTrackerTest {

    @Test
    void initiallyHealthy() {
        SubscriptionHealthTracker t = new SubscriptionHealthTracker("stream", "TEST");
        assertThat(t.isHealthy()).isTrue();
    }

    @Test
    void becomesUnhealthyAfterFiveConsecutiveErrors() {
        SubscriptionHealthTracker t = new SubscriptionHealthTracker("stream", "TEST");
        for (int i = 0; i < 5; i++) t.onError(new RuntimeException("boom"));
        assertThat(t.isHealthy()).isFalse();
    }

    @Test
    void resetsOnSuccess() {
        SubscriptionHealthTracker t = new SubscriptionHealthTracker("stream", "TEST");
        t.onError(new RuntimeException("boom"));
        t.recordSuccess();
        for (int i = 0; i < 4; i++) t.onError(new RuntimeException("boom"));
        assertThat(t.isHealthy()).isTrue();
    }
}
