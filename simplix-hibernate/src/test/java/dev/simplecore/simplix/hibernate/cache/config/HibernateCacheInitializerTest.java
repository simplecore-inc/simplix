package dev.simplecore.simplix.hibernate.cache.config;

import dev.simplecore.simplix.hibernate.cache.core.EntityCacheScanner;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@DisplayName("HibernateCacheInitializer")
@ExtendWith(MockitoExtension.class)
class HibernateCacheInitializerTest {

    @Mock
    private HibernateCacheProperties properties;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityCacheScanner entityScanner;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SessionFactory sessionFactory;

    private HibernateCacheInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new HibernateCacheInitializer(
                properties, entityManagerFactory, entityScanner, applicationContext);
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTests {

        @Test
        @DisplayName("should initialize and log cache configuration")
        void shouldInitializeSuccessfully() {
            when(properties.isDisabled()).thenReturn(false);
            when(properties.isQueryCacheAutoEviction()).thenReturn(true);

            Map<String, Object> sessionFactoryProps = new HashMap<>();
            sessionFactoryProps.put("hibernate.cache.use_second_level_cache", "true");
            sessionFactoryProps.put("hibernate.cache.use_query_cache", "true");
            sessionFactoryProps.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");

            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            when(sessionFactory.getProperties()).thenReturn(sessionFactoryProps);

            assertThatCode(() -> initializer.initialize()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle exception during cache verification gracefully")
        void shouldHandleVerificationException() {
            when(properties.isDisabled()).thenReturn(false);
            when(properties.isQueryCacheAutoEviction()).thenReturn(true);
            when(entityManagerFactory.unwrap(SessionFactory.class))
                    .thenThrow(new RuntimeException("EntityManagerFactory not available"));

            assertThatCode(() -> initializer.initialize()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should log when second-level cache is disabled")
        void shouldLogWhenSecondLevelCacheDisabled() {
            when(properties.isDisabled()).thenReturn(false);
            when(properties.isQueryCacheAutoEviction()).thenReturn(true);

            Map<String, Object> sessionFactoryProps = new HashMap<>();
            sessionFactoryProps.put("hibernate.cache.use_second_level_cache", "false");
            sessionFactoryProps.put("hibernate.cache.use_query_cache", "false");
            sessionFactoryProps.put("hibernate.cache.region.factory_class", "null");

            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            when(sessionFactory.getProperties()).thenReturn(sessionFactoryProps);

            assertThatCode(() -> initializer.initialize()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should log when query cache disabled but auto-eviction enabled")
        void shouldLogWhenQueryCacheDisabledButAutoEvictionEnabled() {
            when(properties.isDisabled()).thenReturn(false);
            when(properties.isQueryCacheAutoEviction()).thenReturn(true);

            Map<String, Object> sessionFactoryProps = new HashMap<>();
            sessionFactoryProps.put("hibernate.cache.use_second_level_cache", "true");
            sessionFactoryProps.put("hibernate.cache.use_query_cache", "false");
            sessionFactoryProps.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");

            when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
            when(sessionFactory.getProperties()).thenReturn(sessionFactoryProps);

            assertThatCode(() -> initializer.initialize()).doesNotThrowAnyException();
        }
    }
}
