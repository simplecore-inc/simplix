package dev.simplecore.simplix.core.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EntityEventPayloadProvider and SimpliXBaseEntity")
class EntityEventPayloadProviderTest {

    static class TestEntity extends SimpliXBaseEntity<Long> implements EntityEventPayloadProvider {
        private Long id;
        private String name;

        TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public Long getId() { return id; }

        @Override
        public void setId(Long id) { this.id = id; }

        @Override
        public Map<String, Object> getEventPayloadData() {
            return Map.of("id", id, "name", name);
        }
    }

    static class DefaultPayloadEntity implements EntityEventPayloadProvider {
        // Uses default implementation
    }

    @Test
    @DisplayName("should provide custom event payload from entity")
    void shouldProvidePayload() {
        TestEntity entity = new TestEntity(1L, "Test");
        Map<String, Object> payload = entity.getEventPayloadData();

        assertThat(payload).containsEntry("id", 1L);
        assertThat(payload).containsEntry("name", "Test");
    }

    @Test
    @DisplayName("should provide empty payload by default")
    void shouldProvideEmptyPayloadByDefault() {
        DefaultPayloadEntity entity = new DefaultPayloadEntity();
        assertThat(entity.getEventPayloadData()).isEmpty();
    }

    @Test
    @DisplayName("SimpliXBaseEntity should support getId and setId")
    void shouldSupportGetSetId() {
        TestEntity entity = new TestEntity(1L, "Test");
        assertThat(entity.getId()).isEqualTo(1L);

        entity.setId(2L);
        assertThat(entity.getId()).isEqualTo(2L);
    }
}
