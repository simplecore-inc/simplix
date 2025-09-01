package dev.simplecore.simplix.event.spi.impl;

import dev.simplecore.simplix.event.constant.SimpliXMetricsConstants;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * NATS Broker Adapter
 * 
 * A message broker implementation using NATS.
 * Provides lightweight, high-performance message delivery.
 * 
 * Configuration example in application.yml:
 * ```yaml
 * spring:
 *   nats:
 *     server: nats://localhost:4222
 *     connection-timeout: 2000
 *     ping-interval: 5000
 *     reconnect-wait: 2000
 *     max-reconnects: 10
 * 
 * simplix:
 *   event:
 *     mq-type: nats
 *     nats:
 *       subject: simplix.>
 * 
 * # Features:
 * # 1. Lightweight and high-performance message delivery
 * # 2. Subject-based routing
 * # 3. Metrics tracking for message processing
 * 
 * # Use Cases:
 * # 1. High-performance event processing
 * # 2. Real-time event streaming
 * ```
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simplix.event.mq-type", havingValue = "nats")
public class NatsBrokerAdapter extends AbstractMessageBrokerAdapter {
    private final Connection natsConnection;
    private final List<Subscription> subscriptions = new ArrayList<>();

    public NatsBrokerAdapter(
            RetryTemplate retryTemplate,
            MeterRegistry meterRegistry,
            SimpliXEventProperties properties,
            ApplicationContext applicationContext,
            Connection natsConnection) {
        super(retryTemplate, meterRegistry, properties, applicationContext);
        this.natsConnection = natsConnection;
    }

    @Override
    public void send(SimpliXMessageEvent event) {
        String channel = event.getChannelName();
        sendMessage(channel, event,
            SimpliXMetricsConstants.NATS_OUTBOUND_ATTEMPTS,
            SimpliXMetricsConstants.NATS_OUTBOUND_SUCCESS,
            SimpliXMetricsConstants.NATS_OUTBOUND_ERRORS,
            () -> natsConnection.publish(channel, event.toString().getBytes())
        );
    }

    @Override
    protected void setupMessageListener(String channel, Consumer<Object> messageHandler) {
        try {
            Dispatcher dispatcher = natsConnection.createDispatcher();
            Subscription subscription = dispatcher.subscribe(channel, msg -> {
                try {
                    SimpliXMessageEvent event = new SimpliXMessageEvent(new String(msg.getData()));
                    messageHandler.accept(event);
                } catch (Exception e) {
                    log.error("Error processing NATS message: {}", e.getMessage(), e);
                }
            });
            subscriptions.add(subscription);
            log.info("Subscription set up for NATS subject: {}", channel);
        } catch (Exception e) {
            log.error("Failed to set up NATS subscription for subject: {}, error: {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to set up NATS subscription: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stopSubscriptions() {
        log.info("Stopping all NATS subscriptions");
        subscriptions.forEach(subscription -> {
            try {
                subscription.unsubscribe();
                log.info("Unsubscribed from subject: {}", subscription.getSubject());
            } catch (Exception e) {
                log.error("Error unsubscribing from subject: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    protected void cleanupResources() {
        stopSubscriptions();
        subscriptions.clear();
    }

    @Override
    protected String getSubscribersMetricName() {
        return SimpliXMetricsConstants.NATS_SUBSCRIBERS;
    }

    @Override
    protected String getInboundProcessedMetricName() {
        return SimpliXMetricsConstants.NATS_INBOUND_PROCESSED;
    }

    @Override
    protected String getInboundErrorsMetricName() {
        return SimpliXMetricsConstants.NATS_INBOUND_ERRORS;
    }
}