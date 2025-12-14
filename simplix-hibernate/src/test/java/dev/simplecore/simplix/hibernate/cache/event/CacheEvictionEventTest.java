package dev.simplecore.simplix.hibernate.cache.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CacheEvictionEvent.
 */
@DisplayName("CacheEvictionEvent Tests")
class CacheEvictionEventTest {

    @Nested
    @DisplayName("Builder tests")
    class BuilderTests {

        @Test
        @DisplayName("Builder should create event with all fields")
        void builderShouldCreateEventWithAllFields() {
            // Given
            String entityClass = "com.example.User";
            String entityId = "123";
            String region = "users";
            String operation = "UPDATE";
            Long timestamp = System.currentTimeMillis();
            String nodeId = "node-1";

            // When
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass(entityClass)
                    .entityId(entityId)
                    .region(region)
                    .operation(operation)
                    .timestamp(timestamp)
                    .nodeId(nodeId)
                    .build();

            // Then
            assertThat(event.getEntityClass()).isEqualTo(entityClass);
            assertThat(event.getEntityId()).isEqualTo(entityId);
            assertThat(event.getRegion()).isEqualTo(region);
            assertThat(event.getOperation()).isEqualTo(operation);
            assertThat(event.getTimestamp()).isEqualTo(timestamp);
            assertThat(event.getNodeId()).isEqualTo(nodeId);
            assertThat(event.getEventId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("Builder should generate unique eventId automatically")
        void builderShouldGenerateUniqueEventId() {
            // When
            CacheEvictionEvent event1 = CacheEvictionEvent.builder()
                    .entityClass("Test")
                    .build();
            CacheEvictionEvent event2 = CacheEvictionEvent.builder()
                    .entityClass("Test")
                    .build();

            // Then
            assertThat(event1.getEventId()).isNotNull();
            assertThat(event2.getEventId()).isNotNull();
            assertThat(event1.getEventId()).isNotEqualTo(event2.getEventId());
        }
    }

    @Nested
    @DisplayName("withNodeId() tests")
    class WithNodeIdTests {

        @Test
        @DisplayName("withNodeId() should create copy with new nodeId")
        void withNodeIdShouldCreateCopyWithNewNodeId() {
            // Given
            CacheEvictionEvent original = CacheEvictionEvent.builder()
                    .entityClass("com.example.User")
                    .entityId("123")
                    .nodeId("original-node")
                    .build();

            // When
            CacheEvictionEvent copy = original.withNodeId("new-node");

            // Then
            assertThat(copy.getNodeId()).isEqualTo("new-node");
            assertThat(copy.getEntityClass()).isEqualTo(original.getEntityClass());
            assertThat(copy.getEntityId()).isEqualTo(original.getEntityId());
            assertThat(copy.getEventId()).isEqualTo(original.getEventId());
            // Original should be unchanged
            assertThat(original.getNodeId()).isEqualTo("original-node");
        }
    }

    @Nested
    @DisplayName("Serialization tests")
    class SerializationTests {

        @Test
        @DisplayName("CacheEvictionEvent should be serializable")
        void shouldBeSerializable() throws Exception {
            // Given
            CacheEvictionEvent original = CacheEvictionEvent.builder()
                    .entityClass("com.example.User")
                    .entityId("123")
                    .region("users")
                    .operation("UPDATE")
                    .timestamp(System.currentTimeMillis())
                    .nodeId("node-1")
                    .build();

            // When - serialize
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(original);
            oos.close();

            // When - deserialize
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            CacheEvictionEvent deserialized = (CacheEvictionEvent) ois.readObject();
            ois.close();

            // Then
            assertThat(deserialized.getEventId()).isEqualTo(original.getEventId());
            assertThat(deserialized.getEntityClass()).isEqualTo(original.getEntityClass());
            assertThat(deserialized.getEntityId()).isEqualTo(original.getEntityId());
            assertThat(deserialized.getRegion()).isEqualTo(original.getRegion());
            assertThat(deserialized.getOperation()).isEqualTo(original.getOperation());
            assertThat(deserialized.getTimestamp()).isEqualTo(original.getTimestamp());
            assertThat(deserialized.getNodeId()).isEqualTo(original.getNodeId());
        }
    }

    @Nested
    @DisplayName("JSON Deserialization tests")
    class JsonDeserializationTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("JsonCreator should preserve eventId during deserialization")
        void jsonCreatorShouldPreserveEventId() throws Exception {
            // Given
            String eventId = "test-event-id-12345";
            String json = """
                    {
                        "eventId": "%s",
                        "entityClass": "com.example.User",
                        "entityId": "123",
                        "operation": "UPDATE"
                    }
                    """.formatted(eventId);

            // When
            CacheEvictionEvent event = objectMapper.readValue(json, CacheEvictionEvent.class);

            // Then
            assertThat(event.getEventId()).isEqualTo(eventId);
        }

        @Test
        @DisplayName("JsonCreator should generate eventId if null in JSON")
        void jsonCreatorShouldGenerateEventIdIfNull() throws Exception {
            // Given
            String json = """
                    {
                        "entityClass": "com.example.User",
                        "entityId": "123",
                        "operation": "UPDATE"
                    }
                    """;

            // When
            CacheEvictionEvent event = objectMapper.readValue(json, CacheEvictionEvent.class);

            // Then
            assertThat(event.getEventId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("JsonCreator should generate eventId if empty in JSON")
        void jsonCreatorShouldGenerateEventIdIfEmpty() throws Exception {
            // Given
            String json = """
                    {
                        "eventId": "",
                        "entityClass": "com.example.User",
                        "entityId": "123"
                    }
                    """;

            // When
            CacheEvictionEvent event = objectMapper.readValue(json, CacheEvictionEvent.class);

            // Then
            assertThat(event.getEventId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("Round-trip JSON serialization should preserve eventId")
        void roundTripJsonSerializationShouldPreserveEventId() throws Exception {
            // Given
            CacheEvictionEvent original = CacheEvictionEvent.builder()
                    .entityClass("com.example.User")
                    .entityId("123")
                    .operation("UPDATE")
                    .nodeId("node-1")
                    .timestamp(System.currentTimeMillis())
                    .build();

            // When
            String json = objectMapper.writeValueAsString(original);
            CacheEvictionEvent deserialized = objectMapper.readValue(json, CacheEvictionEvent.class);

            // Then
            assertThat(deserialized.getEventId()).isEqualTo(original.getEventId());
            assertThat(deserialized.getEntityClass()).isEqualTo(original.getEntityClass());
            assertThat(deserialized.getEntityId()).isEqualTo(original.getEntityId());
            assertThat(deserialized.getNodeId()).isEqualTo(original.getNodeId());
        }
    }

    @Nested
    @DisplayName("Immutability tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Event fields should be immutable")
        void eventFieldsShouldBeImmutable() {
            // Given
            CacheEvictionEvent event = CacheEvictionEvent.builder()
                    .entityClass("com.example.User")
                    .entityId("123")
                    .nodeId("node-1")
                    .build();

            // When - create a new event with different nodeId
            CacheEvictionEvent modified = event.withNodeId("node-2");

            // Then - original should be unchanged
            assertThat(event.getNodeId()).isEqualTo("node-1");
            assertThat(modified.getNodeId()).isEqualTo("node-2");
        }
    }
}
