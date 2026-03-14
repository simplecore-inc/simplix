package dev.simplecore.simplix.hibernate.event;

import dev.simplecore.simplix.core.entity.EntityEventPayloadProvider;
import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.entity.SimpliXBaseEntity;
import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import dev.simplecore.simplix.core.event.EventContext;
import dev.simplecore.simplix.core.event.model.EventMessage;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreUpdate;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JPA Entity Listener that publishes {@link EventMessage} events for entity lifecycle changes.
 * <p>
 * This listener detects entities annotated with {@link EntityEventConfig} and publishes
 * events via Spring's {@link ApplicationEventPublisher}. It supports:
 * <ul>
 *   <li>Dirty property tracking for UPDATE events</li>
 *   <li>Soft delete detection via {@link SoftDeletable}</li>
 *   <li>Event suppression via {@link EventContext}</li>
 *   <li>Property filtering ({@code ignoreProperties}/{@code watchProperties})</li>
 *   <li>Security context propagation (actor ID from SecurityContextHolder)</li>
 * </ul>
 *
 * <p>
 * Register this listener on entities (or a base entity class):
 * <pre>{@code
 * @Entity
 * @EntityListeners(EntityEventPublishingListener.class)
 * @EntityEventConfig(onCreate = "USER_CREATED")
 * public class User extends BaseEntity { ... }
 * }</pre>
 */
@Component
public class EntityEventPublishingListener {

    private static final Logger log = LoggerFactory.getLogger(EntityEventPublishingListener.class);

    private static ApplicationEventPublisher applicationEventPublisher;
    private static EntityManagerFactory entityManagerFactory;

    /**
     * ThreadLocal to store dirty properties captured in @PreUpdate.
     * Uses IdentityHashMap for reference-equality semantics.
     */
    private static final ThreadLocal<IdentityHashMap<Object, Set<String>>> PRE_UPDATE_DIRTY_PROPS =
        ThreadLocal.withInitial(IdentityHashMap::new);

    @Autowired
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        EntityEventPublishingListener.applicationEventPublisher = publisher;
    }

    @Autowired
    public void setEntityManagerFactory(EntityManagerFactory emf) {
        EntityEventPublishingListener.entityManagerFactory = emf;
    }

    // --- JPA Callbacks ---

    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (!shouldPublish(entity)) return;

        EntityEventConfig config = getConfig(entity);
        if (config == null || !config.publishUpdate()) return;

        // Skip dirty check for soft-deleted entities (will be handled as DELETE in @PostUpdate)
        if (entity instanceof SoftDeletable sd && sd.isDeleted()) return;

        Set<String> dirtyProperties = resolveDirtyProperties(entity);
        if (dirtyProperties == null || dirtyProperties.isEmpty()) return;

        Set<String> filtered = applyPropertyFilter(dirtyProperties, config);
        if (!filtered.isEmpty()) {
            PRE_UPDATE_DIRTY_PROPS.get().put(entity, filtered);
        }
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        if (!shouldPublish(entity)) return;

        EntityEventConfig config = getConfig(entity);
        if (config == null || !config.publishCreate()) return;

        publishEvent(config.onCreate(), entity, null, null);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        if (!shouldPublish(entity)) return;

        EntityEventConfig config = getConfig(entity);
        if (config == null) return;

        // Soft delete detection: @SQLDelete causes @PostUpdate instead of @PostRemove
        if (entity instanceof SoftDeletable sd && sd.isDeleted()) {
            if (!config.publishDelete()) return;

            Map<String, Object> extraMetadata = new HashMap<>();
            extraMetadata.put("softDelete", true);
            publishEvent(config.onDelete(), entity, null, extraMetadata);
            return;
        }

        // Retrieve dirty properties captured in @PreUpdate
        Set<String> dirtyProperties = PRE_UPDATE_DIRTY_PROPS.get().remove(entity);
        if (dirtyProperties == null || dirtyProperties.isEmpty()) return;

        publishEvent(config.onUpdate(), entity, dirtyProperties, null);
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        if (!shouldPublish(entity)) return;

        EntityEventConfig config = getConfig(entity);
        if (config == null || !config.publishDelete()) return;

        publishEvent(config.onDelete(), entity, null, null);
    }

    // --- Internal methods ---

    private boolean shouldPublish(Object entity) {
        if (applicationEventPublisher == null) return false;
        if (EventContext.isEventsSuppressed()) return false;
        EntityEventConfig config = getConfig(entity);
        return config != null && config.enabled();
    }

    private EntityEventConfig getConfig(Object entity) {
        return entity.getClass().getAnnotation(EntityEventConfig.class);
    }

    private void publishEvent(String eventType, Object entity, Set<String> changedProperties,
            Map<String, Object> extraMetadata) {
        try {
            Map<String, Object> metadata = buildMetadata(extraMetadata);
            Map<String, Object> payload = buildPayload(entity);

            Object entityId = resolveEntityId(entity);
            String aggregateId = entityId != null ? String.valueOf(entityId) : null;
            String aggregateType = entity.getClass().getSimpleName();

            EventMessage event = new EventMessage(
                aggregateId, aggregateType, eventType,
                payload, metadata, changedProperties, Instant.now()
            );

            applicationEventPublisher.publishEvent(event);

            log.trace("Published entity event: type={}, aggregate={}, id={}",
                eventType, aggregateType, aggregateId);
        } catch (Exception e) {
            log.error("Failed to publish entity event: type={}, entity={}",
                eventType, entity.getClass().getSimpleName(), e);
        }
    }

    /**
     * Resolve dirty properties by comparing loaded state with current entity values.
     * Called during @PreUpdate when EntityEntry.loadedState still reflects the old values.
     */
    private Set<String> resolveDirtyProperties(Object entity) {
        if (entityManagerFactory == null) return null;

        try {
            EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
            if (em == null) return null;

            SessionImplementor session = em.unwrap(SessionImplementor.class);
            PersistenceContext pc = session.getPersistenceContextInternal();
            EntityEntry entry = pc.getEntry(entity);

            if (entry == null) return null;

            EntityPersister persister = entry.getPersister();
            String[] propertyNames = persister.getPropertyNames();
            Object[] loadedState = entry.getLoadedState();

            if (loadedState == null) return null;

            Object[] currentState = persister.getValues(entity);

            Set<String> dirty = new LinkedHashSet<>();
            for (int i = 0; i < propertyNames.length; i++) {
                if (!Objects.equals(loadedState[i], currentState[i])) {
                    dirty.add(propertyNames[i]);
                }
            }

            return dirty;
        } catch (Exception e) {
            log.warn("Failed to resolve dirty properties for entity: {}",
                entity.getClass().getSimpleName(), e);
            return null;
        }
    }

    /**
     * Apply ignoreProperties / watchProperties filtering.
     */
    private Set<String> applyPropertyFilter(Set<String> dirty, EntityEventConfig config) {
        if (config.watchProperties().length > 0) {
            Set<String> watch = Set.of(config.watchProperties());
            Set<String> filtered = new LinkedHashSet<>();
            for (String prop : dirty) {
                if (watch.contains(prop)) {
                    filtered.add(prop);
                }
            }
            return filtered;
        }

        if (config.ignoreProperties().length > 0) {
            Set<String> ignore = Set.of(config.ignoreProperties());
            Set<String> filtered = new LinkedHashSet<>();
            for (String prop : dirty) {
                if (!ignore.contains(prop)) {
                    filtered.add(prop);
                }
            }
            return filtered;
        }

        return dirty;
    }

    private Map<String, Object> buildMetadata(Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new HashMap<>();

        // Security context propagation (optional - works only when Spring Security is on classpath)
        try {
            Object securityContext = Class.forName("org.springframework.security.core.context.SecurityContextHolder")
                .getMethod("getContext")
                .invoke(null);
            if (securityContext != null) {
                Object authentication = securityContext.getClass().getMethod("getAuthentication").invoke(securityContext);
                if (authentication != null) {
                    String actorId = resolveActorId(authentication);
                    if (actorId != null) {
                        metadata.put("actorId", actorId);
                    }
                }
            }
        } catch (Exception ignored) {
            // Spring Security not available or no authentication context
        }

        // MDC request context
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            metadata.put("requestId", requestId);
        }

        // Merge extra metadata
        if (extraMetadata != null) {
            metadata.putAll(extraMetadata);
        }

        return metadata;
    }

    /**
     * Resolves the actor identifier from the authentication principal.
     * <p>
     * Tries {@code getUserId()} first (for principals implementing a user-id-provider pattern),
     * then falls back to {@code authentication.getName()}.
     */
    private String resolveActorId(Object authentication) throws Exception {
        Object principal = authentication.getClass().getMethod("getPrincipal").invoke(authentication);
        if (principal != null) {
            // Try getUserId() — preferred: returns stable UUID rather than mutable username
            try {
                Object userId = principal.getClass().getMethod("getUserId").invoke(principal);
                if (userId != null) {
                    return userId.toString();
                }
            } catch (NoSuchMethodException ignored) {
                // Principal does not implement getUserId()
            }
        }

        // Fallback to authentication.getName()
        Object name = authentication.getClass().getMethod("getName").invoke(authentication);
        return name != null ? name.toString() : null;
    }

    private Map<String, Object> buildPayload(Object entity) {
        Map<String, Object> payload = new HashMap<>();

        Object entityId = resolveEntityId(entity);
        if (entityId != null) {
            payload.put("id", entityId);
        }

        payload.put("className", entity.getClass().getSimpleName());

        // Custom payload from EntityEventPayloadProvider
        if (entity instanceof EntityEventPayloadProvider provider) {
            Map<String, Object> customPayload = provider.getEventPayloadData();
            if (customPayload != null) {
                payload.putAll(customPayload);
            }
        }

        return payload;
    }

    /**
     * Resolve the entity's primary key value.
     * Tries SimpliXBaseEntity.getId() first, then falls back to @Id field scanning.
     */
    private Object resolveEntityId(Object entity) {
        if (entity instanceof SimpliXBaseEntity<?> base) {
            return base.getId();
        }

        // Fallback: scan for @Id annotated field
        Class<?> clazz = entity.getClass();
        while (clazz != null && !clazz.equals(Object.class)) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    try {
                        return field.get(entity);
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
