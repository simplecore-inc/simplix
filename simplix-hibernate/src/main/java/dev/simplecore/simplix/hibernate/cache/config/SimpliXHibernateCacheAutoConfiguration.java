package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.admin.CacheAdminController;
import dev.simplecore.simplix.hibernate.cache.aspect.AutoCacheEvictionAspect;
import dev.simplecore.simplix.hibernate.cache.aspect.ModifyingQueryCacheEvictionAspect;
import dev.simplecore.simplix.hibernate.cache.batch.BatchEvictionOptimizer;
import dev.simplecore.simplix.hibernate.cache.cluster.ClusterSyncMonitor;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.core.QueryCacheManager;
import dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler;
import dev.simplecore.simplix.hibernate.cache.monitoring.EvictionMetrics;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProvider;
import dev.simplecore.simplix.hibernate.cache.provider.CacheProviderFactory;
import dev.simplecore.simplix.hibernate.cache.provider.LocalCacheProvider;
import dev.simplecore.simplix.hibernate.cache.resilience.EvictionRetryHandler;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * Spring Boot Auto-configuration for SimpliX Hibernate Cache Management.
 *
 * <p>This configuration automatically activates when Hibernate is present and sets up
 * transaction-aware cache eviction. Cache eviction only occurs after transaction commit,
 * preventing cache-database inconsistency on rollback.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 * Entity Change
 *     |
 *     v
 * HibernateIntegrator (POST_COMMIT events)
 *     |
 *     v
 * TransactionAwareCacheEvictionCollector
 *     |
 *     v (after commit)
 * PostCommitCacheEvictionHandler
 *     |
 *     v
 * CacheEvictionStrategy (local + distributed)
 * </pre>
 *
 * @see TransactionAwareCacheEvictionCollector
 * @see PostCommitCacheEvictionHandler
 * @see CacheEvictionStrategy
 */
@Slf4j
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, org.hibernate.Cache.class})
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "simplix.hibernate.cache", name = "disabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(HibernateCacheProperties.class)
@EnableAspectJAutoProxy
@EnableScheduling
public class SimpliXHibernateCacheAutoConfiguration {

    private final HibernateCacheProperties properties;

    public SimpliXHibernateCacheAutoConfiguration(HibernateCacheProperties properties) {
        this.properties = properties;
        log.info("ℹ SimpliX Hibernate Cache Auto-Management Module Loading...");
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    public HibernateCacheManager hibernateCacheManager(EntityManagerFactory entityManagerFactory) {
        log.info("✔ Configuring Hibernate Cache Manager");

        // Store cache reference for HibernateIntegrator
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        HibernateCacheHolder.setCache(sessionFactory.getCache());

        return new HibernateCacheManager(entityManagerFactory);
    }

    /**
     * Transaction-aware collector for pending cache evictions.
     * Collects evictions during transaction and publishes event after commit.
     */
    @Bean
    public TransactionAwareCacheEvictionCollector transactionAwareCacheEvictionCollector(
            ApplicationEventPublisher eventPublisher) {
        log.info("✔ Configuring Transaction-Aware Cache Eviction Collector");
        TransactionAwareCacheEvictionCollector collector =
                new TransactionAwareCacheEvictionCollector(eventPublisher);

        // Store collector reference for HibernateIntegrator (SPI access)
        HibernateCacheHolder.setEvictionCollector(collector);

        return collector;
    }

    /**
     * Handler for post-commit cache eviction events.
     * Executes actual cache eviction after transaction successfully commits.
     */
    @Bean
    @ConditionalOnBean({CacheEvictionStrategy.class, EvictionMetrics.class})
    public PostCommitCacheEvictionHandler postCommitCacheEvictionHandler(
            CacheEvictionStrategy evictionStrategy,
            EvictionMetrics evictionMetrics) {
        log.info("✔ Configuring Post-Commit Cache Eviction Handler");
        return new PostCommitCacheEvictionHandler(evictionStrategy, evictionMetrics);
    }

    /**
     * AOP aspect for @Modifying query cache eviction.
     * Automatically detects entity from JPQL and evicts cache.
     * @EvictCache is now optional - entity is auto-extracted from @Query.
     */
    @Bean
    @ConditionalOnBean({TransactionAwareCacheEvictionCollector.class, EntityCacheScanner.class})
    public ModifyingQueryCacheEvictionAspect modifyingQueryCacheEvictionAspect(
            TransactionAwareCacheEvictionCollector evictionCollector,
            EntityCacheScanner entityCacheScanner) {
        log.info("✔ Configuring @Modifying Query Cache Eviction Aspect (auto-detection enabled)");
        return new ModifyingQueryCacheEvictionAspect(evictionCollector, entityCacheScanner);
    }

    @Bean
    public EntityCacheScanner entityCacheScanner() {
        log.info("✔ Configuring Entity Cache Scanner");
        return new EntityCacheScanner();
    }

    @Bean
    public QueryCacheManager queryCacheManager(ApplicationContext applicationContext) {
        // Pass ApplicationContext via constructor injection
        log.info("✔ Configuring Query Cache Manager");
        return new QueryCacheManager(applicationContext);
    }

    @Bean
    public EvictionMetrics evictionMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        if (meterRegistry != null) {
            log.info("✔ Configuring Eviction Metrics with Micrometer");
            return new EvictionMetrics(meterRegistry);
        } else {
            log.info("✔ Configuring Eviction Metrics without Micrometer");
            return new EvictionMetrics();
        }
    }

    @Bean
    @ConditionalOnBean(HibernateCacheManager.class)
    public AutoCacheEvictionAspect autoCacheEvictionAspect(
            HibernateCacheManager cacheManager,
            QueryCacheManager queryCacheManager) {
        log.info("✔ Enabling automatic AOP-based cache eviction");
        return new AutoCacheEvictionAspect(cacheManager, queryCacheManager);
    }

    @Bean
    public LocalCacheProvider localCacheProvider() {
        log.info("✔ Configuring Local Cache Provider");
        return new LocalCacheProvider();
    }

    @Bean
    public CacheProviderFactory cacheProviderFactory(List<CacheProvider> providers) {
        log.info("✔ Configuring Cache Provider Factory");
        return new CacheProviderFactory(providers);
    }

    @Bean
    @ConditionalOnBean(HibernateCacheManager.class)
    public CacheEvictionStrategy cacheEvictionStrategy(
            HibernateCacheManager cacheManager,
            CacheProviderFactory providerFactory,
            HibernateCacheProperties cacheProperties) {
        log.info("✔ Configuring Cache Eviction Strategy");
        CacheEvictionStrategy strategy = new CacheEvictionStrategy(cacheManager, providerFactory, cacheProperties);

        // Store strategy reference for fallback eviction when event publishing fails
        HibernateCacheHolder.setEvictionStrategy(strategy);

        return strategy;
    }

    @Bean
    public BatchEvictionOptimizer batchEvictionOptimizer(CacheProviderFactory providerFactory) {
        log.info("✔ Configuring Batch Eviction Optimizer");
        return new BatchEvictionOptimizer(providerFactory);
    }

    @Bean
    public ClusterSyncMonitor clusterSyncMonitor(CacheProviderFactory providerFactory) {
        log.info("✔ Configuring Cluster Sync Monitor");
        return new ClusterSyncMonitor(providerFactory);
    }

    @Bean
    public EvictionRetryHandler evictionRetryHandler(
            CacheProviderFactory providerFactory,
            HibernateCacheProperties cacheProperties) {
        log.info("✔ Configuring Eviction Retry Handler");
        return new EvictionRetryHandler(providerFactory, cacheProperties);
    }

    @Bean
    @ConditionalOnBean({HibernateCacheManager.class, CacheEvictionStrategy.class})
    public CacheAdminController cacheAdminController(
            HibernateCacheManager cacheManager,
            CacheEvictionStrategy evictionStrategy,
            EvictionMetrics metrics,
            ClusterSyncMonitor clusterMonitor,
            BatchEvictionOptimizer batchOptimizer,
            EvictionRetryHandler retryHandler) {
        log.info("✔ Configuring Cache Admin Controller");
        return new CacheAdminController(cacheManager, evictionStrategy, metrics, clusterMonitor, batchOptimizer, retryHandler);
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    public HibernateCacheInitializer hibernateCacheInitializer(
            HibernateCacheProperties properties,
            EntityManagerFactory entityManagerFactory,
            EntityCacheScanner entityScanner,
            ApplicationContext applicationContext) {
        return new HibernateCacheInitializer(properties, entityManagerFactory, entityScanner, applicationContext);
    }

    /**
     * Auto-scan for cached entities on startup
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (properties.isDisabled()) {
            log.info("⚠ Hibernate Cache Auto-Management is explicitly disabled");
            return;
        }

        ApplicationContext context = event.getApplicationContext();
        EntityCacheScanner scanner = context.getBean(EntityCacheScanner.class);

        // Scan for all cached entities
        String[] basePackages = properties.getScanPackages();
        if (basePackages == null || basePackages.length == 0) {
            // Auto-detect packages from @EntityScan or default packages
            basePackages = detectBasePackages(context);
        }

        scanner.scanForCachedEntities(basePackages);

        log.info("✔ Hibernate Cache Auto-Management activated (transaction-aware mode)");
        log.info("  Cache eviction only occurs after transaction commit");
    }

    private String[] detectBasePackages(ApplicationContext context) {
        // Try to detect from application's package
        try {
            String mainPackage = context.getEnvironment()
                    .getProperty("spring.application.package");
            if (mainPackage != null) {
                return new String[]{mainPackage};
            }

            // Fallback to scanning all packages
            return new String[]{""};
        } catch (Exception e) {
            log.debug("Could not detect base packages, scanning all: {}", e.getMessage());
            return new String[]{""};
        }
    }

    /**
     * Reset static holder references when ApplicationContext is closed.
     * This allows new beans to be registered on context refresh (e.g., test suite).
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        HibernateCacheHolder.reset();
        log.info("✔ HibernateCacheHolder reset on context close");
    }
}
