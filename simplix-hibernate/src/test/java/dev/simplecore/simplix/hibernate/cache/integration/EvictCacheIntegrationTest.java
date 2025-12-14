package dev.simplecore.simplix.hibernate.cache.integration;

import dev.simplecore.simplix.hibernate.cache.annotation.EvictCache;
import dev.simplecore.simplix.hibernate.cache.aspect.ModifyingQueryCacheEvictionAspect;
import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheHolder;
import dev.simplecore.simplix.hibernate.cache.config.HibernateCacheProperties;
import dev.simplecore.simplix.hibernate.cache.config.SimpliXHibernateCacheAutoConfiguration;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Cache;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import org.junit.jupiter.api.Disabled;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for @EvictCache annotation.
 * Verifies that cache eviction works correctly with real Hibernate L2 cache.
 *
 * <p>Note: These tests require a full Spring Boot context with Hibernate L2 cache enabled.
 * They are disabled by default due to complex JCache configuration requirements.
 * To run these tests:</p>
 * <ul>
 *   <li>Configure EhCache JCache provider properly</li>
 *   <li>Ensure ehcache.xml is on the test classpath</li>
 *   <li>Remove the @Disabled annotation</li>
 * </ul>
 */
@Disabled("Integration tests require full JCache/EhCache setup - enable manually when needed")
@DataJpaTest
@Import(EvictCacheIntegrationTest.TestCacheConfig.class)
@EnableJpaRepositories(basePackageClasses = TestCachedEntityRepository.class)
@EntityScan(basePackageClasses = TestCachedEntity.class)
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
        "spring.jpa.properties.hibernate.cache.use_query_cache=true",
        "spring.jpa.properties.hibernate.cache.region.factory_class=jcache",
        "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "simplix.hibernate.cache.disabled=false",
        "simplix.hibernate.cache.query-cache-auto-eviction=true"
})
@DisplayName("@EvictCache Integration Tests")
class EvictCacheIntegrationTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestCacheConfig {

        @Bean
        public HibernateCacheProperties hibernateCacheProperties() {
            return new HibernateCacheProperties();
        }

        @Bean
        public HibernateCacheManager hibernateCacheManager(EntityManagerFactory entityManagerFactory) {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
            HibernateCacheHolder.setCache(sessionFactory.getCache());
            return new HibernateCacheManager(entityManagerFactory);
        }

        @Bean
        public EntityCacheScanner entityCacheScanner() {
            return new EntityCacheScanner();
        }

        @Bean
        public CacheEvictionStrategy cacheEvictionStrategy(HibernateCacheManager cacheManager) {
            return new CacheEvictionStrategy(cacheManager);
        }

        @Bean
        public TransactionAwareCacheEvictionCollector transactionAwareCacheEvictionCollector(
                ApplicationEventPublisher eventPublisher) {
            return new TransactionAwareCacheEvictionCollector(eventPublisher);
        }

        @Bean
        public PostCommitCacheEvictionHandler postCommitCacheEvictionHandler(
                CacheEvictionStrategy evictionStrategy) {
            return new PostCommitCacheEvictionHandler(evictionStrategy);
        }

        @Bean
        public ModifyingQueryCacheEvictionAspect modifyingQueryCacheEvictionAspect(
                TransactionAwareCacheEvictionCollector evictionCollector) {
            return new ModifyingQueryCacheEvictionAspect(evictionCollector);
        }
    }

    @Autowired
    private TestCachedEntityRepository repository;

    @Autowired
    private HibernateCacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Clear all caches before each test
        cacheManager.evictAll();
        // Clean up test data
        repository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Nested
    @DisplayName("Basic @EvictCache functionality")
    class BasicEvictCacheTests {

        @Test
        @DisplayName("@EvictCache should clear L2 cache after @Modifying query commits")
        void evictCacheShouldClearL2CacheAfterModifyingQuery() {
            // Given: Create and cache an entity
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            Long entityId = txTemplate.execute(status -> {
                TestCachedEntity entity = TestCachedEntity.builder()
                        .name("Test Entity")
                        .status("ACTIVE")
                        .build();
                return repository.save(entity).getId();
            });

            // Load entity to populate cache
            txTemplate.execute(status -> {
                repository.findById(entityId);
                return null;
            });

            // Verify entity is in cache
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should be in L2 cache after findById")
                    .isTrue();

            // When: Execute @Modifying query with @EvictCache
            txTemplate.execute(status -> {
                repository.updateStatus(entityId, "INACTIVE");
                return null;
            });

            // Then: Entity should be evicted from cache
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should be evicted from L2 cache after @EvictCache")
                    .isFalse();
        }

        @Test
        @DisplayName("Bulk operation should evict entire entity cache")
        void bulkOperationEvictsEntireEntityCache() {
            // Given: Create multiple entities and cache them
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            Long[] entityIds = txTemplate.execute(status -> {
                TestCachedEntity entity1 = repository.save(TestCachedEntity.builder()
                        .name("Entity 1")
                        .status("ACTIVE")
                        .build());
                TestCachedEntity entity2 = repository.save(TestCachedEntity.builder()
                        .name("Entity 2")
                        .status("ACTIVE")
                        .build());
                TestCachedEntity entity3 = repository.save(TestCachedEntity.builder()
                        .name("Entity 3")
                        .status("ACTIVE")
                        .build());
                return new Long[]{entity1.getId(), entity2.getId(), entity3.getId()};
            });

            // Load entities to populate cache
            txTemplate.execute(status -> {
                for (Long id : entityIds) {
                    repository.findById(id);
                }
                return null;
            });

            // Verify all entities are in cache
            for (Long id : entityIds) {
                assertThat(cacheManager.contains(TestCachedEntity.class, id))
                        .as("Entity %d should be in cache", id)
                        .isTrue();
            }

            // When: Execute bulk update
            txTemplate.execute(status -> {
                repository.updateAllStatus("BULK_UPDATED");
                return null;
            });

            // Then: All entities should be evicted (bulk operation evicts entire entity cache)
            for (Long id : entityIds) {
                assertThat(cacheManager.contains(TestCachedEntity.class, id))
                        .as("Entity %d should be evicted after bulk operation", id)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Transaction rollback behavior")
    class TransactionRollbackTests {

        @Test
        @DisplayName("Cache should NOT be evicted on transaction rollback")
        void cacheNotEvictedOnTransactionRollback() {
            // Given: Create and cache an entity
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            Long entityId = txTemplate.execute(status -> {
                TestCachedEntity entity = TestCachedEntity.builder()
                        .name("Rollback Test Entity")
                        .status("ACTIVE")
                        .build();
                return repository.save(entity).getId();
            });

            // Load entity to populate cache
            txTemplate.execute(status -> {
                repository.findById(entityId);
                return null;
            });

            // Verify entity is in cache
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should be in L2 cache before rollback test")
                    .isTrue();

            // When: Execute @Modifying query but rollback transaction
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);

            try {
                repository.updateStatus(entityId, "SHOULD_NOT_PERSIST");
                // Force rollback
                transactionManager.rollback(status);
            } catch (Exception e) {
                if (!status.isCompleted()) {
                    transactionManager.rollback(status);
                }
            }

            // Then: Entity should still be in cache (eviction happens only on commit)
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should remain in L2 cache after transaction rollback")
                    .isTrue();

            // Verify the entity status was not changed
            TestCachedEntity entity = txTemplate.execute(s ->
                    repository.findById(entityId).orElseThrow());
            assertThat(entity.getStatus())
                    .as("Entity status should remain unchanged after rollback")
                    .isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("Delete operation tests")
    class DeleteOperationTests {

        @Test
        @DisplayName("@EvictCache should work with delete operations")
        void evictCacheShouldWorkWithDeleteOperation() {
            // Given: Create and cache an entity
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            Long entityId = txTemplate.execute(status -> {
                TestCachedEntity entity = TestCachedEntity.builder()
                        .name("Delete Test Entity")
                        .status("ACTIVE")
                        .build();
                return repository.save(entity).getId();
            });

            // Load entity to populate cache
            txTemplate.execute(status -> {
                repository.findById(entityId);
                return null;
            });

            // Verify entity is in cache
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should be in L2 cache")
                    .isTrue();

            // When: Execute delete with @EvictCache
            txTemplate.execute(status -> {
                repository.deleteEntityById(entityId);
                return null;
            });

            // Then: Entity should be evicted from cache
            assertThat(cacheManager.contains(TestCachedEntity.class, entityId))
                    .as("Entity should be evicted from L2 cache after delete")
                    .isFalse();
        }
    }
}
