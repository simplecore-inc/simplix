package dev.simplecore.simplix.messaging.broker.rabbit;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ implementation of {@link BrokerStrategy}.
 *
 * <p>Uses a topic exchange ({@code simplix.messaging}) with channel names as routing keys.
 * Each subscription creates a {@link SimpleMessageListenerContainer} bound to an
 * auto-declared queue with dead letter queue (DLQ) binding.
 *
 * <p>Capabilities: no consumer groups, no replay, no ordering guarantee, dead letter supported.
 *
 * <p>Thread-safe. Tracks active listener containers for clean shutdown.
 */
@Slf4j
public class RabbitBrokerStrategy implements BrokerStrategy {

    private static final BrokerCapabilities CAPABILITIES =
            new BrokerCapabilities(false, false, false, true);

    private static final String EXCHANGE_NAME = "simplix.messaging";
    private static final String DLX_EXCHANGE_NAME = "simplix.messaging.dlx";
    private static final String DLQ_SUFFIX = ".dlq";

    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final RabbitAdmin rabbitAdmin;
    private final ConcurrentHashMap<String, SimpleMessageListenerContainer> activeContainers =
            new ConcurrentHashMap<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Create a RabbitMQ broker strategy.
     *
     * @param rabbitTemplate    the RabbitMQ template for publishing
     * @param connectionFactory the RabbitMQ connection factory for consumer containers
     */
    public RabbitBrokerStrategy(RabbitTemplate rabbitTemplate, ConnectionFactory connectionFactory) {
        this.rabbitTemplate = rabbitTemplate;
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
    }

    @Override
    public PublishResult send(String channel, byte[] payload, MessageHeaders headers) {
        String messageId = headers.get(MessageHeaders.MESSAGE_ID).orElse(UUID.randomUUID().toString());
        String contentType = headers.get(MessageHeaders.CONTENT_TYPE).orElse("application/octet-stream");

        org.springframework.amqp.core.MessageProperties properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setContentType(contentType);
        properties.setTimestamp(new java.util.Date());

        // Copy all message headers into AMQP headers
        headers.toMap().forEach(properties::setHeader);

        org.springframework.amqp.core.Message amqpMessage =
                new org.springframework.amqp.core.Message(payload, properties);

        rabbitTemplate.send(EXCHANGE_NAME, channel, amqpMessage);

        log.debug("Published message to exchange='{}' routingKey='{}' [messageId={}]",
                EXCHANGE_NAME, channel, messageId);

        return new PublishResult(messageId, channel, Instant.now());
    }

    @Override
    public Subscription subscribe(SubscribeRequest request) {
        String queueName = request.channel();

        // Declare queue with DLQ binding
        declareQueueWithDlq(queueName, request.channel());

        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        container.setPrefetchCount(request.batchSize());

        container.setMessageListener(new org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener() {
            @Override
            public void onMessage(org.springframework.amqp.core.Message amqpMessage,
                                  com.rabbitmq.client.Channel rabbitChannel) {
                dispatchMessage(amqpMessage, rabbitChannel, request);
            }
        });

        String containerKey = queueName + ":" + request.consumerName();
        activeContainers.put(containerKey, container);

        container.start();
        log.info("Started RabbitMQ subscription [queue={}, consumer={}]",
                queueName, request.consumerName());

        return new RabbitSubscription(request.channel(), request.groupName(), container, containerKey);
    }

    @Override
    public void ensureConsumerGroup(String channel, String groupName) {
        // AMQP does not have consumer groups. Competing consumers are achieved
        // by multiple consumers reading from the same queue.
        log.info("Consumer group concept not applicable for RabbitMQ; " +
                "competing consumers on queue '{}' achieve similar behavior", channel);
    }

    @Override
    public void acknowledge(String channel, String groupName, String messageId) {
        // Acknowledgment is handled internally by RabbitMessageAcknowledgment.
        log.debug("Acknowledge called for channel='{}' group='{}' messageId='{}' (handled by consumer)",
                channel, groupName, messageId);
    }

    @Override
    public BrokerCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void initialize() {
        // Declare the topic exchange and DLX exchange
        rabbitAdmin.declareExchange(new TopicExchange(EXCHANGE_NAME, true, false));
        rabbitAdmin.declareExchange(new TopicExchange(DLX_EXCHANGE_NAME, true, false));

        ready.set(true);
        log.info("RabbitMQ broker strategy initialized [exchange={}]", EXCHANGE_NAME);
    }

    @Override
    public void shutdown() {
        log.info("Shutting down RabbitMQ broker strategy, stopping {} active container(s)",
                activeContainers.size());

        activeContainers.forEach((key, container) -> {
            try {
                if (container.isRunning()) {
                    container.stop();
                    log.debug("Stopped RabbitMQ container [key={}]", key);
                }
            } catch (Exception e) {
                log.warn("Error stopping RabbitMQ container [key={}]: {}", key, e.getMessage());
            }
        });

        activeContainers.clear();
        ready.set(false);
        log.info("RabbitMQ broker strategy shut down");
    }

    @Override
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public String name() {
        return "rabbit";
    }

    // ---------------------------------------------------------------
    // Internal methods
    // ---------------------------------------------------------------

    /**
     * Declare a queue with dead letter exchange binding.
     * The DLQ is named "{queueName}.dlq" and bound to the DLX exchange.
     */
    private void declareQueueWithDlq(String queueName, String routingKey) {
        // Main queue with DLX argument
        Map<String, Object> queueArgs = new HashMap<>();
        queueArgs.put("x-dead-letter-exchange", DLX_EXCHANGE_NAME);
        queueArgs.put("x-dead-letter-routing-key", queueName + DLQ_SUFFIX);

        Queue mainQueue = new Queue(queueName, true, false, false, queueArgs);
        rabbitAdmin.declareQueue(mainQueue);

        // Bind main queue to the topic exchange
        TopicExchange exchange = new TopicExchange(EXCHANGE_NAME);
        rabbitAdmin.declareBinding(BindingBuilder.bind(mainQueue).to(exchange).with(routingKey));

        // Dead letter queue
        Queue dlq = new Queue(queueName + DLQ_SUFFIX, true, false, false);
        rabbitAdmin.declareQueue(dlq);

        // Bind DLQ to DLX exchange
        TopicExchange dlxExchange = new TopicExchange(DLX_EXCHANGE_NAME);
        rabbitAdmin.declareBinding(BindingBuilder.bind(dlq).to(dlxExchange).with(queueName + DLQ_SUFFIX));

        log.debug("Declared queue '{}' with DLQ '{}'", queueName, queueName + DLQ_SUFFIX);
    }

    private void dispatchMessage(org.springframework.amqp.core.Message amqpMessage,
                                 com.rabbitmq.client.Channel rabbitChannel,
                                 SubscribeRequest request) {
        byte[] payload = amqpMessage.getBody() != null ? amqpMessage.getBody() : new byte[0];
        MessageProperties properties = amqpMessage.getMessageProperties();

        // Extract headers from AMQP message properties
        Map<String, String> headerMap = new java.util.LinkedHashMap<>();
        if (properties.getHeaders() != null) {
            properties.getHeaders().forEach((key, value) ->
                    headerMap.put(key, value != null ? value.toString() : ""));
        }
        MessageHeaders headers = MessageHeaders.of(headerMap);

        String messageId = properties.getMessageId() != null
                ? properties.getMessageId()
                : headers.get(MessageHeaders.MESSAGE_ID).orElse(UUID.randomUUID().toString());

        Message<byte[]> message = Message.<byte[]>builder()
                .messageId(messageId)
                .channel(request.channel())
                .payload(payload)
                .headers(headers)
                .build();

        long deliveryTag = properties.getDeliveryTag();
        MessageAcknowledgment ack = new RabbitMessageAcknowledgment(rabbitChannel, deliveryTag);

        request.listener().onMessage(message, ack);
    }

    // ---------------------------------------------------------------
    // Inner classes
    // ---------------------------------------------------------------

    /**
     * {@link MessageAcknowledgment} implementation backed by RabbitMQ's {@link com.rabbitmq.client.Channel}.
     */
    private static class RabbitMessageAcknowledgment implements MessageAcknowledgment {

        private final com.rabbitmq.client.Channel rabbitChannel;
        private final long deliveryTag;

        RabbitMessageAcknowledgment(com.rabbitmq.client.Channel rabbitChannel, long deliveryTag) {
            this.rabbitChannel = rabbitChannel;
            this.deliveryTag = deliveryTag;
        }

        @Override
        public void ack() {
            try {
                rabbitChannel.basicAck(deliveryTag, false);
            } catch (Exception e) {
                log.warn("Failed to ack message [deliveryTag={}]: {}", deliveryTag, e.getMessage());
            }
        }

        @Override
        public void nack(boolean requeue) {
            try {
                rabbitChannel.basicNack(deliveryTag, false, requeue);
            } catch (Exception e) {
                log.warn("Failed to nack message [deliveryTag={}, requeue={}]: {}",
                        deliveryTag, requeue, e.getMessage());
            }
        }

        @Override
        public void reject(String reason) {
            try {
                // Reject without requeue; the DLX binding routes it to the DLQ
                log.warn("Rejecting RabbitMQ message [deliveryTag={}, reason={}]", deliveryTag, reason);
                rabbitChannel.basicReject(deliveryTag, false);
            } catch (Exception e) {
                log.warn("Failed to reject message [deliveryTag={}, reason={}]: {}",
                        deliveryTag, reason, e.getMessage());
            }
        }

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(RabbitMessageAcknowledgment.class);
    }

    /**
     * {@link Subscription} implementation wrapping a {@link SimpleMessageListenerContainer}.
     */
    private class RabbitSubscription implements Subscription {

        private final String channel;
        private final String groupName;
        private final SimpleMessageListenerContainer container;
        private final String containerKey;
        private final AtomicBoolean active = new AtomicBoolean(true);

        RabbitSubscription(String channel, String groupName,
                           SimpleMessageListenerContainer container,
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
                log.info("Cancelled RabbitMQ subscription [channel={}, group={}]", channel, groupName);
            }
        }
    }
}
