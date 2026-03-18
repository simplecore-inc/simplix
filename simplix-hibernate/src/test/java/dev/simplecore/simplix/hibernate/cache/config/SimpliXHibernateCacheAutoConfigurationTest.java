package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.aspect.ModifyingQueryCacheEvictionAspect;
import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import dev.simplecore.simplix.hibernate.cache.core.HibernateCacheManager;
import dev.simplecore.simplix.hibernate.cache.handler.PostCommitCacheEvictionHandler;
import dev.simplecore.simplix.hibernate.cache.strategy.CacheEvictionStrategy;
import dev.simplecore.simplix.hibernate.cache.transaction.TransactionAwareCacheEvictionCollector;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Cache;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests for SimpliXHibernateCacheAutoConfiguration.
 * Covers bean creation methods, event listeners, and package detection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SimpliXHibernateCacheAutoConfiguration Tests")
class SimpliXHibernateCacheAutoConfigurationTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Cache hibernateCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Environment environment;

    @Mock
    private HibernateCacheManager cacheManager;

    private HibernateCacheProperties properties;
    private SimpliXHibernateCacheAutoConfiguration autoConfiguration;

    @BeforeEach
    void setUp() {
        properties = new HibernateCacheProperties();
        autoConfiguration = new SimpliXHibernateCacheAutoConfiguration(properties);
    }

    @AfterEach
    void tearDown() {
        HibernateCacheHolder.reset();
    }

    @Nested
    @DisplayName("Bean creation methods")
    class BeanCreationTests {

        @Test
        @DisplayName("Should create HibernateCacheManager bean")
        void shouldCreateHibernateCacheManagerBean() {
            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            when(sessionFactory.getCache()).thenReturn(hibernateCache);

            HibernateCacheManager result = autoConfiguration.hibernateCacheManager(entityManagerFactory);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should set HibernateCacheHolder during HibernateCacheManager creation")
        void shouldSetCacheHolderDuringCacheManagerCreation() {
            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            when(sessionFactory.getCache()).thenReturn(hibernateCache);

            autoConfiguration.hibernateCacheManager(entityManagerFactory);

            assertThat(HibernateCacheHolder.getCache()).isEqualTo(hibernateCache);
        }

        @Test
        @DisplayName("Should create TransactionAwareCacheEvictionCollector bean")
        void shouldCreateTransactionAwareCacheEvictionCollectorBean() {
            TransactionAwareCacheEvictionCollector result =
                    autoConfiguration.transactionAwareCacheEvictionCollector(eventPublisher);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should create PostCommitCacheEvictionHandler bean")
        void shouldCreatePostCommitCacheEvictionHandlerBean() {
            CacheEvictionStrategy evictionStrategy = new CacheEvictionStrategy(cacheManager);

            PostCommitCacheEvictionHandler result =
                    autoConfiguration.postCommitCacheEvictionHandler(evictionStrategy);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should create ModifyingQueryCacheEvictionAspect bean")
        void shouldCreateModifyingQueryCacheEvictionAspectBean() {
            TransactionAwareCacheEvictionCollector collector =
                    new TransactionAwareCacheEvictionCollector(eventPublisher);

            ModifyingQueryCacheEvictionAspect result =
                    autoConfiguration.modifyingQueryCacheEvictionAspect(collector);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should create EntityCacheScanner bean")
        void shouldCreateEntityCacheScannerBean() {
            EntityCacheScanner result = autoConfiguration.entityCacheScanner();

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should create CacheEvictionStrategy bean")
        void shouldCreateCacheEvictionStrategyBean() {
            CacheEvictionStrategy result = autoConfiguration.cacheEvictionStrategy(cacheManager);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should create HibernateCacheInitializer bean")
        void shouldCreateHibernateCacheInitializerBean() {
            EntityCacheScanner scanner = new EntityCacheScanner();

            HibernateCacheInitializer result = autoConfiguration.hibernateCacheInitializer(
                    properties, entityManagerFactory, scanner, applicationContext);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("onApplicationReady event listener")
    class OnApplicationReadyTests {

        @Test
        @DisplayName("Should scan for cached entities on context refresh")
        void shouldScanForCachedEntitiesOnContextRefresh() {
            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(scanner).scanForCachedEntities(any(String[].class));
        }

        @Test
        @DisplayName("Should use scan packages from properties when configured")
        void shouldUseScanPackagesFromProperties() {
            properties.setScanPackages(new String[]{"com.example.entity"});

            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(scanner).scanForCachedEntities(new String[]{"com.example.entity"});
        }

        @Test
        @DisplayName("Should detect base packages from environment when not configured")
        void shouldDetectBasePackagesFromEnvironment() {
            properties.setScanPackages(null);

            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);
            when(applicationContext.getEnvironment()).thenReturn(environment);
            when(environment.getProperty("spring.application.package")).thenReturn("com.example.app");

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(scanner).scanForCachedEntities(new String[]{"com.example.app"});
        }

        @Test
        @DisplayName("Should fallback to empty package when environment property is null")
        void shouldFallbackToEmptyPackageWhenPropertyIsNull() {
            properties.setScanPackages(null);

            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);
            when(applicationContext.getEnvironment()).thenReturn(environment);
            when(environment.getProperty("spring.application.package")).thenReturn(null);

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(scanner).scanForCachedEntities(new String[]{""});
        }

        @Test
        @DisplayName("Should fallback to empty package when environment throws exception")
        void shouldFallbackToEmptyPackageWhenEnvironmentThrows() {
            properties.setScanPackages(null);

            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);
            when(applicationContext.getEnvironment()).thenThrow(new RuntimeException("Env error"));

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(scanner).scanForCachedEntities(new String[]{""});
        }

        @Test
        @DisplayName("Should skip scanning when disabled")
        void shouldSkipScanningWhenDisabled() {
            properties.setDisabled(true);

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            verify(applicationContext, never()).getBean(EntityCacheScanner.class);
        }

        @Test
        @DisplayName("Should handle empty scan packages array")
        void shouldHandleEmptyScanPackagesArray() {
            properties.setScanPackages(new String[]{});

            EntityCacheScanner scanner = mock(EntityCacheScanner.class);
            when(applicationContext.getBean(EntityCacheScanner.class)).thenReturn(scanner);
            when(applicationContext.getEnvironment()).thenReturn(environment);
            when(environment.getProperty("spring.application.package")).thenReturn(null);

            ContextRefreshedEvent event = new ContextRefreshedEvent(applicationContext);

            autoConfiguration.onApplicationReady(event);

            // Empty array triggers detectBasePackages
            verify(scanner).scanForCachedEntities(new String[]{""});
        }
    }

    @Nested
    @DisplayName("onContextClosed event listener")
    class OnContextClosedTests {

        @Test
        @DisplayName("Should reset HibernateCacheHolder on context close")
        void shouldResetCacheHolderOnContextClose() {
            // Given - set some cache
            HibernateCacheHolder.setCache(hibernateCache);
            assertThat(HibernateCacheHolder.getCache()).isNotNull();

            ContextClosedEvent event = new ContextClosedEvent(applicationContext);

            // When
            autoConfiguration.onContextClosed(event);

            // Then
            assertThat(HibernateCacheHolder.isReset()).isTrue();
        }

        @Test
        @DisplayName("Should handle reset when cache holder is already null")
        void shouldHandleResetWhenAlreadyNull() {
            ContextClosedEvent event = new ContextClosedEvent(applicationContext);

            assertThatCode(() -> autoConfiguration.onContextClosed(event))
                    .doesNotThrowAnyException();
        }
    }
}
