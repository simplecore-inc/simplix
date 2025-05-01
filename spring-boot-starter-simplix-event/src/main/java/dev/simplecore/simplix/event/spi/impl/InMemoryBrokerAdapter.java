package dev.simplecore.simplix.event.spi.impl;

import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.constant.SimpliXMetricsConstants;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * In-Memory Broker Adapter
 * 
 * A lightweight message broker implementation that stores messages in memory.
 * Suitable for single-instance applications and development environments.
 * 
 * Configuration example in application.yml:
 * ```yaml
 * simplix:
 *   event:
 *     # Message broker type configuration (default: in-memory)
 *     mq-type: in-memory
 *     in-memory:
 *       # Time in milliseconds after which a message is considered expired
 *       # Expired messages will be automatically removed during cleanup
 *       message-expiry-ms: 3600000  # 1 hour
 *       
 *       # Interval in milliseconds between cleanup operations
 *       cleanup-interval-ms: 60000  # 1 minute
 * 
 * # Features:
 * # 1. Thread-Safe: Uses ConcurrentHashMap for thread-safe message handling
 * # 2. In-Memory: Fast message processing without disk I/O
 * # 3. Auto Cleanup: Automatic removal of expired messages
 * 
 * # Use Cases:
 * # 1. Development and testing environments
 * # 2. Single instance applications
 * # 3. Scenarios where persistence is not required
 * ```
 * 
 * Required dependencies:
 * ```gradle
 * # No additional dependencies required
 * # Uses Java built-in concurrent collections
 * ```
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "simplix.event.mq-type", havingValue = "in-memory")
public class InMemoryBrokerAdapter extends AbstractMessageBrokerAdapter {
    
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ThreadPoolTaskScheduler scheduler;

    private final Map<String, Set<Consumer<Object>>> channelHandlers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> cleanupTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> channelQueueSizes = new ConcurrentHashMap<>();
    
    // Configuration values
    private final long messageExpiryMs = 3600000; // 1 hour
    private final long cleanupIntervalMs = 60000; // 1 minute
    private final int maxChannelQueueSize = 1000; // Channel-specific queue size limit
    
    /**
     * Internal event class containing channel and message information
     */
    public static class ChannelMessageEvent {
        private final String channel;
        private final SimpliXMessageEvent message;
        private final Instant createdAt;
        
        public ChannelMessageEvent(String channel, SimpliXMessageEvent message) {
            this.channel = channel;
            this.message = message;
            this.createdAt = Instant.now();
        }
        
        public String getChannel() {
            return channel;
        }
        
        public SimpliXMessageEvent getMessage() {
            return message;
        }
        
        public Instant getCreatedAt() {
            return createdAt;
        }
    }
    
    public InMemoryBrokerAdapter(
            RetryTemplate retryTemplate,
            MeterRegistry meterRegistry,
            SimpliXEventProperties properties,
            ApplicationContext applicationContext,
            ApplicationEventPublisher eventPublisher,
            ThreadPoolTaskScheduler scheduler) {
        super(retryTemplate, meterRegistry, properties, applicationContext);
        this.eventPublisher = eventPublisher;
        this.scheduler = scheduler;
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-processor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        this.taskExecutor = executor;
    }
    
    @Override
    public void send(SimpliXMessageEvent event) {
        String channel = event.getChannelName();
        
        // Track queue size per channel
        AtomicInteger channelSize = channelQueueSizes.computeIfAbsent(channel, k -> new AtomicInteger(0));
        if (channelSize.incrementAndGet() > maxChannelQueueSize) {
            log.warn("Channel queue size limit reached for channel: {}", channel);
        }
        
        sendMessage(channel, event,
            SimpliXMetricsConstants.INMEMORY_OUTBOUND_ATTEMPTS,
            SimpliXMetricsConstants.INMEMORY_OUTBOUND_SUCCESS,
            SimpliXMetricsConstants.INMEMORY_OUTBOUND_ERRORS,
            () -> {
                try {
                    eventPublisher.publishEvent(new ChannelMessageEvent(channel, event));
                } catch (Exception e) {
                    channelSize.decrementAndGet();
                    throw e;
                }
            }
        );
    }
    
    /**
     * Event listener - Process all channel message events
     */
    @EventListener
    public void handleChannelMessageEvent(ChannelMessageEvent event) {
        try {
            // Check if message has expired
            if (isMessageExpired(event.getCreatedAt())) {
                log.warn("Message for channel {} expired and will be discarded", event.getChannel());
                channelQueueSizes.get(event.getChannel()).decrementAndGet();
                return;
            }
            
            // Find subscribers for the channel
            Set<Consumer<Object>> handlers = channelHandlers.get(event.getChannel());
            if (handlers != null && !handlers.isEmpty()) {
                // Deliver message to all subscribers (publish-subscribe pattern)
                boolean processed = false;
                
                for (Consumer<Object> handler : handlers) {
                    // Process message asynchronously for each subscriber
                    taskExecutor.execute(() -> {
                        try {
                            handler.accept(event.getMessage());
                            meterRegistry.counter(getInboundProcessedMetricName(),
                                SimpliXMetricsConstants.METRIC_CHANNEL, event.getChannel()).increment();
                        } catch (Exception e) {
                            meterRegistry.counter(getInboundErrorsMetricName(),
                                SimpliXMetricsConstants.METRIC_CHANNEL, event.getChannel()).increment();
                            log.error("Error processing message for channel {}: {}", 
                                    event.getChannel(), e.getMessage(), e);
                        }
                    });
                    processed = true;
                }
                
                // Decrease queue size after message has been delivered to all subscribers
                if (processed) {
                    channelQueueSizes.get(event.getChannel()).decrementAndGet();
                }
            } else {
                log.warn("No handlers found for channel: {}", event.getChannel());
                channelQueueSizes.get(event.getChannel()).decrementAndGet();
            }
        } catch (RejectedExecutionException e) {
            // Process in current thread if thread pool is full (backpressure mechanism)
            log.warn("Thread pool is full, processing in the current thread");
            try {
                Set<Consumer<Object>> handlers = channelHandlers.get(event.getChannel());
                if (handlers != null && !handlers.isEmpty()) {
                    // Use only the first subscriber when processing in current thread
                    handlers.iterator().next().accept(event.getMessage());
                }
            } catch (Exception ex) {
                log.error("Error processing message in current thread: {}", ex.getMessage(), ex);
            } finally {
                channelQueueSizes.get(event.getChannel()).decrementAndGet();
            }
        } catch (Exception e) {
            log.error("Unexpected error handling message event: {}", e.getMessage(), e);
            channelQueueSizes.get(event.getChannel()).decrementAndGet();
        }
    }
    
    private boolean isMessageExpired(Instant createdAt) {
        return Instant.now().minusMillis(messageExpiryMs).isAfter(createdAt);
    }
    
    @Override
    protected void setupMessageListener(String channel, Consumer<Object> messageHandler) {
        // Get or create the set of subscribers for the channel
        Set<Consumer<Object>> handlers = channelHandlers.computeIfAbsent(channel, 
                k -> new CopyOnWriteArraySet<>());
        
        // Add new subscriber
        handlers.add(messageHandler);
        
        log.info("Registered message handler for channel: {}, total handlers: {}", 
                channel, handlers.size());
    }
    
    /**
     * Remove a subscriber from a specific channel
     */
    public void removeMessageListener(String channel, Consumer<Object> messageHandler) {
        Set<Consumer<Object>> handlers = channelHandlers.get(channel);
        if (handlers != null) {
            handlers.remove(messageHandler);
            if (handlers.isEmpty()) {
                channelHandlers.remove(channel);
            }
            log.info("Removed message handler for channel: {}", channel);
        }
    }
    
    @PostConstruct
    public void startCleanupTask() {
        // Schedule periodic task to monitor queue status
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> {
                log.debug("Current queue size: {}", channelQueueSizes.values().stream().mapToInt(AtomicInteger::get).sum());
                
                // Log subscriber status (for debugging)
                if (log.isDebugEnabled()) {
                    channelHandlers.forEach((channel, handlers) -> {
                        log.debug("Channel: {}, Subscribers: {}", channel, handlers.size());
                    });
                }
            },
            cleanupIntervalMs
        );
        cleanupTasks.put("queueMonitor", future);
    }
    
    @PreDestroy
    public void stopScheduler() {
        log.info("Shutting down In-Memory Broker Adapter");
        
        // Cancel all cleanup tasks
        cleanupTasks.values().forEach(future -> {
            if (future != null && !future.isCancelled()) {
                future.cancel(true);
            }
        });
        cleanupTasks.clear();
        
        // Shutdown task executor
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
        
        cleanupResources();
    }
    
    @Override
    protected void cleanupResources() {
        channelHandlers.clear();
        channelQueueSizes.clear();
    }
    
    @Override
    protected String getSubscribersMetricName() {
        return SimpliXMetricsConstants.INMEMORY_SUBSCRIBERS;
    }
    
    @Override
    protected String getInboundProcessedMetricName() {
        return SimpliXMetricsConstants.INMEMORY_INBOUND_PROCESSED;
    }
    
    @Override
    protected String getInboundErrorsMetricName() {
        return SimpliXMetricsConstants.INMEMORY_INBOUND_ERRORS;
    }
} 