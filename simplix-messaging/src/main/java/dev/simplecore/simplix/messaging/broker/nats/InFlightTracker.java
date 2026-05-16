package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.Message;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory map of in-flight NATS messages keyed by simplix messageId.
 *
 * <p>Used by {@code NatsBrokerStrategy.acknowledge(channel, group, messageId)}
 * to look up the underlying NATS message handle for delayed acknowledgement.
 */
public class InFlightTracker {

    private final ConcurrentHashMap<String, Message> messages = new ConcurrentHashMap<>();

    public void put(String messageId, Message m) { messages.put(messageId, m); }
    public Message get(String messageId) { return messages.get(messageId); }
    public void remove(String messageId) { messages.remove(messageId); }
    public int size() { return messages.size(); }
}
