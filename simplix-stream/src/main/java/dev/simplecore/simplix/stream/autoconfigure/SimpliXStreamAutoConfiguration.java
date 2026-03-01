package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.config.StreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SimpliX Stream Auto Configuration.
 *
 * <p>Provides auto-configuration for real-time subscription system
 * with SSE and WebSocket transports.
 *
 * <p>The following packages are excluded from component scanning:
 * <ul>
 *   <li>{@code autoconfigure} — registered via {@code AutoConfiguration.imports} with
 *       explicit ordering and conditional annotations. Dual registration breaks ordering.</li>
 *   <li>{@code transport.websocket} — contains {@code @Controller} beans that require
 *       WebSocket on the classpath. Managed by {@code SimpliXStreamWebSocketConfiguration}.</li>
 *   <li>{@code persistence} — requires JPA infrastructure. Managed by
 *       {@code SimpliXStreamPersistenceConfiguration}.</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(StreamProperties.class)
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(
        basePackages = "dev.simplecore.simplix.stream",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "dev\\.simplecore\\.simplix\\.stream\\.autoconfigure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "dev\\.simplecore\\.simplix\\.stream\\.transport\\.websocket\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "dev\\.simplecore\\.simplix\\.stream\\.persistence\\..*")
        }
)
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
