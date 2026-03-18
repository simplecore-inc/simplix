package dev.simplecore.simplix.hibernate.event.config;

import dev.simplecore.simplix.hibernate.event.EntityEventPublishingListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EntityEventAutoConfiguration.
 * Verifies that the configuration creates the correct bean.
 */
@DisplayName("EntityEventAutoConfiguration Tests")
class EntityEventAutoConfigurationTest {

    @Nested
    @DisplayName("Bean creation")
    class BeanCreationTests {

        @Test
        @DisplayName("Should create EntityEventPublishingListener bean")
        void shouldCreateEntityEventPublishingListenerBean() {
            EntityEventAutoConfiguration config = new EntityEventAutoConfiguration();

            EntityEventPublishingListener listener = config.entityEventPublishingListener();

            assertThat(listener).isNotNull();
            assertThat(listener).isInstanceOf(EntityEventPublishingListener.class);
        }

        @Test
        @DisplayName("Should create new instance each time")
        void shouldCreateNewInstanceEachTime() {
            EntityEventAutoConfiguration config = new EntityEventAutoConfiguration();

            EntityEventPublishingListener listener1 = config.entityEventPublishingListener();
            EntityEventPublishingListener listener2 = config.entityEventPublishingListener();

            assertThat(listener1).isNotSameAs(listener2);
        }
    }
}
