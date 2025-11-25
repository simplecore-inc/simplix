package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * Initializes Hibernate cache configuration
 */
@Slf4j
public class HibernateCacheInitializer {

    private final HibernateCacheProperties properties;
    private final EntityManagerFactory entityManagerFactory;
    private final EntityCacheScanner entityScanner;
    private final ApplicationContext applicationContext;

    public HibernateCacheInitializer(HibernateCacheProperties properties,
                                    EntityManagerFactory entityManagerFactory,
                                    EntityCacheScanner entityScanner,
                                    ApplicationContext applicationContext) {
        this.properties = properties;
        this.entityManagerFactory = entityManagerFactory;
        this.entityScanner = entityScanner;
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        log.info("ℹ Initializing Hibernate Cache Module");

        logCacheConfiguration();
        verifyCacheConfiguration();

        log.info("✔ Hibernate Cache Module initialized successfully");
    }

    private void logCacheConfiguration() {
        log.info("Cache Configuration:");
        log.info("  Mode: {}", properties.getMode());
        log.info("  Query Cache Auto-Eviction: {}", properties.isQueryCacheAutoEviction());
        log.info("  Auto-Detection Strategy: {}", properties.isAutoDetectEvictionStrategy());
        log.info("  Node ID: {}", properties.getNodeId());

        if (properties.getRedis().isPubSubEnabled()) {
            log.info("  Redis Channel: {}", properties.getRedis().getChannel());
        }
    }

    private void verifyCacheConfiguration() {
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            Map<String, Object> properties = sessionFactory.getProperties();

            boolean secondLevelCacheEnabled = Boolean.parseBoolean(
                    String.valueOf(properties.get(AvailableSettings.USE_SECOND_LEVEL_CACHE))
            );

            boolean queryCacheEnabled = Boolean.parseBoolean(
                    String.valueOf(properties.get(AvailableSettings.USE_QUERY_CACHE))
            );

            if (!secondLevelCacheEnabled) {
                log.warn("⚠ Second-level cache is disabled in Hibernate configuration");
            }

            if (!queryCacheEnabled && this.properties.isQueryCacheAutoEviction()) {
                log.warn("⚠ Query cache is disabled but auto-eviction is enabled");
            }

            String cacheProvider = String.valueOf(properties.get(AvailableSettings.CACHE_REGION_FACTORY));
            log.info("  Cache Provider: {}", cacheProvider);

        } catch (Exception e) {
            log.warn("⚠ Could not verify Hibernate cache configuration: {}", e.getMessage());
        }
    }
}