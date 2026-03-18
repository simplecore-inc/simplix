package dev.simplecore.simplix.web.service;

import dev.simplecore.simplix.core.repository.SimpliXBaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXBaseService - base CRUD service with ModelMapper integration")
class SimpliXBaseServiceTest {

    @Mock
    private SimpliXBaseRepository<TestEntity, Long> repository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ModelMapper modelMapper;

    private TestService service;

    @BeforeEach
    void setUp() {
        service = new TestService(repository, entityManager);
        service.modelMapper = modelMapper;
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Should return entity when found")
        void entityFound() {
            TestEntity entity = new TestEntity(1L, "test");
            when(repository.findById(1L)).thenReturn(Optional.of(entity));

            Optional<TestEntity> result = service.findById(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("test");
        }

        @Test
        @DisplayName("Should return empty when not found")
        void entityNotFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<TestEntity> result = service.findById(999L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById with projection")
    class FindByIdWithProjection {

        @Test
        @DisplayName("Should map entity to projection type")
        void mapToProjection() {
            TestEntity entity = new TestEntity(1L, "test");
            TestDto dto = new TestDto("test");
            when(repository.findById(1L)).thenReturn(Optional.of(entity));
            when(modelMapper.map(entity, TestDto.class)).thenReturn(dto);

            Optional<TestDto> result = service.findById(1L, TestDto.class);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("test");
        }

        @Test
        @DisplayName("Should return empty when entity not found")
        void notFound() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<TestDto> result = service.findById(999L, TestDto.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllById")
    class FindAllById {

        @Test
        @DisplayName("Should return all entities matching IDs")
        void findAll() {
            List<TestEntity> entities = List.of(
                    new TestEntity(1L, "a"),
                    new TestEntity(2L, "b"));
            when(repository.findAllById(List.of(1L, 2L))).thenReturn(entities);

            List<TestEntity> result = service.findAllById(List.of(1L, 2L));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list when no IDs match")
        void findNone() {
            when(repository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

            List<TestEntity> result = service.findAllById(List.of(999L));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllById with projection")
    class FindAllByIdWithProjection {

        @Test
        @DisplayName("Should map all entities to projection type")
        void mapAll() {
            List<TestEntity> entities = List.of(new TestEntity(1L, "a"));
            when(repository.findAllById(List.of(1L))).thenReturn(entities);
            when(modelMapper.map(any(TestEntity.class), eq(TestDto.class)))
                    .thenReturn(new TestDto("a"));

            List<TestDto> result = service.findAllById(List.of(1L), TestDto.class);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("a");
        }

        @Test
        @DisplayName("Should return empty list when no IDs match for projection")
        void emptyProjection() {
            when(repository.findAllById(List.of(999L))).thenReturn(Collections.emptyList());

            List<TestDto> result = service.findAllById(List.of(999L), TestDto.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll pageable")
    class FindAllPageable {

        @Test
        @DisplayName("Should return paginated entities")
        void findAll() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity(1L, "a")));
            when(repository.findAll(pageable)).thenReturn(page);

            Page<TestEntity> result = service.findAll(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when no entities exist")
        void emptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TestEntity> page = new PageImpl<>(Collections.emptyList());
            when(repository.findAll(pageable)).thenReturn(page);

            Page<TestEntity> result = service.findAll(pageable);

            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("findAll pageable with projection")
    class FindAllPageableWithProjection {

        @Test
        @DisplayName("Should map paginated entities to projection type")
        void findAllWithProjection() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TestEntity> page = new PageImpl<>(List.of(new TestEntity(1L, "a")));
            when(repository.findAll(pageable)).thenReturn(page);
            when(modelMapper.map(any(TestEntity.class), eq(TestDto.class)))
                    .thenReturn(new TestDto("a"));

            Page<TestDto> result = service.findAll(pageable, TestDto.class);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("findAll with projection")
    class FindAllWithProjection {

        @Test
        @DisplayName("Should map all entities to projection list")
        void findAll() {
            when(repository.findAll()).thenReturn(List.of(new TestEntity(1L, "a")));
            when(modelMapper.map(any(TestEntity.class), eq(TestDto.class)))
                    .thenReturn(new TestDto("a"));

            List<TestDto> result = service.findAll(TestDto.class);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list for projection when no entities exist")
        void emptyProjectionList() {
            when(repository.findAll()).thenReturn(Collections.emptyList());

            List<TestDto> result = service.findAll(TestDto.class);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsById")
    class ExistsById {

        @Test
        @DisplayName("Should return true when entity exists")
        void exists() {
            when(repository.existsById(1L)).thenReturn(true);

            assertThat(service.existsById(1L)).isTrue();
        }

        @Test
        @DisplayName("Should return false when entity does not exist")
        void notExists() {
            when(repository.existsById(999L)).thenReturn(false);

            assertThat(service.existsById(999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("delete operations")
    class DeleteOperations {

        @Test
        @DisplayName("Should delete by ID")
        void deleteById() {
            service.deleteById(1L);

            verify(repository).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete entity")
        void deleteEntity() {
            TestEntity entity = new TestEntity(1L, "test");

            service.delete(entity);

            verify(repository).delete(entity);
        }

        @Test
        @DisplayName("Should delete all entities in iterable")
        void deleteAll() {
            List<TestEntity> entities = List.of(new TestEntity(1L, "a"));

            service.deleteAll(entities);

            verify(repository).deleteAll(entities);
        }

        @Test
        @DisplayName("Should delete all by IDs")
        void deleteAllByIds() {
            service.deleteAllByIds(List.of(1L, 2L));

            verify(repository, times(2)).deleteById(any());
        }

        @Test
        @DisplayName("Should handle empty ID list in deleteAllByIds")
        void deleteAllByIdsEmpty() {
            service.deleteAllByIds(Collections.emptyList());

            verify(repository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("save operations")
    class SaveOperations {

        @Test
        @DisplayName("Should save all entities")
        void saveAll() {
            List<TestEntity> entities = List.of(new TestEntity(1L, "a"));
            when(repository.saveAll(entities)).thenReturn(entities);

            List<? extends TestEntity> result = service.saveAll(entities);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should save and flush entity")
        void saveAndFlush() {
            TestEntity entity = new TestEntity(1L, "test");
            when(repository.saveAndFlush(entity)).thenReturn(entity);

            TestEntity result = service.saveAndFlush(entity);

            assertThat(result).isEqualTo(entity);
        }
    }

    @Nested
    @DisplayName("hasOwnerPermission")
    class HasOwnerPermission {

        @Test
        @DisplayName("Should return true from test implementation")
        void hasPermission() {
            assertThat(service.hasOwnerPermission("READ", 1L, null)).isTrue();
        }
    }

    // Test helper classes
    static class TestEntity {
        private Long id;
        private String name;

        TestEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
    }

    static class TestDto {
        private String name;

        TestDto(String name) { this.name = name; }

        public String getName() { return name; }
    }

    static class TestService extends SimpliXBaseService<TestEntity, Long> {
        TestService(SimpliXBaseRepository<TestEntity, Long> repository, EntityManager entityManager) {
            super(repository, entityManager);
        }

        @Override
        public boolean hasOwnerPermission(String permission, Long id, Object dto) {
            return true;
        }
    }
}
