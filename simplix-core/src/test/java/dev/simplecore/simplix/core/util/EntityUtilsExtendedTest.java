package dev.simplecore.simplix.core.util;

import jakarta.persistence.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EntityUtils - Extended Coverage")
class EntityUtilsExtendedTest {

    static class SampleEntity {
        @Id
        private Long id;
        private String name;

        public SampleEntity() {}
        public SampleEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class EntityWithoutId {
        private String name;
    }

    static class SourceDto {
        private String name;
        private Integer age;

        public SourceDto(String name, Integer age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public Integer getAge() { return age; }
    }

    static class TargetEntity {
        private String name;
        private Integer age;

        public String getName() { return name; }
        public Integer getAge() { return age; }
    }

    @Nested
    @DisplayName("convertToEntity")
    class ConvertToEntity {

        @Test
        @DisplayName("should return same instance when types match")
        void shouldReturnSameInstance() {
            SampleEntity entity = new SampleEntity(1L, "test");
            SampleEntity result = EntityUtils.convertToEntity(entity, SampleEntity.class);
            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("should convert different type using ModelMapper")
        void shouldConvertDifferentType() {
            SourceDto dto = new SourceDto("John", 30);
            TargetEntity result = EntityUtils.convertToEntity(dto, TargetEntity.class);
            assertThat(result.getName()).isEqualTo("John");
            assertThat(result.getAge()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("getEntityId")
    class GetEntityId {

        @Test
        @DisplayName("should extract @Id field value")
        void shouldExtractId() {
            SampleEntity entity = new SampleEntity(42L, "test");
            Long id = EntityUtils.getEntityId(entity);
            assertThat(id).isEqualTo(42L);
        }

        @Test
        @DisplayName("should throw when no @Id field found")
        void shouldThrowWhenNoId() {
            EntityWithoutId entity = new EntityWithoutId();
            assertThatThrownBy(() -> EntityUtils.<EntityWithoutId, Long>getEntityId(entity))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No @Id field found");
        }
    }
}
