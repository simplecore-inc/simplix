package dev.simplecore.simplix.hibernate.cache.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PendingEviction DTO.
 */
@DisplayName("PendingEviction Tests")
class PendingEvictionTest {

    @Nested
    @DisplayName("Factory method tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() should create PendingEviction with all fields")
        void ofShouldCreateWithAllFields() {
            // Given
            Class<?> entityClass = TestEntity.class;
            Long entityId = 123L;
            String region = "test-region";
            PendingEviction.EvictionOperation operation = PendingEviction.EvictionOperation.UPDATE;

            // When
            PendingEviction result = PendingEviction.of(entityClass, entityId, region, operation);

            // Then
            assertThat(result.getEntityClassName()).isEqualTo(TestEntity.class.getName());
            assertThat(result.getEntityId()).isEqualTo("123");
            assertThat(result.getRegion()).isEqualTo("test-region");
            assertThat(result.getOperation()).isEqualTo(PendingEviction.EvictionOperation.UPDATE);
            assertThat(result.isEvictQueryCache()).isTrue();
            assertThat(result.getTimestamp()).isPositive();
        }

        @Test
        @DisplayName("of() with evictQueryCache should respect the flag")
        void ofWithEvictQueryCacheShouldRespectFlag() {
            // Given
            Class<?> entityClass = TestEntity.class;

            // When
            PendingEviction withQueryCache = PendingEviction.of(
                    entityClass, 1L, null, PendingEviction.EvictionOperation.INSERT, true);
            PendingEviction withoutQueryCache = PendingEviction.of(
                    entityClass, 1L, null, PendingEviction.EvictionOperation.INSERT, false);

            // Then
            assertThat(withQueryCache.isEvictQueryCache()).isTrue();
            assertThat(withoutQueryCache.isEvictQueryCache()).isFalse();
        }

        @Test
        @DisplayName("of() with null entityId should create bulk eviction")
        void ofWithNullEntityIdShouldCreateBulkEviction() {
            // When
            PendingEviction result = PendingEviction.of(
                    TestEntity.class, null, null, PendingEviction.EvictionOperation.BULK_UPDATE);

            // Then
            assertThat(result.getEntityId()).isNull();
            assertThat(result.getOperation()).isEqualTo(PendingEviction.EvictionOperation.BULK_UPDATE);
        }

        @Test
        @DisplayName("of() with null entityClass should handle gracefully")
        void ofWithNullEntityClassShouldHandleGracefully() {
            // When
            PendingEviction result = PendingEviction.of(null, 1L, null, PendingEviction.EvictionOperation.UPDATE);

            // Then
            assertThat(result.getEntityClassName()).isNull();
            assertThat(result.getEntityClass()).isNull();
        }
    }

    @Nested
    @DisplayName("getEntityClass() tests")
    class GetEntityClassTests {

        @Test
        @DisplayName("getEntityClass() should resolve existing class")
        void getEntityClassShouldResolveExistingClass() {
            // Given
            PendingEviction eviction = PendingEviction.of(
                    TestEntity.class, 1L, null, PendingEviction.EvictionOperation.UPDATE);

            // When
            Class<?> result = eviction.getEntityClass();

            // Then
            assertThat(result).isEqualTo(TestEntity.class);
        }

        @Test
        @DisplayName("getEntityClass() should return null for non-existent class")
        void getEntityClassShouldReturnNullForNonExistentClass() {
            // Given
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName("com.nonexistent.FakeClass")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When
            Class<?> result = eviction.getEntityClass();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getEntityClass() should return null for null class name")
        void getEntityClassShouldReturnNullForNullClassName() {
            // Given
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName(null)
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When
            Class<?> result = eviction.getEntityClass();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getEntityClass() should return null for empty class name")
        void getEntityClassShouldReturnNullForEmptyClassName() {
            // Given
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName("")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // When
            Class<?> result = eviction.getEntityClass();

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Serialization tests")
    class SerializationTests {

        @Test
        @DisplayName("PendingEviction should be serializable")
        void shouldBeSerializable() throws Exception {
            // Given
            PendingEviction original = PendingEviction.of(
                    TestEntity.class, 123L, "test-region",
                    PendingEviction.EvictionOperation.UPDATE, true);

            // When - serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(original);
            oos.close();

            // When - deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            PendingEviction deserialized = (PendingEviction) ois.readObject();
            ois.close();

            // Then
            assertThat(deserialized.getEntityClassName()).isEqualTo(original.getEntityClassName());
            assertThat(deserialized.getEntityId()).isEqualTo(original.getEntityId());
            assertThat(deserialized.getRegion()).isEqualTo(original.getRegion());
            assertThat(deserialized.getOperation()).isEqualTo(original.getOperation());
            assertThat(deserialized.isEvictQueryCache()).isEqualTo(original.isEvictQueryCache());
            assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
        }

        @Test
        @DisplayName("getEntityClass() should work after deserialization")
        void getEntityClassShouldWorkAfterDeserialization() throws Exception {
            // Given
            PendingEviction original = PendingEviction.of(
                    TestEntity.class, 1L, null, PendingEviction.EvictionOperation.UPDATE);

            // When - serialize and deserialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(original);
            oos.close();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            PendingEviction deserialized = (PendingEviction) ois.readObject();
            ois.close();

            // Then - class resolution should work
            assertThat(deserialized.getEntityClass()).isEqualTo(TestEntity.class);
        }
    }

    @Nested
    @DisplayName("EvictionOperation enum tests")
    class EvictionOperationTests {

        @Test
        @DisplayName("All operation types should be defined")
        void allOperationTypesShouldBeDefined() {
            PendingEviction.EvictionOperation[] operations = PendingEviction.EvictionOperation.values();

            assertThat(operations).containsExactlyInAnyOrder(
                    PendingEviction.EvictionOperation.INSERT,
                    PendingEviction.EvictionOperation.UPDATE,
                    PendingEviction.EvictionOperation.DELETE,
                    PendingEviction.EvictionOperation.BULK_UPDATE,
                    PendingEviction.EvictionOperation.BULK_DELETE
            );
        }
    }

    @Nested
    @DisplayName("Builder tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder default evictQueryCache should be true")
        void builderDefaultEvictQueryCacheShouldBeTrue() {
            // When
            PendingEviction eviction = PendingEviction.builder()
                    .entityClassName("Test")
                    .operation(PendingEviction.EvictionOperation.UPDATE)
                    .build();

            // Then
            assertThat(eviction.isEvictQueryCache()).isTrue();
        }
    }

    // Test entity class for testing
    private static class TestEntity {
        private Long id;
        private String name;
    }
}
