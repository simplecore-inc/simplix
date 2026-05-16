package dev.simplecore.simplix.messaging.replay;

import dev.simplecore.simplix.messaging.core.MessageListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayServiceContractTest {

    @Test
    void interface_declares_replay_byId() throws NoSuchMethodException {
        Method m = ReplayService.class.getMethod("replay",
                String.class, String.class, String.class, MessageListener.class);
        assertThat(m.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void interface_declares_replay_byTime() throws NoSuchMethodException {
        Method m = ReplayService.class.getMethod("replay",
                String.class, Instant.class, Instant.class, MessageListener.class);
        assertThat(m.getReturnType()).isEqualTo(long.class);
    }

    @Test
    void interface_declares_replay_paginated() throws NoSuchMethodException {
        Method m = ReplayService.class.getMethod("replayPaginated",
                String.class, String.class, String.class, MessageListener.class, int.class);
        assertThat(m.getReturnType()).isEqualTo(long.class);
    }
}
