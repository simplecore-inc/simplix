package dev.simplecore.simplix.event.autoconfigure;

import dev.simplecore.simplix.event.channel.SimpliXDynamicChannelResolver;
import dev.simplecore.simplix.event.constant.SimpliXEventConstants;
import dev.simplecore.simplix.event.gateway.SimpliXEventGateway;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.properties.SimpliXEventProperties;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import dev.simplecore.simplix.event.spi.MessageBrokerProviderRegistry;
import dev.simplecore.simplix.event.util.PayloadConverter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.messaging.MessageChannel;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

/**
 * Auto-configuration for SimpliX Event system.
 * This class combines all configurations related to SimpliX Event system.
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(SimpliXEventProperties.class)
@EnableIntegration
@IntegrationComponentScan(basePackageClasses = SimpliXEventGateway.class)
@AutoConfigureAfter(IntegrationAutoConfiguration.class)
public class SimpliXEventAutoConfiguration {

    private final SimpliXEventProperties properties;
    private final ApplicationContext applicationContext;
    private MessageBrokerAdapter messageBrokerAdapter;

    //
    // Common Configuration Beans
    //

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        log.debug("Configuring default SimpleMeterRegistry");
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate retryTemplate(SimpliXEventProperties properties) {
        RetryTemplate template = new RetryTemplate();
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getRetry().getBackoffInitialInterval());
        backOffPolicy.setMultiplier(properties.getRetry().getBackoffMultiplier());
        backOffPolicy.setMaxInterval(properties.getRetry().getBackoffInitialInterval() * 10);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(properties.getRetry().getMaxAttempts());

        template.setBackOffPolicy(backOffPolicy);
        template.setRetryPolicy(retryPolicy);

        log.debug("Configured RetryTemplate with maxAttempts={}, initialInterval={}, multiplier={}",
                properties.getRetry().getMaxAttempts(),
                properties.getRetry().getBackoffInitialInterval(),
                properties.getRetry().getBackoffMultiplier());

        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public PayloadConverter payloadConverter() {
        log.debug("Created PayloadConverter");
        return new PayloadConverter();
    }

    //
    // Integration Configuration Beans
    //

    @Bean
    @ConditionalOnMissingBean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("simplix-event-scheduler-");
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageBrokerProviderRegistry messageBrokerProviderRegistry() {
        return new MessageBrokerProviderRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageBrokerAdapter messageBrokerAdapter(MessageBrokerProviderRegistry registry) {
        String brokerType = properties.getMqType();
        log.info("Creating message broker adapter for type: {}", brokerType);
        
        messageBrokerAdapter = registry.createAdapter(brokerType, applicationContext);
        
        if (messageBrokerAdapter == null) {
            log.warn("No suitable provider found for broker type: {}. Falling back to in-memory broker.", brokerType);
            messageBrokerAdapter = registry.createAdapter("in-memory", applicationContext);
            
            if (messageBrokerAdapter == null) {
                throw new IllegalStateException("Failed to create message broker adapter. No suitable provider found.");
            }
        }
        
        return messageBrokerAdapter;
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageChannel simpliXOutboundChannel() {
        // Create a direct channel without using the resolver
        // This prevents Spring AMQP from trying to find a queue for this channel
        log.info("Creating direct channel for outbound messages: {}", SimpliXEventConstants.DEFAULT_OUTBOUND_CHANNEL);
        org.springframework.integration.channel.DirectChannel channel = new org.springframework.integration.channel.DirectChannel();
        
        // Add a message handler to the channel
        channel.subscribe(message -> {
            try {
                if (messageBrokerAdapter != null) {
                    Object payload = message.getPayload();
                    if (payload instanceof SimpliXMessageEvent) {
                        messageBrokerAdapter.send((SimpliXMessageEvent) payload);
                    } else {
                        log.warn("Received non-SimpliXMessageEvent payload: {}", payload);
                    }
                } else {
                    log.warn("Message broker adapter not initialized");
                }
            } catch (Exception e) {
                log.error("Error processing outbound message", e);
                throw e;
            }
        });
        
        return channel;
    }
    
    //
    // Dynamic Routing Configuration Beans
    //
    
    @Bean
    public SimpliXDynamicChannelResolver simpliXDynamicChannelResolver(
            List<SimpliXEventReceiver<?>> eventReceiverServices,
            MessageBrokerAdapter messageBrokerAdapter) {
        log.info("Creating SimpliXDynamicChannelResolver with {} event receivers",
                eventReceiverServices != null ? eventReceiverServices.size() : 0);
        return new SimpliXDynamicChannelResolver(eventReceiverServices, messageBrokerAdapter, applicationContext);
    }

    @PostConstruct
    public void init() {
        log.info("Initialized SimpliXEventAutoConfiguration with mqType={}, channelType={}",
                properties.getMqType(), properties.getChannelType());
    }

    @PreDestroy
    public void cleanup() {
        if (messageBrokerAdapter != null) {
            log.info("Cleaning up message broker adapter");
            messageBrokerAdapter.cleanup();
        }
    }
} 