package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.aspect.ModifyingQueryCacheEvictionAspect;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
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

/**
 * Spring Boot Auto-configuration for SimpliX Hibernate Cache Management.
 *
 * <p>This configuration provides:</p>
 * <ul>
 *   <li>{@code @EvictCache} annotation support for @Modifying queries</li>
 *   <li>Manual cache eviction API via {@link HibernateCacheManager}</li>
 * </ul>
 *
 * <p>For save()/delete() operations, Hibernate's native L2 cache management handles
 * cache invalidation automatically. This module focuses on @Modifying queries which
 * bypass Hibernate's entity event system.</p>
 *
 * @see ModifyingQueryCacheEvictionAspect
 * @see HibernateCacheManager
 */
@Slf4j
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, org.hibernate.Cache.class})
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnProperty(prefix = "simplix.hibernate.cache", name = "disabled", havingValue = "false", matchIfMissing = true)
@EnableConfigurationProperties(HibernateCacheProperties.class)
@EnableAspectJAutoProxy
public class SimpliXHibernateCacheAutoConfiguration {

    private final HibernateCacheProperties properties;

    public SimpliXHibernateCacheAutoConfiguration(HibernateCacheProperties properties) {
        this.properties = properties;
        log.info("ℹ SimpliX Hibernate Cache Module Loading...");
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    public HibernateCacheManager hibernateCacheManager(EntityManagerFactory entityManagerFactory) {
        log.info("✔ Configuring Hibernate Cache Manager");

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
        return new TransactionAwareCacheEvictionCollector(eventPublisher);
    }

    /**
     * Handler for post-commit cache eviction events.
     * Executes actual cache eviction after transaction successfully commits.
     */
    @Bean
    @ConditionalOnBean(CacheEvictionStrategy.class)
    public PostCommitCacheEvictionHandler postCommitCacheEvictionHandler(
            CacheEvictionStrategy evictionStrategy) {
        log.info("✔ Configuring Post-Commit Cache Eviction Handler");
        return new PostCommitCacheEvictionHandler(evictionStrategy);
    }

    /**
     * AOP aspect for @EvictCache annotation handling.
     * Requires explicit @EvictCache annotation to specify which entities to evict.
     */
    @Bean
    @ConditionalOnBean(TransactionAwareCacheEvictionCollector.class)
    public ModifyingQueryCacheEvictionAspect modifyingQueryCacheEvictionAspect(
            TransactionAwareCacheEvictionCollector evictionCollector) {
        log.info("✔ Configuring @EvictCache Annotation Aspect");
        return new ModifyingQueryCacheEvictionAspect(evictionCollector);
    }

    @Bean
    public EntityCacheScanner entityCacheScanner() {
        log.info("✔ Configuring Entity Cache Scanner");
        return new EntityCacheScanner();
    }

    @Bean
    @ConditionalOnBean(HibernateCacheManager.class)
    public CacheEvictionStrategy cacheEvictionStrategy(HibernateCacheManager cacheManager) {
        log.info("✔ Configuring Cache Eviction Strategy");
        return new CacheEvictionStrategy(cacheManager);
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
            log.info("⚠ Hibernate Cache Management is explicitly disabled");
            return;
        }

        ApplicationContext context = event.getApplicationContext();
        EntityCacheScanner scanner = context.getBean(EntityCacheScanner.class);

        String[] basePackages = properties.getScanPackages();
        if (basePackages == null || basePackages.length == 0) {
            basePackages = detectBasePackages(context);
        }

        scanner.scanForCachedEntities(basePackages);

        log.info("✔ SimpliX Hibernate Cache Module activated");
        log.info("  @EvictCache annotation support enabled for @Modifying queries");
    }

    private String[] detectBasePackages(ApplicationContext context) {
        try {
            String mainPackage = context.getEnvironment()
                    .getProperty("spring.application.package");
            if (mainPackage != null) {
                return new String[]{mainPackage};
            }
            return new String[]{""};
        } catch (Exception e) {
            log.debug("Could not detect base packages, scanning all: {}", e.getMessage());
            return new String[]{""};
        }
    }

    /**
     * Reset static holder references when ApplicationContext is closed.
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed(ContextClosedEvent event) {
        HibernateCacheHolder.reset();
        log.info("✔ HibernateCacheHolder reset on context close");
    }
}
