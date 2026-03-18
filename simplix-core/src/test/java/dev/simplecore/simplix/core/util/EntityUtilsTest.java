package dev.simplecore.simplix.core.util;

import jakarta.persistence.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EntityUtils")
class EntityUtilsTest {

    // Test entity with @Id annotation
    static class TestEntity {
        @Id
        private Long id;
        private String name;

        public TestEntity() {}

        public TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // Entity without @Id field
    static class NoIdEntity {
        private String name;

        public NoIdEntity() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // Source DTO for conversion testing
    static class SourceDto {
        private String name;

        public SourceDto() {}

        public SourceDto(String name) {
            this.name = name;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Nested
    @DisplayName("convertToEntity")
    class ConvertToEntity {

        @Test
        @DisplayName("should return same object when source class matches entity class")
        void shouldReturnSameObjectWhenClassesMatch() {
            TestEntity entity = new TestEntity(1L, "test");

            TestEntity result = EntityUtils.convertToEntity(entity, TestEntity.class);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("should convert DTO to entity when classes differ")
        void shouldConvertWhenClassesDiffer() {
            SourceDto dto = new SourceDto("converted");

            TestEntity result = EntityUtils.convertToEntity(dto, TestEntity.class);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("converted");
        }
    }

    @Nested
    @DisplayName("getEntityId")
    class GetEntityId {

        @Test
        @DisplayName("should extract @Id field value from entity")
        void shouldExtractIdFromEntity() {
            TestEntity entity = new TestEntity(42L, "test");

            Long id = EntityUtils.getEntityId(entity);

            assertThat(id).isEqualTo(42L);
        }

        @Test
        @DisplayName("should return null when @Id field has null value")
        void shouldReturnNullWhenIdIsNull() {
            TestEntity entity = new TestEntity(null, "test");

            Long id = EntityUtils.getEntityId(entity);

            assertThat(id).isNull();
        }

        @Test
        @DisplayName("should throw RuntimeException when entity has no @Id field")
        void shouldThrowWhenNoIdField() {
            NoIdEntity entity = new NoIdEntity();

            assertThatThrownBy(() -> EntityUtils.getEntityId(entity))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No @Id field found");
        }
    }
}
