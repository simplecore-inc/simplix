package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.dedup.IdempotencyStore;

import java.time.Duration;

/**
 * Pass-through implementation of {@link IdempotencyStore} for the NATS broker.
 *
 * <p>NATS JetStream enforces deduplication at publish time using the
 * {@code Nats-Msg-Id} header and the stream's configured
 * {@code duplicate_window}. Subscriber-side dedup is therefore unnecessary
 * — this store always returns {@code true}.
 */
public class NatsNativeIdempotencyStore implements IdempotencyStore {

    private final Duration duplicateWindow;

    public NatsNativeIdempotencyStore(Duration duplicateWindow) {
        this.duplicateWindow = duplicateWindow;
    }

    @Override
    public boolean tryAcquire(String channel, String groupName, String messageId) {
        return true;
    }

    @Override
    public Duration ttl() {
        return duplicateWindow;
    }
}
