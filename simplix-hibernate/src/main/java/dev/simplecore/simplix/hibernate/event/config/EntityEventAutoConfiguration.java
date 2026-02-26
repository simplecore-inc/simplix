package dev.simplecore.simplix.hibernate.event.config;

import dev.simplecore.simplix.hibernate.event.EntityEventPublishingListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for entity event publishing.
 * <p>
 * Registers {@link EntityEventPublishingListener} as a Spring bean so that
 * it can receive injected dependencies (ApplicationEventPublisher, EntityManagerFactory)
 * via static setter injection.
 * <p>
 * This configuration is automatically enabled when:
 * <ul>
 *   <li>JPA is on the classpath ({@code jakarta.persistence.EntityManager})</li>
 *   <li>{@code simplix.entity-events.enabled} is {@code true} (default)</li>
 * </ul>
 * <p>
 * Disable by setting {@code simplix.entity-events.enabled=false} in application properties.
 */
@Configuration
@ConditionalOnClass(name = "jakarta.persistence.EntityManager")
@ConditionalOnProperty(prefix = "simplix.entity-events", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class EntityEventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntityEventPublishingListener entityEventPublishingListener() {
        return new EntityEventPublishingListener();
    }
}
