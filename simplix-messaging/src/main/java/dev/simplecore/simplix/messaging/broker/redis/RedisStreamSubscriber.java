package dev.simplecore.simplix.messaging.broker.redis;

import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final Logger MESSAGE_TRACE = LoggerFactory.getLogger("MESSAGE_TRACE");
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

        SubscriptionHealthTracker healthTracker = new SubscriptionHealthTracker(streamKey, "BASE64");

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(pollTimeout)
                        .batchSize(batchSize)
                        .serializer(redisTemplate.getStringSerializer())
                        .errorHandler(healthTracker::onError)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(
                        redisTemplate.getConnectionFactory(), options);

        StreamListener<String, MapRecord<String, String, String>> streamListener =
                record -> {
                    processBase64Record(record, request);
                    healthTracker.recordSuccess();
                };

        Consumer consumer = Consumer.from(request.groupName(), request.consumerName());
        org.springframework.data.redis.stream.Subscription springSubscription = container.register(
                StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
                        .consumer(consumer)
                        .autoAcknowledge(false)
                        .cancelOnError(t -> false)
                        .build(),
                streamListener);

        container.start();
        log.info("Started subscription on stream '{}' [group={}, consumer={}, encoding=BASE64]",
                streamKey, request.groupName(), request.consumerName());

        return new RedisSubscription(request.channel(), request.groupName(), container, springSubscription, healthTracker);
    }

    private void processBase64Record(MapRecord<String, String, String> record, SubscribeRequest request) {
        log.debug("Subscriber received record [id={}, channel={}]", record.getId(), request.channel());
        try {
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
        } catch (Exception e) {
            log.error("Failed to process record [id={}, channel={}]: {}",
                    record.getId(), request.channel(), e.getMessage(), e);
            // XACK unrecoverable messages to prevent infinite PEL retry
            String streamKey = resolveKey(request.channel());
            redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(),
                    record.getId().getValue());
        }
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

                    MessageAcknowledgment ack = new RedisMessageAcknowledgment(
                            redisTemplate, streamKey, request.groupName(),
                            record.getId().getValue());
                    dispatchMessageWithAck(payload, headerMap, record.getId().getValue(),
                            request, ack);
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

                    MessageAcknowledgment ack = new RedisMessageAcknowledgment(
                            redisTemplate, streamKey, request.groupName(),
                            record.getId().getValue());
                    dispatchMessageWithAck(payload, headerMap, record.getId().getValue(),
                            request, ack);
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

        SubscriptionHealthTracker healthTracker = new SubscriptionHealthTracker(streamKey, "RAW");

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
                        .errorHandler(healthTracker::onError)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, byte[]>> container =
                StreamMessageListenerContainer.create(
                        redisTemplate.getConnectionFactory(), options);

        StreamListener<String, MapRecord<String, String, byte[]>> streamListener =
                record -> {
                    processRawRecord(record, request);
                    healthTracker.recordSuccess();
                };

        Consumer consumer = Consumer.from(request.groupName(), request.consumerName());
        org.springframework.data.redis.stream.Subscription springSubscription = container.register(
                StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(streamKey, ReadOffset.lastConsumed()))
                        .consumer(consumer)
                        .autoAcknowledge(false)
                        .cancelOnError(t -> false)
                        .build(),
                streamListener);

        container.start();
        log.info("Started subscription on stream '{}' [group={}, consumer={}, encoding=RAW]",
                streamKey, request.groupName(), request.consumerName());

        return new RedisSubscription(request.channel(), request.groupName(), container, springSubscription, healthTracker);
    }

    private void processRawRecord(MapRecord<String, String, byte[]> record, SubscribeRequest request) {
        try {
            Map<String, byte[]> fields = record.getValue();

            byte[] payload = fields.getOrDefault(PAYLOAD_FIELD, new byte[0]);

            Map<String, String> headerMap = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> entry : fields.entrySet()) {
                if (!PAYLOAD_FIELD.equals(entry.getKey())) {
                    headerMap.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
                }
            }

            dispatchMessage(payload, headerMap, record.getId().getValue(), request);
        } catch (Exception e) {
            log.error("Failed to process record [id={}, channel={}]: {}",
                    record.getId(), request.channel(), e.getMessage(), e);
            String streamKey = resolveKey(request.channel());
            redisTemplate.opsForStream().acknowledge(streamKey, request.groupName(),
                    record.getId().getValue());
        }
    }

    @SuppressWarnings("unchecked")
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
                    MessageAcknowledgment ack = new RedisMessageAcknowledgment(
                            redisTemplate, streamKey, request.groupName(),
                            record.getId().getValue());
                    extractAndDispatchByteRecord(record, request, ack);
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
                    MessageAcknowledgment ack = new RedisMessageAcknowledgment(
                            redisTemplate, streamKey, request.groupName(),
                            record.getId().getValue());
                    extractAndDispatchByteRecord(record, request, ack);
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

        traceMessage("IN", request.channel(), recordId, messageId, payload, headers);

        request.listener().onMessage(message, ack);
    }

    private void traceMessage(String direction, String channel, String recordId,
                               String messageId, byte[] payload, MessageHeaders headers) {
        if (!MESSAGE_TRACE.isInfoEnabled()) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder(256);
            sb.append("{\"ts\":\"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            sb.append("\",\"dir\":\"").append(direction);
            sb.append("\",\"ch\":\"").append(escapeJson(channel));
            sb.append("\",\"recordId\":\"").append(escapeJson(recordId));
            sb.append("\",\"msgId\":\"").append(escapeJson(messageId));
            sb.append("\",\"size\":").append(payload.length);
            sb.append(",\"headers\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : headers.toMap().entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append('"');
                first = false;
            }
            sb.append("},\"payload\":\"").append(Base64.getEncoder().encodeToString(payload));
            sb.append("\"}");
            MESSAGE_TRACE.info(sb.toString());
        } catch (Exception e) {
            log.debug("Failed to trace message: {}", e.getMessage());
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
        private final SubscriptionHealthTracker healthTracker;
        private final AtomicBoolean active = new AtomicBoolean(true);

        RedisSubscription(String channel, String groupName,
                          StreamMessageListenerContainer<?, ?> container,
                          org.springframework.data.redis.stream.Subscription springSubscription,
                          SubscriptionHealthTracker healthTracker) {
            this.channel = channel;
            this.groupName = groupName;
            this.container = container;
            this.springSubscription = springSubscription;
            this.healthTracker = healthTracker;
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
         * Returns {@code true} only when the container is running, the internal
         * subscription task is alive, AND the polling loop is healthy (not in
         * a consecutive error state). This ensures that subscriptions stuck in
         * error loops (e.g., stream deleted, Redis unreachable) are detected
         * as dead and resubscribed by the PEL recovery scheduler.
         */
        @Override
        public boolean isActive() {
            return active.get() && container.isRunning() && springSubscription.isActive()
                    && healthTracker.isHealthy();
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

    /**
     * Tracks polling health for a single subscription. Counts consecutive
     * errors and applies exponential backoff (500ms to 30s) during error
     * storms to prevent tight polling loops that flood logs and waste CPU.
     *
     * <p>After {@link #UNHEALTHY_THRESHOLD} consecutive errors, {@link #isHealthy()}
     * returns {@code false}, causing the subscription's {@code isActive()} to
     * return {@code false}. The PEL recovery scheduler in {@link RedisBrokerStrategy}
     * then detects the dead subscription and triggers a full resubscribe
     * (ensureConsumerGroup + PEL recovery + new subscription).
     */
    private static class SubscriptionHealthTracker {

        private static final int UNHEALTHY_THRESHOLD = 5;
        private static final long INITIAL_BACKOFF_MS = 500;
        private static final long MAX_BACKOFF_MS = 30_000;

        private final String streamKey;
        private final String encoding;
        private final AtomicInteger consecutiveErrors = new AtomicInteger(0);

        SubscriptionHealthTracker(String streamKey, String encoding) {
            this.streamKey = streamKey;
            this.encoding = encoding;
        }

        void onError(Throwable t) {
            int errorCount = consecutiveErrors.incrementAndGet();
            long backoffMs = Math.min(
                    INITIAL_BACKOFF_MS * (1L << Math.min(errorCount - 1, 16)),
                    MAX_BACKOFF_MS);

            log.warn("Stream polling error [stream={}, encoding={}, consecutiveErrors={}, backoffMs={}]: {}",
                    streamKey, encoding, errorCount, backoffMs, t.getMessage());

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void recordSuccess() {
            if (consecutiveErrors.get() > 0) {
                int prev = consecutiveErrors.getAndSet(0);
                log.info("Stream polling recovered [stream={}, encoding={}, previousErrors={}]",
                        streamKey, encoding, prev);
            }
        }

        boolean isHealthy() {
            return consecutiveErrors.get() < UNHEALTHY_THRESHOLD;
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(SubscriptionHealthTracker.class);
    }
}
