package dev.simplecore.simplix.event.spi.impl;

import dev.simplecore.simplix.event.constant.SimpliXMetricsConstants;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import dev.simplecore.simplix.event.util.PayloadConverter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Kafka Broker Adapter
 * 
 * A message broker implementation using Apache Kafka.
 * Provides reliable message delivery and persistence.
 * 
 * Configuration example in application.yml:
 * ```yaml
 * spring:
 *   kafka:
 *     bootstrap-servers: localhost:9092
 *     producer:
 *       key-serializer: org.apache.kafka.common.serialization.StringSerializer
 *       value-serializer: org.apache.kafka.common.serialization.ByteArraySerializer
 *     consumer:
 *       group-id: simplix-event-group
 *       auto-offset-reset: earliest
 *       key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
 *       value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
 *       properties:
 *         spring.json.trusted.packages: dev.simplecore.simplix.event
 * 
 * simplix:
 *   event:
 *     mq-type: kafka
 *     kafka:
 *       group-id: simplix-event-group       # Consumer group ID
 *       topic-pattern: simplix.*            # Topic pattern for auto-subscription
 *       topic-prefix: simplix.event.        # Prefix for topic names
 * 
 * # Features:
 * # 1. Reliable message delivery with Kafka's persistence
 * # 2. Topic-based routing
 * # 3. Metrics tracking for message processing
 * 
 * # Use Cases:
 * # 1. Distributed systems requiring reliable message delivery
 * # 2. Event streaming and persistence
 * ```
 * 
 * Required dependencies:
 * ```gradle
 * implementation 'org.springframework.kafka:spring-kafka'
 * ```
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simplix.event.mq-type", havingValue = "kafka")
public class KafkaBrokerAdapter extends AbstractMessageBrokerAdapter {
    private final KafkaTemplate<String, SimpliXMessageEvent> kafkaTemplate;
    private final ConsumerFactory<String, SimpliXMessageEvent> consumerFactory;
    private final List<KafkaMessageListenerContainer<String, SimpliXMessageEvent>> containers = new ArrayList<>();

    public KafkaBrokerAdapter(
            RetryTemplate retryTemplate,
            MeterRegistry meterRegistry,
            SimpliXEventProperties properties,
            ApplicationContext applicationContext,
            KafkaTemplate<String, SimpliXMessageEvent> kafkaTemplate,
            ConsumerFactory<String, SimpliXMessageEvent> consumerFactory) {
        super(retryTemplate, meterRegistry, properties, applicationContext);
        this.kafkaTemplate = kafkaTemplate;
        this.consumerFactory = consumerFactory;
        
        log.info("Initialized Kafka broker adapter");
        
        // Check connection status
        checkConnectionStatus();
        
        // Check default topics
        checkDefaultTopics();
    }
    
    /**
     * Checks the Kafka connection status and logs the result.
     * @return true if connected, false otherwise
     */
    private boolean checkConnectionStatus() {
        try {
            // Kafka doesn't provide a direct way to check connection status
            // We'll use a simple metadata request to verify connectivity
            kafkaTemplate.execute(operations -> {
                operations.partitionsFor("__connection_test__");
                return true;
            });
            log.info("Kafka connection status: CONNECTED");
            return true;
        } catch (Exception e) {
            log.error("Failed to check Kafka connection: {}", e.getMessage(), e);
            log.info("Kafka connection status: DISCONNECTED");
            return false;
        }
    }
    
    /**
     * Checks if default topics for frequently used channels exist.
     * This method ensures that topics exist for important channels like system-metrics.
     */
    @SuppressWarnings({"rawtypes"})
    private void checkDefaultTopics() {
        try {
            log.info("Checking default Kafka topics");
            
            // In Kafka, topics are created automatically when messages are sent
            // But we can check if important topics already exist
            
            // Get all registered receivers to check their channels
            Map<String, SimpliXEventReceiver> receivers = applicationContext.getBeansOfType(SimpliXEventReceiver.class);
            if (receivers == null || receivers.isEmpty()) {
                log.info("No SimpliXEventReceiver implementations found, skipping topic check");
                return;
            }
            
            log.info("Found {} SimpliXEventReceiver implementations", receivers.size());
            
            // Collect all unique channels from receivers
            List<String> channels = new ArrayList<>();
            for (Map.Entry<String, SimpliXEventReceiver> entry : receivers.entrySet()) {
                String name = entry.getKey();
                SimpliXEventReceiver receiver = entry.getValue();
                
                String[] supportedChannels = receiver.getSupportedChannels();
                if (supportedChannels == null || supportedChannels.length == 0) {
                    log.warn("Receiver {} does not support any channels", name);
                    continue;
                }
                
                log.info("Receiver {} supports channels: {}", name, String.join(", ", supportedChannels));
                
                for (String channel : supportedChannels) {
                    if (!channels.contains(channel)) {
                        channels.add(channel);
                    }
                }
            }
            
            if (channels.isEmpty()) {
                log.info("No channels found from receivers, skipping topic check");
                return;
            }
            
            log.info("Checking topics for channels: {}", String.join(", ", channels));
            
            // Check each channel as a potential topic
            for (String channel : channels) {
                try {
                    // Check if topic exists
                    boolean topicExists = kafkaTemplate.execute(operations -> {
                        try {
                            return operations.partitionsFor(channel) != null;
                        } catch (Exception e) {
                            log.debug("Error checking partitions for topic {}: {}", channel, e.getMessage());
                            return false;
                        }
                    });
                    
                    if (!topicExists) {
                        log.info("Topic '{}' does not exist. It will be created automatically when messages are sent.", 
                                channel);
                    } else {
                        log.info("Topic already exists: {}", channel);
                    }
                } catch (Exception e) {
                    log.warn("Error checking topic {}: {}", channel, e.getMessage());
                }
            }
            
            log.info("Default topics check completed");
        } catch (Exception e) {
            log.error("Failed to check default topics: {}", e.getMessage(), e);
        }
    }
    
    @PostConstruct
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void initializeReceivers() {
        log.info("Initializing SimpliXEventReceivers for Kafka");
        try {
            // Find all SimpliXEventReceiver implementations
            Map<String, SimpliXEventReceiver> receivers = applicationContext.getBeansOfType(SimpliXEventReceiver.class);
            log.info("Found {} SimpliXEventReceiver implementations", receivers.size());
            
            // Check if PayloadConverter bean exists
            PayloadConverter payloadConverter = null;
            try {
                payloadConverter = applicationContext.getBean(PayloadConverter.class);
                log.info("Found PayloadConverter bean");
            } catch (Exception e) {
                log.warn("PayloadConverter bean not found: {}. Will use direct casting for payloads.", e.getMessage());
            }
            
            // Set up subscriptions for each receiver's supported channels
            for (Map.Entry<String, SimpliXEventReceiver> entry : receivers.entrySet()) {
                String name = entry.getKey();
                SimpliXEventReceiver receiver = entry.getValue();
                
                String[] channels = receiver.getSupportedChannels();
                if (channels == null || channels.length == 0) {
                    log.warn("Receiver {} does not support any channels, skipping", name);
                    continue;
                }
                
                // Get the payload type from the receiver
                Class<?> payloadType = null;
                try {
                    payloadType = receiver.getPayloadType();
                    log.info("Receiver {} expects payload type: {}", name, payloadType.getName());
                } catch (Exception e) {
                    log.warn("Failed to get payload type for receiver {}: {}", name, e.getMessage());
                }
                
                // Set up subscriptions for each channel
                for (String channel : channels) {
                    log.info("Setting up subscription for receiver: {} on channel: {}", name, channel);
                    
                    // Create a final reference to the payload type and converter for the lambda
                    final Class<?> finalPayloadType = payloadType;
                    final PayloadConverter finalPayloadConverter = payloadConverter;
                    
                    // Set up subscription for the channel
                    subscribe(channel, event -> {
                        try {
                            Object convertedPayload = null;
                            
                            // Convert the payload to the expected type
                            if (finalPayloadConverter != null && finalPayloadType != null) {
                                // Use PayloadConverter if available
                                convertedPayload = finalPayloadConverter.convertPayload(event.getPayload(), finalPayloadType);
                            } else {
                                // Fallback to direct casting
                                convertedPayload = event.getPayload();
                                log.debug("Using direct payload without conversion for receiver: {} on channel: {}", 
                                        name, channel);
                            }
                            
                            // Forward the event to the receiver with the converted payload
                            receiver.onEvent(channel, event, convertedPayload);
                        } catch (Exception e) {
                            log.error("Error forwarding event to receiver: {} for channel: {}: {}", 
                                    name, channel, e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize receivers: {}", e.getMessage(), e);
        }
    }

    @Override
    public void send(SimpliXMessageEvent event) {
        String channel = event.getChannelName();
        sendMessage(channel, event,
            SimpliXMetricsConstants.KAFKA_OUTBOUND_ATTEMPTS,
            SimpliXMetricsConstants.KAFKA_OUTBOUND_SUCCESS,
            SimpliXMetricsConstants.KAFKA_OUTBOUND_ERRORS,
            () -> kafkaTemplate.send(channel, event)
        );
    }

    @Override
    protected void setupMessageListener(String channel, Consumer<Object> messageHandler) {
        ContainerProperties containerProps = new ContainerProperties(channel);
        containerProps.setMessageListener((MessageListener<String, SimpliXMessageEvent>) record -> {
            try {
                SimpliXMessageEvent event = record.value();
                if (record.headers() != null) {
                    Map<String, Object> headers = new HashMap<>();
                    record.headers().forEach(header -> 
                        headers.put(header.key(), new String(header.value())));
                    event.setHeaders(headers);
                }
                messageHandler.accept(event);
            } catch (Exception e) {
                log.error("Error processing Kafka message: {}", e.getMessage(), e);
            }
        });
        
        KafkaMessageListenerContainer<String, SimpliXMessageEvent> container =
            new KafkaMessageListenerContainer<>(consumerFactory, containerProps);
        
        container.setAutoStartup(true);
        container.start();
        
        containers.add(container);
        
        log.info("Subscription set up for channel: {}", channel);
    }

    @PreDestroy
    public void stopContainers() {
        log.info("Stopping all Kafka message listener containers");
        containers.forEach(container -> {
            try {
                container.stop();
                log.info("Stopped container for topics: {}", String.join(", ", container.getContainerProperties().getTopics()));
            } catch (Exception e) {
                log.error("Error stopping container: {}", e.getMessage(), e);
            }
        });
    }

    @SuppressWarnings("null")
    @Override
    protected void cleanupResources() {
        stopContainers();
        containers.clear();
        
        try {
            kafkaTemplate.execute(producer -> {
                producer.close();
                return null;
            });
            log.info("Successfully closed Kafka producer");
        } catch (Exception e) {
            log.warn("Error while closing Kafka producer: {}", e.getMessage(), e);
        }
    }

    @Override
    protected String getSubscribersMetricName() {
        return SimpliXMetricsConstants.KAFKA_SUBSCRIBERS;
    }

    @Override
    protected String getInboundProcessedMetricName() {
        return SimpliXMetricsConstants.KAFKA_INBOUND_PROCESSED;
    }

    @Override
    protected String getInboundErrorsMetricName() {
        return SimpliXMetricsConstants.KAFKA_INBOUND_ERRORS;
    }
} 