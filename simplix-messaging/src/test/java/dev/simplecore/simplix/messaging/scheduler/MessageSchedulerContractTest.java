package dev.simplecore.simplix.messaging.scheduler;

import dev.simplecore.simplix.messaging.core.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSchedulerContractTest {

    @Test
    void declares_publishDelayed() throws NoSuchMethodException {
        Method m = MessageScheduler.class.getMethod("publishDelayed", Message.class, Duration.class);
        assertThat(m.getReturnType()).isEqualTo(String.class);
    }

    @Test
    void declares_cancel() throws NoSuchMethodException {
        Method m = MessageScheduler.class.getMethod("cancel", String.class);
        assertThat(m.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void declares_lifecycle() throws NoSuchMethodException {
        assertThat(MessageScheduler.class.getMethod("start")).isNotNull();
        assertThat(MessageScheduler.class.getMethod("stop")).isNotNull();
    }
}
