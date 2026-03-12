package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.ByteRecord;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes messages from Redis Streams using {@link StreamMessageListenerContainer}.
 *
 * <p>Supports two payload encoding strategies:
 * <ul>
 *   <li>{@link PayloadEncoding#BASE64} - decodes Base64 string from payload field (default)</li>
 *   <li>{@link PayloadEncoding#RAW} - reads raw binary bytes from payload field directly</li>
 * </ul>
 *
 * <p>Supports consumer group based consumption with PEL (Pending Entries List)
 * self-recovery on startup and XCLAIM-based reclamation of stuck messages
 * from dead consumers.
 */
@Slf4j
public class RedisStreamSubscriber {

    private static final String PAYLOAD_FIELD = "payload";

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration defaultPollTimeout;
    private final int defaultBatchSize;
    private final PayloadEncoding payloadEncoding;

    public RedisStreamSubscriber(StringRedisTemplate redisTemplate, String keyPrefix,
                                 Duration defaultPollTimeout, int defaultBatchSize,
                                 PayloadEncoding payloadEncoding) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.defaultPollTimeout = defaultPollTimeout;
        this.defaultBatchSize = defaultBatchSize;
        this.payloadEncoding = payloadEncoding;
    }

    public RedisStreamSubscriber(StringRedisTemplate redisTemplate, String keyPrefix,
                                 Duration defaultPollTimeout, int defaultBatchSize) {
        this(redisTemplate, keyPrefix, defaultPollTimeout, defaultBatchSize, PayloadEncoding.BASE64);
    }

    public RedisStreamSubscriber(StringRedisTemplate redisTemplate, String keyPrefix) {
        this(redisTemplate, keyPrefix, Duration.ofSeconds(2), 10, PayloadEncoding.BASE64);
    }

    /**
     * Subscribe to a channel using a consumer group.
     *
     * @param request the subscription parameters
     * @return a handle to manage the subscription lifecycle
     */
    public Subscription subscribe(SubscribeRequest request) {
        if (payloadEncoding == PayloadEncoding.RAW) {
            return subscribeRaw(request);
        }
        return subscribeBase64(request);
    }

    /**
     * Recover pending messages (PEL) that were delivered but not acknowledged.
     *
     * @param request the subscription parameters (channel, group, consumer, listener)
     */
    public void recoverPendingMessages(SubscribeRequest request) {
        if (payloadEncoding == PayloadEncoding.RAW) {
            recoverPendingMessagesRaw(request);
        } else {
            recoverPendingMessagesBase64(request);
        }
    }

    /**
     * Auto-claim stuck messages from dead consumers using XPENDING + XCLAIM.
     *
     * @param request     the subscription parameters
     * @param minIdleTime minimum idle time before a message can be claimed
     */
    public void autoClaimStuckMessages(SubscribeRequest request, Duration minIdleTime) {
        if (payloadEncoding == PayloadEncoding.RAW) {
            autoClaimStuckMessagesRaw(request, minIdleTime);
        } else {
            autoClaimStuckMessagesBase64(request, minIdleTime);
        }
    }

    // ---------------------------------------------------------------
    // BASE64 mode implementation
    // ---------------------------------------------------------------

    private Subscription subscribeBase64(SubscribeRequest request) {
        String streamKey = resolveKey(request.channel());
        Duration pollTimeout = request.pollTimeout() != null ? request.pollTimeout() : defaultPollTimeout;
        int batchSize = request.batchSize() > 0 ? request.batchSize() : defaultBatchSize;

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(pollTimeout)
                        .batchSize(batchSize)
                        .serializer(redisTemplate.getStringSerializer())
                        .errorHandler(e -> log.warn("Stream polling error [stream={}, encoding=BASE64]: {}",
                                streamKey, e.getMessage()))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(
                        redisTemplate.getConnectionFactory(), options);

        StreamListener<String, MapRecord<String, String, String>> streamListener =
                record -> processBase64Record(record, request);

        Consumer consumer = Consumer.from(request.groupName(), request.consumerName());
        org.springframework.data.redis.stream.Subscription springSubscription = container.receive(
                consumer,
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                streamListener);

        container.start();
        log.info("Started subscription on stream '{}' [group={}, consumer={}, encoding=BASE64]",
                streamKey, request.groupName(), request.consumerName());

        return new RedisSubscription(request.channel(), request.groupName(), container, springSubscription);
    }

    private void processBase64Record(MapRecord<String, String, String> record, SubscribeRequest request) {
        Map<String, String> fields = record.getValue();

        String encodedPayload = fields.get(PAYLOAD_FIELD);
        byte[] payload = encodedPayload != null
                ? Base64.getDecoder().decode(encodedPayload)
                : new byte[0];

        Map<String, String> headerMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                headerMap.put(entry.getKey(), entry.getValue());
            }
        }

        dispatchMessage(payload, headerMap, record.getId().getValue(), request);
    }

    @SuppressWarnings("unchecked")
    private void recoverPendingMessagesBase64(SubscribeRequest request) {
        String streamKey = resolveKey(request.channel());
        Consumer consumer = Consumer.from(request.groupName(), request.consumerName());

        try {
            List<MapRecord<String, Object, Object>> pending = redisTemplate.opsForStream()
                    .read(consumer, StreamOffset.create(streamKey, ReadOffset.from("0")));

            if (pending == null || pending.isEmpty()) {
                log.debug("No pending messages to recover for stream '{}' [group={}, consumer={}]",
                        streamKey, request.groupName(), request.consumerName());
                return;
            }

            log.info("Recovering {} pending message(s) from stream '{}' [group={}, consumer={}]",
                    pending.size(), streamKey, request.groupName(), request.consumerName());

            for (MapRecord<String, Object, Object> record : pending) {
                try {
                    Map<String, String> fields = new LinkedHashMap<>();
                    record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));

                    String encodedPayload = fields.get(PAYLOAD_FIELD);
                    byte[] payload = encodedPayload != null
                            ? Base64.getDecoder().decode(encodedPayload)
                            : new byte[0];

                    Map<String, String> headerMap = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : fields.entrySet()) {
                        if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                            headerMap.put(entry.getKey(), entry.getValue());
                        }
                    }

                    TrackingAcknowledgment tracking = new TrackingAcknowledgment();
                    dispatchMessageWithAck(payload, headerMap, record.getId().getValue(),
                            request, tracking);
                    if (tracking.shouldAcknowledge()) {
                        redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(), record.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to recover pending message '{}' from stream '{}': {}",
                            record.getId(), streamKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("PEL recovery failed for stream '{}' [group={}, consumer={}]: {}",
                    streamKey, request.groupName(), request.consumerName(), e.getMessage());
        }
    }

    private void autoClaimStuckMessagesBase64(SubscribeRequest request, Duration minIdleTime) {
        String streamKey = resolveKey(request.channel());

        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(streamKey, request.groupName(), Range.unbounded(), 100L);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            List<RecordId> stuckIds = pendingMessages.stream()
                    .filter(pm -> pm.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0)
                    .map(PendingMessage::getId)
                    .toList();

            if (stuckIds.isEmpty()) {
                return;
            }

            List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream()
                    .claim(streamKey, request.groupName(), request.consumerName(),
                            minIdleTime, stuckIds.toArray(RecordId[]::new));

            if (claimed == null || claimed.isEmpty()) {
                return;
            }

            log.info("Auto-claimed {} stuck message(s) from stream '{}' [group={}]",
                    claimed.size(), streamKey, request.groupName());

            for (MapRecord<String, Object, Object> record : claimed) {
                try {
                    Map<String, String> fields = new LinkedHashMap<>();
                    record.getValue().forEach((k, v) -> fields.put(String.valueOf(k), String.valueOf(v)));

                    String encodedPayload = fields.get(PAYLOAD_FIELD);
                    byte[] payload = encodedPayload != null
                            ? Base64.getDecoder().decode(encodedPayload)
                            : new byte[0];

                    Map<String, String> headerMap = new LinkedHashMap<>();
                    for (Map.Entry<String, String> entry : fields.entrySet()) {
                        if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                            headerMap.put(entry.getKey(), entry.getValue());
                        }
                    }

                    TrackingAcknowledgment tracking = new TrackingAcknowledgment();
                    dispatchMessageWithAck(payload, headerMap, record.getId().getValue(),
                            request, tracking);
                    if (tracking.shouldAcknowledge()) {
                        redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(), record.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to process auto-claimed message '{}' from stream '{}': {}",
                            record.getId(), streamKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Auto-claim failed for stream '{}' [group={}]: {}",
                    streamKey, request.groupName(), e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // RAW mode implementation
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Subscription subscribeRaw(SubscribeRequest request) {
        String streamKey = resolveKey(request.channel());
        Duration pollTimeout = request.pollTimeout() != null ? request.pollTimeout() : defaultPollTimeout;
        int batchSize = request.batchSize() > 0 ? request.batchSize() : defaultBatchSize;

        // hashKeySerializer(string()) guarantees hash keys are String at runtime,
        // but the builder infers HK as Object. The cast is safe.
        StreamMessageListenerContainerOptions<String, MapRecord<String, String, byte[]>> options =
                (StreamMessageListenerContainerOptions<String, MapRecord<String, String, byte[]>>)
                (StreamMessageListenerContainerOptions<?, ?>)
                StreamMessageListenerContainerOptions.builder()
                        .keySerializer(RedisSerializer.string())
                        .hashKeySerializer(RedisSerializer.string())
                        .hashValueSerializer(RedisSerializer.byteArray())
                        .pollTimeout(pollTimeout)
                        .batchSize(batchSize)
                        .errorHandler(e -> log.warn("Stream polling error [stream={}, encoding=RAW]: {}",
                                streamKey, e.getMessage()))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, byte[]>> container =
                StreamMessageListenerContainer.create(
                        redisTemplate.getConnectionFactory(), options);

        StreamListener<String, MapRecord<String, String, byte[]>> streamListener =
                record -> processRawRecord(record, request);

        Consumer consumer = Consumer.from(request.groupName(), request.consumerName());
        org.springframework.data.redis.stream.Subscription springSubscription = container.receive(
                consumer,
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                streamListener);

        container.start();
        log.info("Started subscription on stream '{}' [group={}, consumer={}, encoding=RAW]",
                streamKey, request.groupName(), request.consumerName());

        return new RedisSubscription(request.channel(), request.groupName(), container, springSubscription);
    }

    private void processRawRecord(MapRecord<String, String, byte[]> record, SubscribeRequest request) {
        Map<String, byte[]> fields = record.getValue();

        byte[] payload = fields.getOrDefault(PAYLOAD_FIELD, new byte[0]);

        Map<String, String> headerMap = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : fields.entrySet()) {
            if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                headerMap.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
            }
        }

        dispatchMessage(payload, headerMap, record.getId().getValue(), request);
    }

    private void recoverPendingMessagesRaw(SubscribeRequest request) {
        String streamKey = resolveKey(request.channel());
        byte[] streamKeyBytes = streamKey.getBytes(StandardCharsets.UTF_8);

        try {
            List<ByteRecord> pending = redisTemplate.execute((RedisCallback<List<ByteRecord>>) connection ->
                    connection.streamCommands().xReadGroup(
                            Consumer.from(request.groupName(), request.consumerName()),
                            StreamReadOptions.empty().count(100),
                            StreamOffset.create(streamKeyBytes, ReadOffset.from("0"))
                    )
            );

            if (pending == null || pending.isEmpty()) {
                log.debug("No pending messages to recover for stream '{}' [group={}, consumer={}]",
                        streamKey, request.groupName(), request.consumerName());
                return;
            }

            log.info("Recovering {} pending message(s) from stream '{}' [group={}, consumer={}]",
                    pending.size(), streamKey, request.groupName(), request.consumerName());

            for (ByteRecord record : pending) {
                try {
                    TrackingAcknowledgment tracking = new TrackingAcknowledgment();
                    extractAndDispatchByteRecord(record, request, tracking);
                    if (tracking.shouldAcknowledge()) {
                        redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(), record.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to recover pending message '{}' from stream '{}': {}",
                            record.getId(), streamKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("PEL recovery failed for stream '{}' [group={}, consumer={}]: {}",
                    streamKey, request.groupName(), request.consumerName(), e.getMessage());
        }
    }

    private void autoClaimStuckMessagesRaw(SubscribeRequest request, Duration minIdleTime) {
        String streamKey = resolveKey(request.channel());

        try {
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(streamKey, request.groupName(), Range.unbounded(), 100L);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            List<RecordId> stuckIds = pendingMessages.stream()
                    .filter(pm -> pm.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0)
                    .map(PendingMessage::getId)
                    .toList();

            if (stuckIds.isEmpty()) {
                return;
            }

            byte[] streamKeyBytes = streamKey.getBytes(StandardCharsets.UTF_8);
            List<ByteRecord> claimed = redisTemplate.execute((RedisCallback<List<ByteRecord>>) connection ->
                    connection.streamCommands().xClaim(
                            streamKeyBytes,
                            request.groupName(),
                            request.consumerName(),
                            minIdleTime,
                            stuckIds.toArray(RecordId[]::new)
                    )
            );

            if (claimed == null || claimed.isEmpty()) {
                return;
            }

            log.info("Auto-claimed {} stuck message(s) from stream '{}' [group={}]",
                    claimed.size(), streamKey, request.groupName());

            for (ByteRecord record : claimed) {
                try {
                    TrackingAcknowledgment tracking = new TrackingAcknowledgment();
                    extractAndDispatchByteRecord(record, request, tracking);
                    if (tracking.shouldAcknowledge()) {
                        redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(), record.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to process auto-claimed message '{}' from stream '{}': {}",
                            record.getId(), streamKey, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Auto-claim failed for stream '{}' [group={}]: {}",
                    streamKey, request.groupName(), e.getMessage());
        }
    }

    private void extractAndDispatchByteRecord(ByteRecord record, SubscribeRequest request,
                                               MessageAcknowledgment ack) {
        Map<byte[], byte[]> rawFields = record.getValue();
        byte[] payloadKey = PAYLOAD_FIELD.getBytes(StandardCharsets.UTF_8);

        byte[] payload = new byte[0];
        Map<String, String> headerMap = new LinkedHashMap<>();

        for (Map.Entry<byte[], byte[]> entry : rawFields.entrySet()) {
            String fieldName = new String(entry.getKey(), StandardCharsets.UTF_8);
            if (PAYLOAD_FIELD.equals(fieldName)) {
                payload = entry.getValue();
            } else {
                headerMap.put(fieldName, new String(entry.getValue(), StandardCharsets.UTF_8));
            }
        }

        dispatchMessageWithAck(payload, headerMap, record.getId().getValue(),
                request, ack);
    }

    // ---------------------------------------------------------------
    // Common dispatch logic
    // ---------------------------------------------------------------

    private void dispatchMessage(byte[] payload, Map<String, String> headerMap,
                                  String recordId, SubscribeRequest request) {
        String streamKey = resolveKey(request.channel());
        MessageAcknowledgment ack = new RedisMessageAcknowledgment(
                redisTemplate, streamKey, request.groupName(), recordId);
        dispatchMessageWithAck(payload, headerMap, recordId, request, ack);
    }

    private void dispatchMessageWithAck(byte[] payload, Map<String, String> headerMap,
                                         String recordId, SubscribeRequest request,
                                         MessageAcknowledgment ack) {
        MessageHeaders headers = MessageHeaders.of(headerMap);
        String messageId = headers.get(MessageHeaders.MESSAGE_ID).orElse(recordId);

        Message<byte[]> message = Message.<byte[]>builder()
                .messageId(messageId)
                .channel(request.channel())
                .payload(payload)
                .headers(headers)
                .build();

        request.listener().onMessage(message, ack);
    }

    private String resolveKey(String channel) {
        return keyPrefix + channel;
    }

    // ---------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------

    /**
     * {@link MessageAcknowledgment} that tracks the listener's ack/nack/reject decision
     * without performing any Redis operations. Used during PEL recovery and auto-claim
     * to determine whether the caller should XACK the message.
     */
    private static class TrackingAcknowledgment implements MessageAcknowledgment {

        private boolean acknowledged = false;
        private boolean rejected = false;

        @Override
        public void ack() {
            acknowledged = true;
        }

        @Override
        public void nack(boolean requeue) {
            if (!requeue) {
                rejected = true;
            }
        }

        @Override
        public void reject(String reason) {
            rejected = true;
        }

        boolean shouldAcknowledge() {
            return acknowledged || rejected;
        }
    }

    /**
     * {@link MessageAcknowledgment} implementation backed by Redis XACK.
     */
    private record RedisMessageAcknowledgment(
            StringRedisTemplate redisTemplate,
            String streamKey,
            String groupName,
            String recordId
    ) implements MessageAcknowledgment {

        @Override
        public void ack() {
            redisTemplate.opsForStream().acknowledge(streamKey, groupName, recordId);
        }

        @Override
        public void nack(boolean requeue) {
            log.warn("NACK for record '{}' on stream '{}' [group={}, requeue={}]. "
                    + "Message remains in PEL for retry.",
                    recordId, streamKey, groupName, requeue);
        }

        @Override
        public void reject(String reason) {
            log.warn("Rejecting record '{}' on stream '{}' [group={}, reason={}]",
                    recordId, streamKey, groupName, reason);
            redisTemplate.opsForStream().acknowledge(streamKey, groupName, recordId);
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(RedisMessageAcknowledgment.class);
    }

    /**
     * {@link Subscription} implementation wrapping a {@link StreamMessageListenerContainer}.
     * Uses raw type to support both BASE64 and RAW container types.
     */
    private static class RedisSubscription implements Subscription {

        private final String channel;
        private final String groupName;
        @SuppressWarnings("rawtypes")
        private final StreamMessageListenerContainer container;
        private final org.springframework.data.redis.stream.Subscription springSubscription;
        private final AtomicBoolean active = new AtomicBoolean(true);

        RedisSubscription(String channel, String groupName,
                          StreamMessageListenerContainer<?, ?> container,
                          org.springframework.data.redis.stream.Subscription springSubscription) {
            this.channel = channel;
            this.groupName = groupName;
            this.container = container;
            this.springSubscription = springSubscription;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public String groupName() {
            return groupName;
        }

        /**
         * Returns {@code true} only when both the container is running AND
         * the internal subscription task is still alive. When Redis connection
         * errors trigger cancelOnError, the Spring subscription becomes inactive
         * while the container may still report as running.
         */
        @Override
        public boolean isActive() {
            return active.get() && container.isRunning() && springSubscription.isActive();
        }

        @Override
        public void cancel() {
            if (active.compareAndSet(true, false)) {
                container.stop();
                log.info("Cancelled subscription on channel '{}' [group={}]", channel, groupName);
            }
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(RedisSubscription.class);
    }
}
