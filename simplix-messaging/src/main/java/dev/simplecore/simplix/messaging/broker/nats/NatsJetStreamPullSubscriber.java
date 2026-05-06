package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.broker.common.SubscriptionHealthTracker;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates JetStream pull subscriptions and manages their background polling loop.
 *
 * <p>On subscribe, ensures the stream and optional consumer group exist via
 * {@link NatsConsumerGroupManager}, then starts a single-threaded daemon poller
 * that calls {@link JetStreamSubscription#fetch(int, Duration)} in a loop and
 * dispatches each received message to the configured {@link dev.simplecore.simplix.messaging.core.MessageListener}.
 *
 * <p>If the listener throws, the underlying NATS message is NAK-ed so the broker
 * redelivers it up to the configured {@code maxDeliver} limit.
 */
@Slf4j
public class NatsJetStreamPullSubscriber {

    private final JetStream jetStream;
    private final NatsConsumerGroupManager groupManager;

    public NatsJetStreamPullSubscriber(JetStream jetStream, NatsConsumerGroupManager groupManager) {
        this.jetStream = jetStream;
        this.groupManager = groupManager;
    }

    /**
     * Subscribes to the given channel and starts a background polling loop.
     *
     * @param request the subscription parameters
     * @return an active {@link Subscription} handle
     * @throws IllegalStateException if the underlying JetStream subscribe call fails
     */
    public Subscription subscribe(SubscribeRequest request) {
        String streamName = groupManager.resolveStreamName(request.channel());
        String subject = groupManager.resolveSubject(request.channel());

        groupManager.ensureStream(request.channel());
        if (request.groupName() != null && !request.groupName().isEmpty()) {
            groupManager.ensureConsumerGroup(request.channel(), request.groupName());
        }

        String durableName = (request.groupName() == null || request.groupName().isEmpty())
                ? null : request.groupName();

        PullSubscribeOptions opts = PullSubscribeOptions.builder()
                .stream(streamName)
                .durable(durableName)
                .build();

        JetStreamSubscription jsSub;
        try {
            jsSub = jetStream.subscribe(subject, opts);
        } catch (JetStreamApiException | IOException e) {
            throw new IllegalStateException("Failed to subscribe to subject " + subject, e);
        }

        SubscriptionHealthTracker tracker = new SubscriptionHealthTracker(streamName, "PULL");
        InFlightTracker inflight = new InFlightTracker();
        AtomicBoolean active = new AtomicBoolean(true);
        ExecutorService poller = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nats-poll-" + request.channel());
            t.setDaemon(true);
            return t;
        });

        int batchSize = request.batchSize() > 0 ? request.batchSize() : 10;
        Duration pollTimeout = request.pollTimeout() != null ? request.pollTimeout() : Duration.ofSeconds(2);

        poller.submit(() -> pollLoop(jsSub, request, inflight, tracker, active, batchSize, pollTimeout));

        log.info("Started NATS pull subscription on stream='{}' subject='{}' group='{}' consumer='{}'",
                streamName, subject, request.groupName(), request.consumerName());

        return new NatsSubscription(request.channel(), request.groupName(),
                jsSub, poller, tracker, active);
    }

    private void pollLoop(JetStreamSubscription jsSub,
                          SubscribeRequest request,
                          InFlightTracker inflight,
                          SubscriptionHealthTracker tracker,
                          AtomicBoolean active,
                          int batchSize,
                          Duration pollTimeout) {
        while (active.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // fetch blocks for up to pollTimeout; checked exceptions vary across jnats versions
                // so we catch broadly to keep the loop alive on unexpected failures
                List<io.nats.client.Message> batch = jsSub.fetch(batchSize, pollTimeout);
                if (batch == null || batch.isEmpty()) {
                    continue;
                }
                for (io.nats.client.Message m : batch) {
                    if (!active.get() || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    try {
                        dispatch(m, request, inflight);
                        tracker.recordSuccess();
                    } catch (Exception e) {
                        log.error("Listener failed on subject '{}': {}", m.getSubject(), e.getMessage(), e);
                        try {
                            m.nak();
                        } catch (Exception nakEx) {
                            log.debug("Error during nak: {}", nakEx.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (!active.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                tracker.onError(e);
            }
        }
    }

    private void dispatch(io.nats.client.Message natsMsg,
                          SubscribeRequest request,
                          InFlightTracker inflight) {
        Map<String, String> headerMap = new LinkedHashMap<>();
        Headers natsHeaders = natsMsg.getHeaders();
        if (natsHeaders != null) {
            for (String key : natsHeaders.keySet()) {
                List<String> values = natsHeaders.get(key);
                if (values != null && !values.isEmpty()) {
                    headerMap.put(key, values.get(0));
                }
            }
        }

        byte[] payload = natsMsg.getData();
        if (payload == null) {
            payload = new byte[0];
        }

        long seq = natsMsg.metaData() != null ? natsMsg.metaData().streamSequence() : 0L;
        String streamName = groupManager.resolveStreamName(request.channel());
        String recordId = streamName + "-" + seq;
        String messageId = headerMap.getOrDefault(MessageHeaders.MESSAGE_ID, recordId);

        Message<byte[]> message = Message.<byte[]>builder()
                .messageId(messageId)
                .channel(request.channel())
                .payload(payload)
                .headers(MessageHeaders.of(headerMap))
                .build();

        inflight.put(messageId, natsMsg);
        NatsMessageAcknowledgment ack = new NatsMessageAcknowledgment(natsMsg, inflight, messageId);
        request.listener().onMessage(message, ack);
    }
}
