package dev.simplecore.simplix.messaging.dedup;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreContractTest {

    @Test
    void declares_tryAcquire() throws NoSuchMethodException {
        Method m = IdempotencyStore.class.getMethod("tryAcquire",
                String.class, String.class, String.class);
        assertThat(m.getReturnType()).isEqualTo(boolean.class);
    }

    @Test
    void declares_ttl() throws NoSuchMethodException {
        Method m = IdempotencyStore.class.getMethod("ttl");
        assertThat(m.getReturnType()).isEqualTo(Duration.class);
    }
}
