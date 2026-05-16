package dev.simplecore.simplix.messaging.dedup;

import java.time.Duration;

/** Subscriber-side message deduplication. */
public interface IdempotencyStore {

    /** Returns true if first time seen, false if duplicate. */
    boolean tryAcquire(String channel, String groupName, String messageId);

    /** Effective deduplication TTL (informational). */
    Duration ttl();
}
