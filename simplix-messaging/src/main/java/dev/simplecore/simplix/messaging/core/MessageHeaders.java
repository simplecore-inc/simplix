package dev.simplecore.simplix.messaging.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Type-safe, immutable header map for messaging metadata.
 *
 * <p>Headers are stored as string key-value pairs and transmitted alongside the payload.
 * Standard header keys are defined as constants with {@code x-} prefix convention.
 */
public final class MessageHeaders {

    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String CONTENT_TYPE = "x-content-type";
    public static final String SOURCE = "x-source";
    public static final String REPLY_CHANNEL = "x-reply-channel";
    public static final String RETRY_COUNT = "x-retry-count";
    public static final String MESSAGE_ID = "x-message-id";
    public static final String TIMESTAMP = "x-timestamp";
    public static final String PARTITION_KEY = "x-partition-key";
    public static final String DEAD_LETTER_REASON = "x-dead-letter-reason";
    public static final String ORIGINAL_CHANNEL = "x-original-channel";
    public static final String DEAD_LETTERED_AT = "x-dead-lettered-at";

    private final Map<String, String> entries;

    private MessageHeaders(Map<String, String> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * Create empty headers.
     */
    public static MessageHeaders empty() {
        return new MessageHeaders(Map.of());
    }

    /**
     * Create headers from a map.
     */
    public static MessageHeaders of(Map<String, String> entries) {
        return new MessageHeaders(entries);
    }

    /**
     * Return a new instance with the given key-value pair added or replaced.
     */
    public MessageHeaders with(String key, String value) {
        var copy = new LinkedHashMap<>(this.entries);
        copy.put(key, value);
        return new MessageHeaders(copy);
    }

    /**
     * Return the value for the given key, if present.
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    public String correlationId() {
        return entries.get(CORRELATION_ID);
    }

    public String contentType() {
        return entries.get(CONTENT_TYPE);
    }

    public String source() {
        return entries.get(SOURCE);
    }

    public String replyChannel() {
        return entries.get(REPLY_CHANNEL);
    }

    public String retryCount() {
        return entries.get(RETRY_COUNT);
    }

    /**
     * Return all entries as an unmodifiable map.
     */
    public Map<String, String> toMap() {
        return entries;
    }

    /**
     * Return the number of header entries.
     */
    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public String toString() {
        return "MessageHeaders" + entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageHeaders that)) return false;
        return entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
