package dev.simplecore.simplix.messaging.broker.kafka;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache Kafka implementation of {@link BrokerStrategy}.
 *
 * <p>Uses Spring Kafka's {@link KafkaTemplate} for publishing and
 * {@link ConcurrentMessageListenerContainer} for consuming. Each subscription
 * creates its own listener container scoped to a single topic and consumer group.
 *
 * <p>Capabilities: consumer groups, replay, ordering, dead letter (all supported).
 *
 * <p>Thread-safe. Tracks active containers for clean shutdown.
 */
@Slf4j
public class KafkaBrokerStrategy implements BrokerStrategy {

    private static final BrokerCapabilities CAPABILITIES =
            new BrokerCapabilities(true, true, true, true);
    private static final long SEND_TIMEOUT_SECONDS = 30;

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final org.springframework.kafka.core.ConsumerFactory<String, byte[]> consumerFactory;
    private final String defaultTopic;
    private final ConcurrentHashMap<String, ConcurrentMessageListenerContainer<String, byte[]>> activeContainers =
            new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Create a Kafka broker strategy.
     *
     * @param kafkaTemplate   the Kafka template for producing messages
     * @param consumerFactory the consumer factory for creating listener containers
     * @param defaultTopic    optional default topic (used when channel is null or empty)
     */
    public KafkaBrokerStrategy(KafkaTemplate<String, byte[]> kafkaTemplate,
                                org.springframework.kafka.core.ConsumerFactory<String, byte[]> consumerFactory,
                                String defaultTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.consumerFactory = consumerFactory;
        this.defaultTopic = defaultTopic;
    }

    /**
     * Create a Kafka broker strategy without a default topic.
     *
     * @param kafkaTemplate   the Kafka template for producing messages
     * @param consumerFactory the consumer factory for creating listener containers
     */
    public KafkaBrokerStrategy(KafkaTemplate<String, byte[]> kafkaTemplate,
                                org.springframework.kafka.core.ConsumerFactory<String, byte[]> consumerFactory) {
        this(kafkaTemplate, consumerFactory, null);
    }

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        String topic = resolveTopic(channel);
        String partitionKey = headers.get(MessageHeaders.PARTITION_KEY).orElse(null);

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, null, partitionKey, payload);

        // Copy message headers into Kafka record headers
        headers.toMap().forEach((key, value) ->
                record.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));

        try {
            CompletableFuture<SendResult<String, byte[]>> future = kafkaTemplate.send(record);
            SendResult<String, byte[]> sendResult = future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String recordId = sendResult.getRecordMetadata().topic()
                    + "-" + sendResult.getRecordMetadata().partition()
                    + "-" + sendResult.getRecordMetadata().offset();

            log.debug("Published message to topic '{}' [partition={}, offset={}]",
                    topic, sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());

            return new PublishResult(recordId, channel, Instant.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending Kafka message to topic: " + topic, e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send Kafka message to topic: " + topic, e);
        }
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        String topic = resolveTopic(request.channel());
        String groupId = request.groupName().isEmpty() ? "simplix-default" : request.groupName();

        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setGroupId(groupId);
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL);

        AcknowledgingMessageListener<String, byte[]> messageListener = (consumerRecord, acknowledgment) ->
                dispatchMessage(consumerRecord, acknowledgment, request);

        containerProperties.setMessageListener(messageListener);

        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);

        // Store but do not start yet - start is handled by initialize() or explicitly
        String containerKey = topic + ":" + groupId + ":" + request.consumerName();
        activeContainers.put(containerKey, container);

        container.start();
        log.info("Started Kafka subscription [topic={}, group={}, consumer={}]",
                topic, groupId, request.consumerName());

        return new KafkaSubscription(request.channel(), request.groupName(), container, containerKey);
    }

    @Override
    public void ensureConsumerGroup(String channel, String groupName) {
        // Kafka consumer groups are auto-created when a consumer joins.
        // No explicit creation needed.
        log.info("Consumer group '{}' for topic '{}' will be auto-created by Kafka on first consumer join",
                groupName, resolveTopic(channel));
    }

    @Override
    public void acknowledge(String channel, String groupName, String messageId) {
        // Acknowledgment is handled internally by KafkaMessageAcknowledgment.
        // This method exists for API compatibility; Kafka ack is per-consumer, not global.
        log.debug("Acknowledge called for channel='{}' group='{}' messageId='{}' (handled by consumer)",
                channel, groupName, messageId);
    }

    @Override
    public BrokerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void initialize() {
        ready.set(true);
        log.info("Kafka broker strategy initialized");
    }

    @Override
    public void shutdown() {
        log.info("Shutting down Kafka broker strategy, stopping {} active container(s)",
                activeContainers.size());

        activeContainers.forEach((key, container) -> {
            try {
                if (container.isRunning()) {
                    container.stop();
                    log.debug("Stopped Kafka container [key={}]", key);
                }
            } catch (Exception e) {
                log.warn("Error stopping Kafka container [key={}]: {}", key, e.getMessage());
            }
        });

        activeContainers.clear();
        ready.set(false);
        log.info("Kafka broker strategy shut down");
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public String name() {
        return "kafka";
    }

    // ---------------------------------------------------------------
    // Internal methods
    // ---------------------------------------------------------------

    private String resolveTopic(String channel) {
        if (channel != null && !channel.isEmpty()) {
            return channel;
        }
        if (defaultTopic != null && !defaultTopic.isEmpty()) {
            return defaultTopic;
        }
        throw new IllegalArgumentException("No topic specified and no default topic configured");
    }

    private void dispatchMessage(ConsumerRecord<String, byte[]> consumerRecord,
                                 Acknowledgment kafkaAcknowledgment,
                                 SubscribeRequest request) {
        byte[] payload = consumerRecord.value() != null ? consumerRecord.value() : new byte[0];

        // Extract headers from Kafka record
        Map<String, String> headerMap = new LinkedHashMap<>();
        for (Header header : consumerRecord.headers()) {
            headerMap.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        MessageHeaders headers = MessageHeaders.of(headerMap);

        String messageId = headers.get(MessageHeaders.MESSAGE_ID)
                .orElse(consumerRecord.topic() + "-" + consumerRecord.partition() + "-" + consumerRecord.offset());

        Message<byte[]> message = Message.<byte[]>builder()
                .messageId(messageId)
                .channel(request.channel())
                .payload(payload)
                .headers(headers)
                .build();

        MessageAcknowledgment ack = new KafkaMessageAcknowledgment(kafkaAcknowledgment);

        request.listener().onMessage(message, ack);
    }

    // ---------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------

    /**
     * {@link MessageAcknowledgment} implementation backed by Kafka's {@link Acknowledgment}.
     */
    private static class KafkaMessageAcknowledgment implements MessageAcknowledgment {

        private final Acknowledgment kafkaAcknowledgment;

        KafkaMessageAcknowledgment(Acknowledgment kafkaAcknowledgment) {
            this.kafkaAcknowledgment = kafkaAcknowledgment;
        }

        @Override
        public void ack() {
            if (kafkaAcknowledgment != null) {
                kafkaAcknowledgment.acknowledge();
            }
        }

        @Override
        public void nack(boolean requeue) {
            if (kafkaAcknowledgment != null) {
                // nack with sleep 0 to trigger immediate redelivery
                kafkaAcknowledgment.nack(java.time.Duration.ZERO);
            }
        }

        @Override
        public void reject(String reason) {
            // Acknowledge to skip the message; dead letter routing is handled
            // at a higher layer via Kafka DLT (Dead Letter Topic) configuration.
            log.warn("Rejecting Kafka message [reason={}], acknowledging to skip", reason);
            if (kafkaAcknowledgment != null) {
                kafkaAcknowledgment.acknowledge();
            }
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(KafkaMessageAcknowledgment.class);
    }

    /**
     * {@link Subscription} implementation wrapping a {@link ConcurrentMessageListenerContainer}.
     */
    private class KafkaSubscription implements Subscription {

        private final String channel;
        private final String groupName;
        private final ConcurrentMessageListenerContainer<String, byte[]> container;
        private final String containerKey;
        private final AtomicBoolean active = new AtomicBoolean(true);

        KafkaSubscription(String channel, String groupName,
                          ConcurrentMessageListenerContainer<String, byte[]> container,
                          String containerKey) {
            this.channel = channel;
            this.groupName = groupName;
            this.container = container;
            this.containerKey = containerKey;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public String groupName() {
            return groupName;
        }

        @Override
        public boolean isActive() {
            return active.get() && container.isRunning();
        }

        @Override
        public void cancel() {
            if (active.compareAndSet(true, false)) {
                container.stop();
                activeContainers.remove(containerKey);
                log.info("Cancelled Kafka subscription [channel={}, group={}]", channel, groupName);
            }
        }
    }
}
