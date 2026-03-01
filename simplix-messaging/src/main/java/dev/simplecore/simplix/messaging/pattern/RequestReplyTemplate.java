package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Request-Reply pattern implementation using correlation IDs.
 *
 * <p>Publishes a request message with a unique correlation ID and reply channel,
 * then waits for a response with the matching correlation ID on the reply channel.
 *
 * <p>Usage example:
 * <pre>{@code
 * RequestReplyTemplate template = new RequestReplyTemplate(broker, "reply-channel", "my-group");
 * Message<byte[]> request = Message.ofBytes("request-channel", payload);
 *
 * CompletableFuture<Message<byte[]>> reply = template.sendAndReceive(request, Duration.ofSeconds(10));
 * Message<byte[]> response = reply.get();
 * }</pre>
 */
@Slf4j
public class RequestReplyTemplate {

    private final BrokerStrategy brokerStrategy;
    private final String replyChannel;
    private final String replyGroup;
    private final ConcurrentHashMap<String, CompletableFuture<Message<byte[]>>> pendingReplies;
    private volatile Subscription replySubscription;

    /**
     * Create a request-reply template.
     *
     * @param brokerStrategy the broker to use for sending and receiving
     * @param replyChannel   the channel to listen for replies on
     * @param replyGroup     the consumer group for reply consumption
     */
    public RequestReplyTemplate(BrokerStrategy brokerStrategy, String replyChannel, String replyGroup) {
        this.brokerStrategy = brokerStrategy;
        this.replyChannel = replyChannel;
        this.replyGroup = replyGroup;
        this.pendingReplies = new ConcurrentHashMap<>();
    }

    /**
     * Send a request and wait for a reply with a timeout.
     *
     * <p>The request message is enriched with a correlation ID and reply channel header.
     * A temporary listener is set up to capture the reply matching the correlation ID.
     *
     * @param request the request message
     * @param timeout maximum time to wait for a reply
     * @return a future that completes with the reply message, or times out
     */
    public CompletableFuture<Message<byte[]>> sendAndReceive(Message<?> request, Duration timeout) {
        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<Message<byte[]>> future = new CompletableFuture<>();

        pendingReplies.put(correlationId, future);
        ensureReplySubscription();

        // Enrich request with correlation ID and reply channel
        MessageHeaders enrichedHeaders = request.getHeaders()
                .with(MessageHeaders.CORRELATION_ID, correlationId)
                .with(MessageHeaders.REPLY_CHANNEL, replyChannel);

        byte[] payload = resolvePayload(request);
        brokerStrategy.send(request.getChannel(), payload, enrichedHeaders);

        log.debug("Sent request to '{}' with correlationId='{}', waiting for reply on '{}'",
                request.getChannel(), correlationId, replyChannel);

        // Apply timeout and cleanup
        return future
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> {
                    pendingReplies.remove(correlationId);
                    if (error != null) {
                        log.debug("Request-reply timed out or failed for correlationId='{}'", correlationId);
                    }
                });
    }

    /**
     * Shut down the reply listener and cancel all pending requests.
     */
    public void shutdown() {
        if (replySubscription != null) {
            replySubscription.cancel();
            replySubscription = null;
        }
        int pendingCount = pendingReplies.size();
        pendingReplies.forEach((id, future) -> future.cancel(false));
        pendingReplies.clear();
        log.info("RequestReplyTemplate shut down, cancelled {} pending replies", pendingCount);
    }

    private synchronized void ensureReplySubscription() {
        if (replySubscription != null && replySubscription.isActive()) {
            return;
        }

        String consumerName = "reply-" + UUID.randomUUID().toString().substring(0, 8);
        brokerStrategy.ensureConsumerGroup(replyChannel, replyGroup);

        SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                .channel(replyChannel)
                .groupName(replyGroup)
                .consumerName(consumerName)
                .listener((message, ack) -> {
                    String correlationId = message.getHeaders().correlationId();
                    if (correlationId != null) {
                        CompletableFuture<Message<byte[]>> pending = pendingReplies.remove(correlationId);
                        if (pending != null) {
                            pending.complete(message);
                            log.debug("Reply received for correlationId='{}'", correlationId);
                        } else {
                            log.debug("No pending request for correlationId='{}', discarding reply", correlationId);
                        }
                    }
                    ack.ack();
                })
                .build();

        replySubscription = brokerStrategy.subscribe(subscribeRequest);
        log.info("Started reply listener on channel '{}' [group={}]", replyChannel, replyGroup);
    }

    private byte[] resolvePayload(Message<?> message) {
        Object payload = message.getPayload();
        if (payload == null) return new byte[0];
        if (payload instanceof byte[] bytes) return bytes;
        if (payload instanceof String str) return str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        throw new IllegalArgumentException("Unsupported payload type: " + payload.getClass().getName());
    }
}
