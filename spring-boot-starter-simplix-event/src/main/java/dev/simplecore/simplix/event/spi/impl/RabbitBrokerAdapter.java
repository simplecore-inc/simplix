package dev.simplecore.simplix.event.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.event.constant.SimpliXEventConstants;
import dev.simplecore.simplix.event.constant.SimpliXLogMessages;
import dev.simplecore.simplix.event.constant.SimpliXMetricsConstants;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RabbitMQ Broker Adapter
 * 
 * A message broker implementation using RabbitMQ.
 * Provides reliable message delivery with queue-based routing.
 * 
 * Configuration example in application.yml:
 * ```yaml
 * spring:
 *   rabbitmq:
 *     host: localhost
 *     port: 5672
 *     username: guest
 *     password: guest
 *     virtual-host: /
 * 
 * simplix:
 *   event:
 *     mq-type: rabbitmq
 *     rabbit:
 *       exchange-name: simplix.event.exchange
 *       queue-name: simplix.event.queue
 * 
 * # Features:
 * # 1. Reliable message delivery with RabbitMQ's persistence
 * # 2. Exchange and queue-based routing
 * # 3. Metrics tracking for message processing
 * 
 * # Use Cases:
 * # 1. Systems requiring reliable message delivery
 * # 2. Event processing with persistence
 * ```
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simplix.event.mq-type", havingValue = "rabbitmq")
public class RabbitBrokerAdapter extends AbstractMessageBrokerAdapter {
    private final RabbitTemplate rabbitTemplate;
    private final ConnectionFactory connectionFactory;
    private final MessageConverter messageConverter;
    private final SimpliXEventProperties properties;
    private final ApplicationContext applicationContext;
    private final List<SimpleMessageListenerContainer> containers = new ArrayList<>();

    public RabbitBrokerAdapter(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RetryTemplate retryTemplate,
            MeterRegistry meterRegistry,
            SimpliXEventProperties properties,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate) {
        super(retryTemplate, meterRegistry, properties, applicationContext);
        this.connectionFactory = connectionFactory;
        this.messageConverter = messageConverter;
        this.properties = properties;
        this.applicationContext = applicationContext;
        this.rabbitTemplate = rabbitTemplate;
        
        this.rabbitTemplate.setMessageConverter(messageConverter);
        this.rabbitTemplate.setRetryTemplate(retryTemplate);
        this.rabbitTemplate.setMandatory(true);
        this.rabbitTemplate.setExchange(properties.getRabbit().getExchangeName());

        // Create Exchange
        Exchange exchange = ExchangeBuilder
                .topicExchange(properties.getRabbit().getExchangeName())
                .durable(true)
                .build();
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.declareExchange(exchange);
        log.info(SimpliXLogMessages.RABBIT_CONNECTED, properties.getRabbit().getExchangeName());
        
        initializeSubscribers();
    }

    private void initializeSubscribers() {
        @SuppressWarnings("rawtypes")
        Map<String, SimpliXEventReceiver> receivers = applicationContext.getBeansOfType(SimpliXEventReceiver.class);
        log.info("Found {} SimpliXEventReceiver implementations", receivers.size());
        
        receivers.values().forEach(receiver -> {
            String[] supportedChannels = receiver.getSupportedChannels();
            for (String channel : supportedChannels) {
                log.info("Registering receiver: {} for channel: {}", receiver.getClass().getName(), channel);
                subscribe(channel, receiver);
            }
        });
    }

    @Override
    public void send(SimpliXMessageEvent event) {
        String channel = event.getChannelName();
        sendMessage(channel, event,
            SimpliXMetricsConstants.RABBIT_OUTBOUND_ATTEMPTS,
            SimpliXMetricsConstants.RABBIT_OUTBOUND_SUCCESS,
            SimpliXMetricsConstants.RABBIT_OUTBOUND_ERRORS,
            () -> rabbitTemplate.convertAndSend(properties.getRabbit().getExchangeName(), channel, event)
        );
    }

    @Override
    protected void setupMessageListener(String channel, Consumer<Object> messageHandler) {
        // Skip setting up listener for outbound channel
        if (SimpliXEventConstants.DEFAULT_OUTBOUND_CHANNEL.equals(channel)) {
            log.debug("Skipping message listener setup for outbound channel: {}. This channel is handled directly by the integration framework.", channel);
            return;
        }
        
        // Create queue name
        String queueName = properties.getRabbit().getQueueName() + "." + channel;
        
        // Create queue and bind it to the exchange
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        Queue queue = QueueBuilder.durable(queueName).build();
        rabbitAdmin.declareQueue(queue);
        
        // Bind queue to exchange with routing key matching the channel name
        Binding binding = BindingBuilder
                .bind(queue)
                .to(new TopicExchange(properties.getRabbit().getExchangeName()))
                .with(channel);
        rabbitAdmin.declareBinding(binding);
        
        log.info("Created and bound queue: {} to exchange: {} with routing key: {}", 
                 queueName, properties.getRabbit().getExchangeName(), channel);
        
        // Set up the message listener container
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(new MessageListenerAdapter(
            new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        SimpliXMessageEvent event = (SimpliXMessageEvent) messageConverter.fromMessage(message);
                        messageHandler.accept(event);
                    } catch (Exception e) {
                        log.error("Error processing RabbitMQ message: {}", e.getMessage(), e);
                    }
                }
            },
            messageConverter
        ));
        container.start();
        containers.add(container);
    }

    @PreDestroy
    public void stopContainers() {
        log.info("Stopping all RabbitMQ message listener containers");
        containers.forEach(container -> {
            try {
                container.stop();
                log.info("Stopped container for queue: {}", String.join(", ", container.getQueueNames()));
            } catch (Exception e) {
                log.error("Error stopping container: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    protected void cleanupResources() {
        stopContainers();
        containers.clear();
    }

    @Override
    protected String getSubscribersMetricName() {
        return SimpliXMetricsConstants.RABBIT_SUBSCRIBERS;
    }

    @Override
    protected String getInboundProcessedMetricName() {
        return SimpliXMetricsConstants.RABBIT_INBOUND_PROCESSED;
    }

    @Override
    protected String getInboundErrorsMetricName() {
        return SimpliXMetricsConstants.RABBIT_INBOUND_ERRORS;
    }
} 