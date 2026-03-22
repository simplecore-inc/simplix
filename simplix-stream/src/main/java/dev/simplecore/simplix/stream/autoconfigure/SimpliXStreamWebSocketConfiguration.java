package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import dev.simplecore.simplix.stream.transport.websocket.WebSocketStreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Auto-configuration for WebSocket transport.
 * <p>
 * Configures STOMP over WebSocket for real-time streaming.
 * Only activates when WebSocket starter is on the classpath.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker")
@ConditionalOnProperty(name = "simplix.stream.websocket.enabled", havingValue = "true", matchIfMissing = false)
public class SimpliXStreamWebSocketConfiguration {

    /**
     * WebSocket message broker configuration.
     */
    @Configuration
    @EnableWebSocketMessageBroker
    @ConditionalOnMissingBean(WebSocketMessageBrokerConfigurer.class)
    public static class StreamWebSocketConfig implements WebSocketMessageBrokerConfigurer {

        private final StreamProperties properties;

        public StreamWebSocketConfig(StreamProperties properties) {
            this.properties = properties;
        }

        @Override
        public void configureMessageBroker(MessageBrokerRegistry registry) {
            // Enable simple broker for subscriptions
            registry.enableSimpleBroker("/queue", "/topic");
            // Set application destination prefix
            registry.setApplicationDestinationPrefixes("/app");
            // Set user destination prefix
            registry.setUserDestinationPrefix("/user");

            log.info("Configured WebSocket message broker for stream");
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            // Register STOMP endpoint
            registry.addEndpoint("/ws/stream")
                    .setAllowedOriginPatterns("*")
                    .withSockJS();

            // Also register without SockJS fallback
            registry.addEndpoint("/ws/stream")
                    .setAllowedOriginPatterns("*");

            log.info("Registered WebSocket STOMP endpoint: /ws/stream");
        }
    }

    /**
     * WebSocket stream handler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public WebSocketStreamHandler webSocketStreamHandler(
            SessionManager sessionManager,
            SubscriptionManager subscriptionManager,
            SimpliXStreamDataCollectorRegistry collectorRegistry,
            BroadcastService broadcastService,
            StreamAuthorizationService authorizationService,
            StreamProperties properties,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            SessionValidator sessionValidator,
            ExecutorService sessionValidationExecutor,
            ScheduledExecutorService streamScheduledExecutor) {

        log.info("Creating WebSocket stream handler");
        return new WebSocketStreamHandler(
                sessionManager,
                subscriptionManager,
                collectorRegistry,
                broadcastService,
                authorizationService,
                properties,
                messagingTemplate,
                objectMapper,
                sessionValidator,
                sessionValidationExecutor,
                streamScheduledExecutor
        );
    }
}
