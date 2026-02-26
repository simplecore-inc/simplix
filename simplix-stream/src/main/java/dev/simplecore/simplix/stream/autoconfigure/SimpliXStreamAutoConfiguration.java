package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.config.StreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SimpliX Stream Auto Configuration.
 * <p>
 * Provides auto-configuration for real-time subscription system
 * with SSE and WebSocket transports.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(StreamProperties.class)
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "dev.simplecore.simplix.stream")
@EnableScheduling
@Slf4j
public class SimpliXStreamAutoConfiguration {

    public SimpliXStreamAutoConfiguration(StreamProperties properties) {
        log.info("SimpliX Stream module initialized (mode={})", properties.getMode());
    }

    // Core beans will be defined in separate configuration classes:
    // - SimpliXStreamCoreConfiguration (session, subscription, scheduler managers)
    // - SimpliXStreamSseConfiguration (SSE transport)
    // - SimpliXStreamWebSocketConfiguration (WebSocket transport)
    // - SimpliXStreamDistributedConfiguration (Redis distributed mode)
    // - SimpliXStreamAdminConfiguration (Admin API)

}
