package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import dev.simplecore.simplix.stream.security.SessionValidator;
import dev.simplecore.simplix.stream.security.StreamAuthorizationService;
import dev.simplecore.simplix.stream.transport.sse.SseStreamController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Auto-configuration for SSE transport.
 * <p>
 * Configures the SSE controller and async support for Server-Sent Events.
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXStreamSseConfiguration implements WebMvcConfigurer {

    private final StreamProperties properties;

    public SimpliXStreamSseConfiguration(StreamProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        // Configure async timeout for SSE connections
        long timeoutMs = properties.getSession().getTimeout().toMillis();
        configurer.setDefaultTimeout(timeoutMs);
        log.debug("Configured async timeout: {}ms", timeoutMs);
    }

    /**
     * SSE stream controller bean.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "simplix.stream.mode", havingValue = "local", matchIfMissing = true)
    public SseStreamController sseStreamController(
            SessionManager sessionManager,
            SubscriptionManager subscriptionManager,
            SimpliXStreamDataCollectorRegistry collectorRegistry,
            LocalBroadcaster broadcaster,
            StreamAuthorizationService authorizationService,
            StreamProperties properties,
            ObjectMapper objectMapper,
            ScheduledExecutorService streamScheduledExecutor,
            SessionValidator sessionValidator,
            ExecutorService sessionValidationExecutor) {

        log.info("Creating SSE stream controller");
        return new SseStreamController(
                sessionManager,
                subscriptionManager,
                collectorRegistry,
                broadcaster,
                authorizationService,
                properties,
                objectMapper,
                streamScheduledExecutor,
                sessionValidator,
                sessionValidationExecutor
        );
    }
}
