package dev.simplecore.simplix.hibernate.event;

import dev.simplecore.simplix.core.entity.EntityEventPayloadProvider;
import dev.simplecore.simplix.core.entity.SoftDeletable;
import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import dev.simplecore.simplix.core.event.EventContext;
import dev.simplecore.simplix.core.event.model.EventMessage;
import jakarta.persistence.Id;
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
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Extended tests for EntityEventPublishingListener covering soft delete detection,
 * property filtering, payload provider, MDC context, resolveEntityId fallback,
 * and event suppression.
 */
@DisplayName("EntityEventPublishingListener - Extended Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntityEventPublishingListenerExtendedTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EntityEventPublishingListener listener;

    @BeforeEach
    void setUp() {
        listener = new EntityEventPublishingListener();
        listener.setApplicationEventPublisher(eventPublisher);
        listener.setEntityManagerFactory(null);
    }

    @AfterEach
    void tearDown() {
        listener.setApplicationEventPublisher(eventPublisher);
        MDC.clear();
        EventContext.clear();
    }

    @Nested
    @DisplayName("Soft delete detection via onPostUpdate")
    class SoftDeleteTests {

        @Test
        @DisplayName("Should publish DELETE event when soft-deleted entity is updated")
        void shouldPublishDeleteEventForSoftDeletedEntity() {
            // Re-set publisher before verification
            listener.setApplicationEventPublisher(eventPublisher);

            SoftDeleteEntity entity = new SoftDeleteEntity();
            entity.id = 1L;
            entity.deleted = true;

            listener.onPostUpdate(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            Object published = captor.getValue();
            assertThat(published).isInstanceOf(EventMessage.class);

            EventMessage event = (EventMessage) published;
            assertThat(event.eventType()).isEqualTo("SOFT_DELETED");
            assertThat(event.isSoftDelete()).isTrue();
        }

        @Test
        @DisplayName("Should not publish DELETE event when entity is not deleted")
        void shouldNotPublishDeleteEventWhenNotDeleted() {
            listener.setApplicationEventPublisher(eventPublisher);

            SoftDeleteEntity entity = new SoftDeleteEntity();
            entity.id = 1L;
            entity.deleted = false;

            // No dirty properties captured in @PreUpdate, so update event is not published
            listener.onPostUpdate(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should not publish soft delete when publishDelete is false")
        void shouldNotPublishSoftDeleteWhenPublishDeleteIsFalse() {
            listener.setApplicationEventPublisher(eventPublisher);

            SoftDeleteNoDeleteEntity entity = new SoftDeleteNoDeleteEntity();
            entity.id = 1L;
            entity.deleted = true;

            listener.onPostUpdate(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should skip dirty check in onPreUpdate for soft-deleted entity")
        void shouldSkipDirtyCheckForSoftDeletedEntity() {
            SoftDeleteEntity entity = new SoftDeleteEntity();
            entity.id = 1L;
            entity.deleted = true;

            // PreUpdate should return early for soft-deleted entities
            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Property filtering in onPreUpdate")
    class PropertyFilteringTests {

        @Test
        @DisplayName("Should not throw when entityManagerFactory is null during preUpdate")
        void shouldNotThrowWhenEmfIsNull() {
            WatchPropertiesEntity entity = new WatchPropertiesEntity();
            entity.id = 1L;

            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should not process entity without EntityEventConfig in preUpdate")
        void shouldNotProcessEntityWithoutConfig() {
            PlainEntity entity = new PlainEntity();
            entity.id = 1L;

            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("EntityEventPayloadProvider integration")
    class PayloadProviderTests {

        @Test
        @DisplayName("Should include custom payload data from EntityEventPayloadProvider")
        void shouldIncludeCustomPayloadData() {
            listener.setApplicationEventPublisher(eventPublisher);

            PayloadProviderEntity entity = new PayloadProviderEntity();
            entity.id = 42L;
            entity.customField = "custom-value";

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.payload()).containsEntry("id", 42L);
            assertThat(event.payload()).containsEntry("className", "PayloadProviderEntity");
            assertThat(event.payload()).containsEntry("customField", "custom-value");
        }

        @Test
        @DisplayName("Should handle null custom payload data")
        void shouldHandleNullCustomPayloadData() {
            listener.setApplicationEventPublisher(eventPublisher);

            NullPayloadProviderEntity entity = new NullPayloadProviderEntity();
            entity.id = 10L;

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.payload()).containsEntry("id", 10L);
            assertThat(event.payload()).containsEntry("className", "NullPayloadProviderEntity");
        }
    }

    @Nested
    @DisplayName("MDC request context propagation")
    class MdcContextTests {

        @Test
        @DisplayName("Should include requestId from MDC in metadata")
        void shouldIncludeRequestIdFromMdc() {
            listener.setApplicationEventPublisher(eventPublisher);

            MDC.put("requestId", "req-12345");

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.metadata()).containsEntry("requestId", "req-12345");
        }

        @Test
        @DisplayName("Should not include requestId when MDC is empty")
        void shouldNotIncludeRequestIdWhenMdcEmpty() {
            listener.setApplicationEventPublisher(eventPublisher);

            MDC.clear();

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.metadata()).doesNotContainKey("requestId");
        }
    }

    @Nested
    @DisplayName("Event suppression via EventContext")
    class EventSuppressionTests {

        @Test
        @DisplayName("Should not publish when events are suppressed")
        void shouldNotPublishWhenEventsSuppressed() {
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            EventContext.suppressEvents(() -> {
                listener.onPostPersist(entity);
            });

            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("Should publish after suppression is lifted")
        void shouldPublishAfterSuppressionLifted() {
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            EventContext.suppressEvents(() -> {
                // Events suppressed here
            });

            // After suppression, events should be published
            listener.onPostPersist(entity);

            verify(eventPublisher).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("resolveEntityId fallback")
    class ResolveEntityIdTests {

        @Test
        @DisplayName("Should resolve @Id field from entity without SimpliXBaseEntity")
        void shouldResolveIdFieldFromPlainEntity() {
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 99L;

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.aggregateId()).isEqualTo("99");
            assertThat(event.payload()).containsEntry("id", 99L);
        }

        @Test
        @DisplayName("Should handle entity with @Id in parent class")
        void shouldHandleIdInParentClass() {
            listener.setApplicationEventPublisher(eventPublisher);

            ChildEntity entity = new ChildEntity();
            entity.id = 77L;
            entity.childField = "test";

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.aggregateId()).isEqualTo("77");
        }

        @Test
        @DisplayName("Should handle entity without @Id field")
        void shouldHandleEntityWithoutIdField() {
            listener.setApplicationEventPublisher(eventPublisher);

            NoIdEntity entity = new NoIdEntity();

            listener.onPostPersist(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.aggregateId()).isNull();
        }
    }

    @Nested
    @DisplayName("onPostRemove edge cases")
    class OnPostRemoveEdgeCases {

        @Test
        @DisplayName("Should publish DELETE event with correct aggregate type")
        void shouldPublishDeleteEventWithCorrectAggregateType() {
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 5L;

            listener.onPostRemove(entity);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(captor.capture());

            EventMessage event = (EventMessage) captor.getValue();
            assertThat(event.eventType()).isEqualTo("TEST_DELETED");
            assertThat(event.aggregateType()).isEqualTo("TestEntity");
            assertThat(event.aggregateId()).isEqualTo("5");
        }
    }

    @Nested
    @DisplayName("publishEvent error handling")
    class PublishEventErrorTests {

        @Test
        @DisplayName("Should handle exception during event publish gracefully")
        void shouldHandlePublishExceptionGracefully() {
            listener.setApplicationEventPublisher(eventPublisher);

            doThrow(new RuntimeException("Publisher error"))
                    .when(eventPublisher).publishEvent((Object) any());

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            // Should not throw
            assertThatCode(() -> listener.onPostPersist(entity)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("onPostUpdate with normal (non-soft-delete) entity")
    class OnPostUpdateNormalTests {

        @Test
        @DisplayName("Should not publish when config is null (unannotated entity)")
        void shouldNotPublishWhenConfigIsNull() {
            PlainEntity entity = new PlainEntity();
            entity.id = 1L;

            listener.onPostUpdate(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
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
    }

    @EntityEventConfig(
            onCreate = "SOFT_CREATED",
            onUpdate = "SOFT_UPDATED",
            onDelete = "SOFT_DELETED"
    )
    static class SoftDeleteEntity implements SoftDeletable {
        @Id
        Long id;
        boolean deleted;

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
    static class SoftDeleteNoDeleteEntity implements SoftDeletable {
        @Id
        Long id;
        boolean deleted;

        @Override
        public boolean isDeleted() {
            return deleted;
        }
    }

    @EntityEventConfig(
            watchProperties = {"name", "email"},
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class WatchPropertiesEntity {
        @Id
        Long id;
        String name;
        String email;
    }

    @EntityEventConfig(
            ignoreProperties = {"updatedAt"},
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class IgnorePropertiesEntity {
        @Id
        Long id;
        String name;
    }

    @EntityEventConfig(
            onCreate = "PAYLOAD_CREATED",
            onUpdate = "PAYLOAD_UPDATED",
            onDelete = "PAYLOAD_DELETED"
    )
    static class PayloadProviderEntity implements EntityEventPayloadProvider {
        @Id
        Long id;
        String customField;

        @Override
        public Map<String, Object> getEventPayloadData() {
            return Map.of("customField", customField);
        }
    }

    @EntityEventConfig(
            onCreate = "NULL_PAYLOAD_CREATED",
            onUpdate = "NULL_PAYLOAD_UPDATED",
            onDelete = "NULL_PAYLOAD_DELETED"
    )
    static class NullPayloadProviderEntity implements EntityEventPayloadProvider {
        @Id
        Long id;

        @Override
        public Map<String, Object> getEventPayloadData() {
            return null;
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

    // Entity without @EntityEventConfig
    static class PlainEntity {
        @Id
        Long id;
    }
}
