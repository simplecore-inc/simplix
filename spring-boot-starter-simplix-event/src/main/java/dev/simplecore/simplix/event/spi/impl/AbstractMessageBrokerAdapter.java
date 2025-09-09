package dev.simplecore.simplix.event.spi.impl;

import dev.simplecore.simplix.event.constant.SimpliXMetricsConstants;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.util.PayloadConverter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

@Slf4j
public abstract class AbstractMessageBrokerAdapter implements MessageBrokerAdapter {
    protected final RetryTemplate retryTemplate;
    protected final MeterRegistry meterRegistry;
    protected final SimpliXEventProperties properties;
    protected final ApplicationContext applicationContext;
    protected final Map<String, Set<Consumer<SimpliXMessageEvent>>> subscribers = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    @FunctionalInterface
    protected interface MessageSender {
        void send() throws Exception;
    }

    @FunctionalInterface
    protected interface MessageProcessor {
        void process(Object message) throws Exception;
    }

    protected AbstractMessageBrokerAdapter(
            RetryTemplate retryTemplate,
            MeterRegistry meterRegistry,
            SimpliXEventProperties properties,
            ApplicationContext applicationContext) {
        this.retryTemplate = retryTemplate;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void initializeReceivers() {
        if (initialized) {
            log.debug("Receivers already initialized, skipping");
            return;
        }

        log.info("Initializing SimpliXEventReceivers");
        try {
            Map<String, SimpliXEventReceiver> receivers = applicationContext.getBeansOfType(SimpliXEventReceiver.class);
            log.info("Found {} SimpliXEventReceiver implementations", receivers.size());
            
            PayloadConverter payloadConverter = null;
            try {
                payloadConverter = applicationContext.getBean(PayloadConverter.class);
                log.info("Found PayloadConverter bean");
            } catch (Exception e) {
                log.warn("PayloadConverter bean not found: {}. Will use direct casting for payloads.", e.getMessage());
            }
            
            for (Map.Entry<String, SimpliXEventReceiver> entry : receivers.entrySet()) {
                String name = entry.getKey();
                SimpliXEventReceiver receiver = entry.getValue();
                
                String[] channels = receiver.getSupportedChannels();
                if (channels == null || channels.length == 0) {
                    log.warn("Receiver {} does not support any channels, skipping", name);
                    continue;
                }
                
                Class<?> payloadType = null;
                try {
                    payloadType = receiver.getPayloadType();
                    log.info("Receiver {} expects payload type: {}", name, payloadType.getName());
                } catch (Exception e) {
                    log.warn("Failed to get payload type for receiver {}: {}", name, e.getMessage());
                }
                
                for (String channel : channels) {
                    // Check if already subscribed
                    if (subscribers.containsKey(channel)) {
                        log.debug("Channel {} already has subscribers, skipping", channel);
                        continue;
                    }
                    
                    log.info("Setting up subscription for receiver: {} on channel: {}", name, channel);
                    
                    final Class<?> finalPayloadType = payloadType;
                    final PayloadConverter finalPayloadConverter = payloadConverter;
                    
                    subscribe(channel, event -> {
                        try {
                            Object convertedPayload = null;
                            
                            if (finalPayloadConverter != null && finalPayloadType != null) {
                                try {
                                    convertedPayload = finalPayloadConverter.convertPayload(event.getPayload(), finalPayloadType);
                                } catch (Exception e) {
                                    log.error("Failed to convert payload for receiver {} on channel {}: {}", 
                                        name, channel, e.getMessage(), e);
                                    return;
                                }
                            } else {
                                convertedPayload = event.getPayload();
                                log.debug("Using direct payload without conversion for receiver: {} on channel: {}", 
                                        name, channel);
                            }
                            
                            receiver.onEvent(channel, event, convertedPayload);
                        } catch (Exception e) {
                            log.error("Error forwarding event to receiver: {} for channel: {}: {}", 
                                    name, channel, e.getMessage(), e);
                        }
                    });
                }
            }
            initialized = true;
            log.info("Successfully initialized all receivers");
        } catch (Exception e) {
            log.error("Failed to initialize receivers: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize receivers", e);
        }
    }

    @Override
    public void subscribe(String channel, Consumer<SimpliXMessageEvent> handler) {
        log.info("Setting up subscription for channel: {}", channel);
        
        try {
            // Register subscriber
            subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(handler);
            meterRegistry.counter(getSubscribersMetricName(), 
                SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();

            // Set up message listener
            setupMessageListener(channel, message -> {
                if (message instanceof SimpliXMessageEvent) {
                    SimpliXMessageEvent event = (SimpliXMessageEvent) message;
                    try {
                        handler.accept(event);
                        meterRegistry.counter(getInboundProcessedMetricName(), 
                            SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
                    } catch (Exception e) {
                        log.error("Error processing event on channel {}: {}", channel, e.getMessage(), e);
                        meterRegistry.counter(getInboundErrorsMetricName(), 
                            SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
                    }
                } else {
                    log.warn("Received non-SimpliXMessageEvent message on channel {}: {}", channel, message.getClass().getName());
                }
            });
            
            log.info("Subscription set up for channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to set up subscription for channel {}: {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to set up subscription for channel: " + channel, e);
        }
    }

    @Override
    public void subscribe(String channel, SimpliXEventReceiver<?> receiver) {
        log.info("Setting up subscription for receiver on channel: {}", channel);
        
        try {
            // Get payload type from the receiver
            Class<?> payloadType = receiver.getPayloadType();
            
            // Get PayloadConverter
            PayloadConverter payloadConverter = null;
            try {
                payloadConverter = applicationContext.getBean(PayloadConverter.class);
            } catch (Exception e) {
                log.warn("PayloadConverter bean not found: {}. Will use direct casting for payloads.", e.getMessage());
            }
            
            final Class<?> finalPayloadType = payloadType;
            final PayloadConverter finalPayloadConverter = payloadConverter;
            
            // Set up subscription with payload conversion
            subscribe(channel, event -> {
                try {
                    Object convertedPayload = null;
                    
                    if (finalPayloadConverter != null && finalPayloadType != null) {
                        try {
                            convertedPayload = finalPayloadConverter.convertPayload(event.getPayload(), finalPayloadType);
                        } catch (Exception e) {
                            log.error("Failed to convert payload for receiver {} on channel {}: {}", 
                                receiver.getClass().getSimpleName(), channel, e.getMessage(), e);
                            return;
                        }
                    } else {
                        convertedPayload = event.getPayload();
                        log.debug("Using direct payload without conversion for receiver: {} on channel: {}", 
                                receiver.getClass().getSimpleName(), channel);
                    }
                    
                    @SuppressWarnings("unchecked")
                    SimpliXEventReceiver<Object> typedReceiver = (SimpliXEventReceiver<Object>) receiver;
                    typedReceiver.onEvent(channel, event, convertedPayload);
                } catch (Exception e) {
                    log.error("Error processing event for receiver {} on channel {}: {}", 
                            receiver.getClass().getSimpleName(), channel, e.getMessage(), e);
                }
            });
            
            log.info("Subscription set up for receiver on channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to set up subscription for receiver on channel {}: {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to set up subscription for receiver on channel: " + channel, e);
        }
    }

    @Override
    public void unsubscribe(String channel, SimpliXEventReceiver<?> receiver) {
        log.info("Removing subscription for receiver on channel: {}", channel);
        try {
            // Find and remove the subscriber
            Set<Consumer<SimpliXMessageEvent>> channelSubscribers = subscribers.get(channel);
            if (channelSubscribers != null) {
                channelSubscribers.removeIf(subscriber -> {
                    try {
                        // Use reflection to check if the subscriber is for this receiver
                        java.lang.reflect.Field field = subscriber.getClass().getDeclaredField("receiver");
                        field.setAccessible(true);
                        return field.get(subscriber) == receiver;
                    } catch (Exception e) {
                        return false;
                    }
                });
                
                if (channelSubscribers.isEmpty()) {
                    subscribers.remove(channel);
                    meterRegistry.counter(getSubscribersMetricName(), 
                        SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment(-1);
                    log.info("Removed empty channel: {}", channel);
                }
            }
        } catch (Exception e) {
            log.error("Failed to remove subscription for receiver on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    protected void deliverMessage(String channel, SimpliXMessageEvent event) {
        Set<Consumer<SimpliXMessageEvent>> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers != null) {
            channelSubscribers.forEach(subscriber -> {
                try {
                    subscriber.accept(event);
                    meterRegistry.counter(getInboundProcessedMetricName(), 
                        SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
                } catch (Exception e) {
                    log.error("Failed to deliver message to subscriber", e);
                    meterRegistry.counter(getInboundErrorsMetricName(), 
                        SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
                }
            });
        }
    }

    @Override
    public void cleanup() {
        log.info("Cleaning up resources");
        try {
            subscribers.clear();
            cleanupResources();
        } catch (Exception e) {
            log.error("Error during cleanup: {}", e.getMessage(), e);
        }
    }

    protected void sendMessage(String channel, SimpliXMessageEvent event, 
            String outboundAttemptsMetric, String outboundSuccessMetric, String outboundErrorsMetric,
            MessageSender messageSender) {
        try {
            meterRegistry.counter(outboundAttemptsMetric, 
                SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();

            retryTemplate.execute(ctx -> {
                try {
                    messageSender.send();
                    return null;
                } catch (Exception e) {
                    log.error("Error sending message: {}", e.getMessage(), e);
                    throw e;
                }
            });

            meterRegistry.counter(outboundSuccessMetric, 
                SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
        } catch (Exception e) {
            meterRegistry.counter(outboundErrorsMetric, 
                SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
            log.error("Failed to send message: channel={}, error={}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    protected void setupMessageListenerTemplate(String channel,
            String inboundReceivedMetric, String inboundErrorsMetric,
            MessageProcessor messageProcessor) {
        try {
            setupMessageListener(channel, message -> {
                try {
                    meterRegistry.counter(inboundReceivedMetric, 
                        SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();

                    retryTemplate.execute(ctx -> {
                        try {
                            messageProcessor.process(message);
                            return null;
                        } catch (Exception e) {
                            log.error("Error processing message: {}", e.getMessage(), e);
                            throw e;
                        }
                    });
                } catch (Exception e) {
                    meterRegistry.counter(inboundErrorsMetric, 
                        SimpliXMetricsConstants.METRIC_CHANNEL, channel).increment();
                    log.error("Failed to process message: channel={}, error={}", channel, e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to set up message listener for channel {}: {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to set up message listener: " + e.getMessage(), e);
        }
    }

    // Abstract methods to be implemented by concrete adapters
    protected abstract void setupMessageListener(String channel, Consumer<Object> messageHandler);
    protected abstract void cleanupResources();
    protected abstract String getSubscribersMetricName();
    protected abstract String getInboundProcessedMetricName();
    protected abstract String getInboundErrorsMetricName();
} 