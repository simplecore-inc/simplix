package dev.simplecore.simplix.event.channel;

import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.DestinationResolver;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.MessagingException;

import java.util.concurrent.ConcurrentHashMap;
import dev.simplecore.simplix.event.service.SimpliXEventReceiver;
import dev.simplecore.simplix.event.model.SimpliXMessageEvent;
import dev.simplecore.simplix.event.spi.MessageBrokerAdapter;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.context.ApplicationContext;
import dev.simplecore.simplix.event.util.PayloadConverter;

/**
 * Creates a DirectChannel dynamically if the channel name doesn't exist.
 * When a message arrives at the channel, SimpliXEventReceiverService.onEvent() is called.
 */
public class SimpliXDynamicChannelResolver implements DestinationResolver<MessageChannel> {

    private final ConcurrentHashMap<String, MessageChannel> channels = new ConcurrentHashMap<>();
    private final List<SimpliXEventReceiver<?>> eventReceiverServices;
    private final MessageBrokerAdapter messageBrokerAdapter;
    private static final Logger log = LoggerFactory.getLogger(SimpliXDynamicChannelResolver.class);
    private final ApplicationContext applicationContext;

    public SimpliXDynamicChannelResolver(
            List<SimpliXEventReceiver<?>> eventReceiverServices,
            MessageBrokerAdapter messageBrokerAdapter,
            ApplicationContext applicationContext) {
        this.eventReceiverServices = eventReceiverServices;
        this.messageBrokerAdapter = messageBrokerAdapter;
        this.applicationContext = applicationContext;
    }

    @Override
    @NonNull
    public MessageChannel resolveDestination(@NonNull String channelName) {
        return channels.computeIfAbsent(channelName, name -> {
            DirectChannel channel = new DirectChannel();
            channel.subscribe(message -> {
                try {
                    if (message.getPayload() instanceof SimpliXMessageEvent) {
                        SimpliXMessageEvent event = (SimpliXMessageEvent) message.getPayload();
                        String targetChannel = event.getChannelName();
                        if (targetChannel == null) {
                            targetChannel = channelName;
                        }
                        event.setChannelName(targetChannel);
                        messageBrokerAdapter.send(event);
                    } else {
                        log.warn("Received non-SimpliXEvent message on channel {}: {}", channelName, message.getPayload());
                    }
                } catch (Exception e) {
                    log.error("Error processing message on channel {}: {}", channelName, e.getMessage(), e);
                    throw new MessagingException(message, "Failed to process message", e);
                }
            });

            messageBrokerAdapter.subscribe(channelName, event -> {
                for (SimpliXEventReceiver<?> service : eventReceiverServices) {
                    String[] supportedChannels = service.getSupportedChannels();
                    if (supportedChannels == null || supportedChannels.length == 0) {
                        continue;
                    }
                    
                    for (String supportedChannel : supportedChannels) {
                        if (!supportedChannel.equals(channelName)) {
                            continue;
                        }
                        
                        try {
                            Class<?> payloadType = service.getPayloadType();
                            PayloadConverter payloadConverter = applicationContext.getBean(PayloadConverter.class);
                            Object convertedPayload = payloadConverter.convertPayload(event.getPayload(), payloadType);
                            
                            @SuppressWarnings("unchecked")
                            SimpliXEventReceiver<Object> typedService = (SimpliXEventReceiver<Object>) service;
                            typedService.onEvent(channelName, event, convertedPayload);
                            break;
                        } catch (Exception e) {
                            log.error("Error processing event for receiver {} on channel {}: {}", 
                                service.getClass().getSimpleName(), channelName, e.getMessage(), e);
                        }
                    }
                }
            });

            return channel;
        });
    }
}