package dev.simplecore.simplix.hibernate.event;

import dev.simplecore.simplix.core.entity.annotation.EntityEventConfig;
import jakarta.persistence.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EntityEventPublishingListener")
@ExtendWith(MockitoExtension.class)
class EntityEventPublishingListenerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EntityEventPublishingListener listener;

    @BeforeEach
    void setUp() {
        listener = new EntityEventPublishingListener();
        listener.setApplicationEventPublisher(eventPublisher);
        // Reset entity manager factory to null for unit tests
        listener.setEntityManagerFactory(null);
    }

    @AfterEach
    void tearDown() {
        // Restore to the mock publisher so other tests are not affected by null
        listener.setApplicationEventPublisher(eventPublisher);
    }

    @Nested
    @DisplayName("onPostPersist")
    class OnPostPersistTests {

        @Test
        @DisplayName("should publish event for annotated entity on persist")
        void shouldPublishEventOnPersist() {
            // Re-set publisher before verification to ensure static field is correct
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            listener.onPostPersist(entity);

            // publishEvent(Object) is a default method in ApplicationEventPublisher;
            // use explicit Object.class argument matcher to match the correct overload
            verify(eventPublisher).publishEvent((Object) any());
        }

        @Test
        @DisplayName("should not publish when publishCreate is false")
        void shouldNotPublishWhenCreateDisabled() {
            NoCreateEntity entity = new NoCreateEntity();
            entity.id = 1L;

            listener.onPostPersist(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }

        @Test
        @DisplayName("should not publish for entity without annotation")
        void shouldNotPublishForUnannotatedEntity() {
            PlainEntity entity = new PlainEntity();

            listener.onPostPersist(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("onPostRemove")
    class OnPostRemoveTests {

        @Test
        @DisplayName("should publish event for annotated entity on remove")
        void shouldPublishEventOnRemove() {
            // Re-set publisher before verification to ensure static field is correct
            listener.setApplicationEventPublisher(eventPublisher);

            TestEntity entity = new TestEntity();
            entity.id = 1L;

            listener.onPostRemove(entity);

            verify(eventPublisher).publishEvent((Object) any());
        }

        @Test
        @DisplayName("should not publish when publishDelete is false")
        void shouldNotPublishWhenDeleteDisabled() {
            NoDeleteEntity entity = new NoDeleteEntity();
            entity.id = 1L;

            listener.onPostRemove(entity);

            verify(eventPublisher, never()).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("onPostUpdate")
    class OnPostUpdateTests {

        @Test
        @DisplayName("should not publish update when no dirty properties captured")
        void shouldNotPublishWhenNoDirtyProperties() {
            TestEntity entity = new TestEntity();
            entity.id = 1L;

            // No @PreUpdate was called, so no dirty properties are captured
            listener.onPostUpdate(entity);

            // No event because PRE_UPDATE_DIRTY_PROPS has no entry
            verify(eventPublisher, never()).publishEvent((Object) any());
        }
    }

    @Nested
    @DisplayName("onPreUpdate")
    class OnPreUpdateTests {

        @Test
        @DisplayName("should not throw when entity manager factory is null")
        void shouldNotThrowWhenEmfNull() {
            TestEntity entity = new TestEntity();
            entity.id = 1L;

            // entityManagerFactory is null in setUp()
            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not process entity without annotation")
        void shouldNotProcessUnannotatedEntity() {
            PlainEntity entity = new PlainEntity();

            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not process entity with update publishing disabled")
        void shouldNotProcessWhenUpdateDisabled() {
            NoUpdateEntity entity = new NoUpdateEntity();
            entity.id = 1L;

            assertThatCode(() -> listener.onPreUpdate(entity)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("shouldPublish edge cases")
    class ShouldPublishEdgeCases {

        @Test
        @DisplayName("should not publish when event publisher is null")
        void shouldNotPublishWhenPublisherNull() {
            listener.setApplicationEventPublisher(null);
            TestEntity entity = new TestEntity();
            entity.id = 1L;

            listener.onPostPersist(entity);

            // Cannot verify a null publisher, but no exception should be thrown
        }

        @Test
        @DisplayName("should not publish when entity config is disabled")
        void shouldNotPublishWhenDisabled() {
            DisabledEntity entity = new DisabledEntity();

            listener.onPostPersist(entity);

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
            publishCreate = false,
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class NoCreateEntity {
        @Id
        Long id;
    }

    @EntityEventConfig(
            publishDelete = false,
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class NoDeleteEntity {
        @Id
        Long id;
    }

    @EntityEventConfig(
            publishUpdate = false,
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class NoUpdateEntity {
        @Id
        Long id;
    }

    @EntityEventConfig(
            enabled = false,
            onCreate = "CREATED",
            onUpdate = "UPDATED",
            onDelete = "DELETED"
    )
    static class DisabledEntity {
        @Id
        Long id;
    }

    // Entity without @EntityEventConfig
    static class PlainEntity {
        @Id
        Long id;
    }
}
