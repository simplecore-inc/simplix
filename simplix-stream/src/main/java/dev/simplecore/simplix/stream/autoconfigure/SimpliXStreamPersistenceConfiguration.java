package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.persistence.repository.StreamServerInstanceRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSessionRepository;
import dev.simplecore.simplix.stream.persistence.repository.StreamSubscriptionRepository;
import dev.simplecore.simplix.stream.persistence.service.DbSessionRegistry;
import dev.simplecore.simplix.stream.persistence.service.StreamServerManager;
import dev.simplecore.simplix.stream.persistence.service.StreamStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.UUID;

/**
 * Auto-configuration for database persistence layer.
 * <p>
 * Configures DB-based session registry, server instance management,
 * and statistics service. These components are available in all modes
 * (standalone and distributed) when JPA is configured.
 * <p>
 * DB persistence provides:
 * <ul>
 *   <li>Cross-server session restoration</li>
 *   <li>Global statistics across all servers</li>
 *   <li>Orphan session detection and cleanup</li>
 * </ul>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplix.stream.persistence.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@ConditionalOnBean(name = "entityManagerFactory")
@EnableJpaRepositories(basePackages = "dev.simplecore.simplix.stream.persistence.repository")
public class SimpliXStreamPersistenceConfiguration {

    @Value("${simplix.stream.server.instance-id:#{null}}")
    private String configuredInstanceId;

    /**
     * Persistence instance ID.
     * <p>
     * Used to identify this server instance in database records.
     * Generated if not configured.
     */
    @Bean
    @ConditionalOnMissingBean(name = "persistenceInstanceId")
    public String persistenceInstanceId() {
        String id = configuredInstanceId != null ? configuredInstanceId
                : UUID.randomUUID().toString().substring(0, 8);
        log.info("Persistence instance ID: {}", id);
        return id;
    }

    /**
     * DB-based session registry.
     * <p>
     * Provides session persistence and cross-server session restoration.
     * This is the primary SessionRegistry when JPA is available,
     * replacing LocalSessionRegistry or RedisSessionRegistry.
     * <p>
     * Note: initialize() is called via @PostConstruct in DbSessionRegistry.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(SessionRegistry.class)
    public DbSessionRegistry dbSessionRegistry(
            StreamSessionRepository sessionRepository,
            StreamSubscriptionRepository subscriptionRepository,
            StreamProperties properties,
            ObjectMapper objectMapper,
            String persistenceInstanceId) {

        log.info("Creating DB session registry (cross-server restoration enabled)");
        return new DbSessionRegistry(
                sessionRepository, subscriptionRepository, properties, objectMapper, persistenceInstanceId);
    }

    /**
     * Server instance manager.
     * <p>
     * Manages server lifecycle, heartbeats, and dead server detection.
     * Automatically cleans up orphan sessions when servers fail.
     * <p>
     * Note: initialize() is called via @PostConstruct in StreamServerManager.
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamServerManager streamServerManager(
            StreamServerInstanceRepository serverRepository,
            StreamSessionRepository sessionRepository,
            StreamProperties properties) {

        log.info("Creating stream server manager");
        return new StreamServerManager(serverRepository, sessionRepository, properties);
    }

    /**
     * Statistics service.
     * <p>
     * Provides aggregated statistics across all server instances
     * including session counts, subscription counts, and server health.
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamStatisticsService streamStatisticsService(
            StreamSessionRepository sessionRepository,
            StreamSubscriptionRepository subscriptionRepository,
            StreamServerInstanceRepository serverRepository) {

        log.info("Creating stream statistics service");
        return new StreamStatisticsService(sessionRepository, subscriptionRepository, serverRepository);
    }
}
