package dev.simplecore.simplix.hibernate.event;

import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import dev.simplecore.simplix.core.event.model.EventMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for EntityEventPublishingListener's internal methods using reflection
 * and EntityManagerHolder binding to cover resolveDirtyProperties, applyPropertyFilter,
 * and buildMetadata paths.
 */
@DisplayName("EntityEventPublishingListener - Internal Method Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityEventPublishingListenerInternalTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private EntityManager entityManager;

    @Mock
    private SessionImplementor sessionImplementor;

    @Mock
    private PersistenceContext persistenceContext;

    @Mock
    private EntityEntry entityEntry;

    @Mock
    private EntityPersister entityPersister;

    private EntityEventPublishingListener listener;

    @BeforeEach
    void setUp() {
        listener = new EntityEventPublishingListener();
        listener.setApplicationEventPublisher(eventPublisher);
        listener.setEntityManagerFactory(entityManagerFactory);
    }

    @AfterEach
    void tearDown() {
        listener.setApplicationEventPublisher(eventPublisher);
        // Unbind any resources
        try {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
        } catch (IllegalStateException ignored) {
            // Already unbound
        }
    }

    private void bindEntityManager() {
        EntityManagerHolder holder = new EntityManagerHolder(entityManager);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, holder);
    }

    @Nested
    @DisplayName("resolveDirtyProperties with mocked Hibernate session")
    class ResolveDirtyPropertiesTests {

        @Test
        @DisplayName("Should detect dirty properties when loaded state differs from current")
        void shouldDetectDirtyProperties() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;
            entity.name = "updated-name";
            entity.email = "new@email.com";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old-name", "old@email.com"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated-name", "new@email.com"});

            // When - onPreUpdate captures dirty properties
            listener.onPreUpdate(entity);

            // Then - onPostUpdate should publish with dirty properties
            listener.onPostUpdate(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.eventType()).isEqualTo("TEST_UPDATED");
            assertThat(event.changedProperties()).containsExactlyInAnyOrder("name", "email");
        }

        @Test
        @DisplayName("Should not detect dirty properties when values are the same")
        void shouldNotDetectDirtyWhenValuesAreSame() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;
            entity.name = "same";
            entity.email = "same@email.com";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"same", "same@email.com"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"same", "same@email.com"});

            // When - no dirty properties
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event published
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should handle null EntityEntry gracefully")
        void shouldHandleNullEntityEntry() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(null);

            // When
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should handle null loadedState gracefully")
        void shouldHandleNullLoadedState() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name"});
            when(entityEntry.getLoadedState()).thenReturn(null);

            // When
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should handle exception in resolveDirtyProperties gracefully")
        void shouldHandleExceptionGracefully() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            when(entityManager.unwrap(SessionImplementor.class))
                    .thenThrow(new RuntimeException("Session error"));

            // When
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event, no exception propagated
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should detect partial dirty properties")
        void shouldDetectPartialDirtyProperties() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;
            entity.name = "updated";
            entity.email = "same@email.com";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old-name", "same@email.com"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated", "same@email.com"});

            // When
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - event published with only changed property
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.changedProperties()).containsExactly("name");
        }
    }

    @Nested
    @DisplayName("applyPropertyFilter tests")
    class ApplyPropertyFilterTests {

        @Test
        @DisplayName("Should filter dirty properties using watchProperties")
        void shouldFilterUsingWatchProperties() {
            // Given
            bindEntityManager();

            WatchPropertiesEntity entity = new WatchPropertiesEntity();
            entity.id = 1L;
            entity.name = "updated";
            entity.email = "new@email.com";
            entity.status = "changed";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email", "status"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old", "old@email.com", "old-status"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated", "new@email.com", "changed"});

            // When - watchProperties = {"name", "email"}, so "status" should be filtered out
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - only "name" and "email" should be in changedProperties
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.changedProperties()).containsExactlyInAnyOrder("name", "email");
            assertThat(event.changedProperties()).doesNotContain("status");
        }

        @Test
        @DisplayName("Should filter out changes to unwatched properties resulting in no event")
        void shouldNotPublishWhenOnlyUnwatchedPropertiesChange() {
            // Given
            bindEntityManager();

            WatchPropertiesEntity entity = new WatchPropertiesEntity();
            entity.id = 1L;
            entity.name = "same";
            entity.email = "same@email.com";
            entity.status = "changed";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email", "status"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"same", "same@email.com", "old-status"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"same", "same@email.com", "changed"});

            // When - only "status" changed but it's not in watchProperties
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event because filtered properties are empty
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should filter dirty properties using ignoreProperties")
        void shouldFilterUsingIgnoreProperties() {
            // Given
            bindEntityManager();

            IgnorePropertiesEntity entity = new IgnorePropertiesEntity();
            entity.id = 1L;
            entity.name = "updated";
            entity.updatedAt = "2024-01-01";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "updatedAt"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old", "2023-12-31"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated", "2024-01-01"});

            // When - ignoreProperties = {"updatedAt"}
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - "updatedAt" should be filtered out, "name" should remain
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.changedProperties()).containsExactly("name");
            assertThat(event.changedProperties()).doesNotContain("updatedAt");
        }

        @Test
        @DisplayName("Should not publish when only ignored properties change")
        void shouldNotPublishWhenOnlyIgnoredPropertiesChange() {
            // Given
            bindEntityManager();

            IgnorePropertiesEntity entity = new IgnorePropertiesEntity();
            entity.id = 1L;
            entity.name = "same";
            entity.updatedAt = "2024-01-01";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "updatedAt"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"same", "2023-12-31"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"same", "2024-01-01"});

            // When - only "updatedAt" changed but it's ignored
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - no event
            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should pass all dirty properties when no filter is configured")
        void shouldPassAllDirtyWhenNoFilter() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;
            entity.name = "updated";
            entity.email = "new@email.com";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name", "email"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old", "old@email.com"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated", "new@email.com"});

            // When - no filter configured
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then - all dirty properties passed through
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.changedProperties()).containsExactlyInAnyOrder("name", "email");
        }
    }

    @Nested
    @DisplayName("onPostUpdate with dirty properties from preUpdate")
    class PostUpdateWithDirtyPropsTests {

        @Test
        @DisplayName("Should retrieve and clear dirty properties from ThreadLocal")
        void shouldRetrieveAndClearDirtyProperties() {
            // Given
            bindEntityManager();

            TestEntity entity = new TestEntity();
            entity.id = 1L;
            entity.name = "updated";

            when(entityManager.unwrap(SessionImplementor.class)).thenReturn(sessionImplementor);
            when(sessionImplementor.getPersistenceContextInternal()).thenReturn(persistenceContext);
            when(persistenceContext.getEntry(entity)).thenReturn(entityEntry);
            when(entityEntry.getPersister()).thenReturn(entityPersister);
            when(entityPersister.getPropertyNames()).thenReturn(new String[]{"name"});
            when(entityEntry.getLoadedState()).thenReturn(new Object[]{"old"});
            when(entityPersister.getValues(entity)).thenReturn(new Object[]{"updated"});

            // When - PreUpdate captures props, PostUpdate publishes
            listener.onPreUpdate(entity);
            listener.onPostUpdate(entity);

            // Then
            verify(eventPublisher).publishEvent((Object) any());

            // Second PostUpdate should not publish (dirty props already consumed)
            reset(eventPublisher);
            listener.onPostUpdate(entity);
            verify(eventPublisher, never()).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("Soft delete detection in onPreUpdate")
    class SoftDeletePreUpdateTests {

        @Test
        @DisplayName("Should skip dirty check for soft-deleted entities in PreUpdate")
        void shouldSkipDirtyCheckForSoftDeletedEntities() {
            // Given
            bindEntityManager();

            SoftDeleteEntity entity = new SoftDeleteEntity();
            entity.id = 1L;
            entity.deleted = true;

            // When - onPreUpdate should return early
            listener.onPreUpdate(entity);

            // Then - no interaction with entityManager (no dirty check)
            verify(entityManager, never()).unwrap(any());
        }
    }

    @Nested
    @DisplayName("onPostUpdate soft delete with publishDelete false")
    class PostUpdateSoftDeletePublishDeleteFalseTests {

        @Test
        @DisplayName("Should not publish when soft-deleted and publishDelete is false")
        void shouldNotPublishSoftDeleteWhenPublishDeleteFalse() {
            NoDeleteSoftEntity entity = new NoDeleteSoftEntity();
            entity.id = 1L;
            entity.deleted = true;

            listener.onPostUpdate(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("resolveEntityId edge cases")
    class ResolveEntityIdEdgeCases {

        @Test
        @DisplayName("Should traverse parent class to find @Id field")
        void shouldTraverseParentClassToFindId() {
            // Given
            ChildEntity entity = new ChildEntity();
            entity.id = 55L;
            entity.childField = "test";

            listener.onPostPersist(entity);

            // Then
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.aggregateId()).isEqualTo("55");
        }

        @Test
        @DisplayName("Should return null for entity without any @Id field")
        void shouldReturnNullForEntityWithoutId() {
            NoIdEntity entity = new NoIdEntity();
            entity.name = "test";

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.aggregateId()).isNull();
        }
    }

    // --- Test entities ---

    @EntityEventConfig(
            onCreate = "TEST_CREATED",
            onUpdate = "TEST_UPDATED",
            onDelete = "TEST_DELETED"
    )
    static class TestEntity {
        @Id
        Long id;
        String name;
        String email;
    }

    @EntityEventConfig(
            watchProperties = {"name", "email"},
            onCreate = "WATCH_CREATED",
            onUpdate = "WATCH_UPDATED",
            onDelete = "WATCH_DELETED"
    )
    static class WatchPropertiesEntity {
        @Id
        Long id;
        String name;
        String email;
        String status;
    }

    @EntityEventConfig(
            ignoreProperties = {"updatedAt"},
            onCreate = "IGNORE_CREATED",
            onUpdate = "IGNORE_UPDATED",
            onDelete = "IGNORE_DELETED"
    )
    static class IgnorePropertiesEntity {
        @Id
        Long id;
        String name;
        String updatedAt;
    }

    @EntityEventConfig(
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class SoftDeleteEntity implements SoftDeletable {
        @Id
        Long id;
        boolean deleted;
        String name;

        @Override
        public boolean isDeleted() {
            return deleted;
        }
    }

    @EntityEventConfig(
            publishDelete = false,
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class NoDeleteSoftEntity implements SoftDeletable {
        @Id
        Long id;
        boolean deleted;

        @Override
        public boolean isDeleted() {
            return deleted;
        }
    }

    @EntityEventConfig(
            onCreate = "CHILD_CREATED",
            onUpdate = "CHILD_UPDATED",
            onDelete = "CHILD_DELETED"
    )
    static class ChildEntity extends ParentEntity {
        String childField;
    }

    static class ParentEntity {
        @Id
        Long id;
    }

    @EntityEventConfig(
            onCreate = "NO_ID_CREATED",
            onUpdate = "NO_ID_UPDATED",
            onDelete = "NO_ID_DELETED"
    )
    static class NoIdEntity {
        String name;
    }
}
