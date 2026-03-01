package dev.simplecore.simplix.messaging.core;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * Default publisher implementation that delegates to the active {@link BrokerStrategy}.
 *
 * <p>Handles payload serialization to bytes before passing to the broker.
 * If the payload is already {@code byte[]}, it is used directly.
 * If the payload is a {@code String}, it is UTF-8 encoded.
 * Otherwise, an {@link IllegalArgumentException} is thrown.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultMessagePublisher implements MessagePublisher {

    private final BrokerStrategy brokerStrategy;

    @Override
    public PublishResult publish(Message<?> message) {
        byte[] payload = resolvePayload(message);
        MessageHeaders headers = enrichHeaders(message);
        return brokerStrategy.send(message.getChannel(), payload, headers);
    }

    @Override
    public CompletableFuture<PublishResult> publishAsync(Message<?> message) {
        return CompletableFuture.supplyAsync(() -> publish(message));
    }

    @Override
    public boolean isAvailable() {
        return brokerStrategy.isReady();
    }

    private byte[] resolvePayload(Message<?> message) {
        Object payload = message.getPayload();
        if (payload == null) {
            return new byte[0];
        }
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        if (payload instanceof String str) {
            return str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException(
                "Unsupported payload type: " + payload.getClass().getName()
                + ". Use Message.ofBytes() or Message.ofProtobuf() for binary payloads.");
    }

    private MessageHeaders enrichHeaders(Message<?> message) {
        MessageHeaders headers = message.getHeaders();
        if (headers.get(MessageHeaders.MESSAGE_ID).isEmpty()) {
            headers = headers.with(MessageHeaders.MESSAGE_ID, message.getMessageId());
        }
        if (headers.get(MessageHeaders.TIMESTAMP).isEmpty()) {
            headers = headers.with(MessageHeaders.TIMESTAMP, message.getTimestamp().toString());
        }
        return headers;
    }
}
