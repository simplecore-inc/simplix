package dev.simplecore.simplix.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DtoUtils")
class DtoUtilsTest {

    // Simple source/target POJOs for testing
    static class SourceEntity {
        private String name;
        private int age;

        public SourceEntity() {}

        public SourceEntity(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    static class TargetDto {
        private String name;
        private int age;

        public TargetDto() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Nested
    @DisplayName("toDto")
    class ToDto {

        @Test
        @DisplayName("should convert entity to DTO with matching fields")
        void shouldConvertEntityToDto() {
            SourceEntity entity = new SourceEntity("John", 30);

            TargetDto dto = DtoUtils.toDto(entity, TargetDto.class);

            assertThat(dto).isNotNull();
            assertThat(dto.getName()).isEqualTo("John");
            assertThat(dto.getAge()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("toDtoList")
    class ToDtoList {

        @Test
        @DisplayName("should convert list of entities to list of DTOs")
        void shouldConvertEntityListToDtoList() {
            List<SourceEntity> entities = List.of(
                new SourceEntity("Alice", 25),
                new SourceEntity("Bob", 35)
            );

            List<TargetDto> dtos = DtoUtils.toDtoList(entities, TargetDto.class);

            assertThat(dtos).hasSize(2);
            assertThat(dtos.get(0).getName()).isEqualTo("Alice");
            assertThat(dtos.get(1).getName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("should return empty list for empty input")
        void shouldReturnEmptyListForEmptyInput() {
            List<TargetDto> dtos = DtoUtils.toDtoList(Collections.emptyList(), TargetDto.class);

            assertThat(dtos).isEmpty();
        }
    }

    @Nested
    @DisplayName("toDtoPage")
    class ToDtoPage {

        @Test
        @DisplayName("should convert page of entities to page of DTOs preserving pagination")
        void shouldConvertPagePreservingPagination() {
            List<SourceEntity> entities = List.of(
                new SourceEntity("Alice", 25),
                new SourceEntity("Bob", 35)
            );
            PageRequest pageRequest = PageRequest.of(0, 10);
            Page<SourceEntity> entityPage = new PageImpl<>(entities, pageRequest, 100);

            Page<TargetDto> dtoPage = DtoUtils.toDtoPage(entityPage, TargetDto.class);

            assertThat(dtoPage.getContent()).hasSize(2);
            assertThat(dtoPage.getTotalElements()).isEqualTo(100);
            assertThat(dtoPage.getPageable()).isEqualTo(pageRequest);
            assertThat(dtoPage.getContent().get(0).getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("should handle empty page")
        void shouldHandleEmptyPage() {
            PageRequest pageRequest = PageRequest.of(0, 10);
            Page<SourceEntity> entityPage = new PageImpl<>(Collections.emptyList(), pageRequest, 0);

            Page<TargetDto> dtoPage = DtoUtils.toDtoPage(entityPage, TargetDto.class);

            assertThat(dtoPage.getContent()).isEmpty();
            assertThat(dtoPage.getTotalElements()).isZero();
        }
    }
}
