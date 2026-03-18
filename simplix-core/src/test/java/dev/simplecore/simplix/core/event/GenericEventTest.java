package dev.simplecore.simplix.core.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GenericEvent")
class GenericEventTest {

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build event with required fields")
        void shouldBuildWithRequiredFields() {
            GenericEvent event = GenericEvent.builder()
                .eventType("USER_CREATED")
                .aggregateId("user-123")
                .build();

            assertThat(event.getEventType()).isEqualTo("USER_CREATED");
            assertThat(event.getAggregateId()).isEqualTo("user-123");
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getOccurredAt()).isNotNull();
            assertThat(event.getMetadata()).isNotNull();
        }

        @Test
        @DisplayName("should build event with all optional fields")
        void shouldBuildWithAllFields() {
            Instant now = Instant.now();
            Map<String, Object> metadata = Map.of("key", "value");

            GenericEvent event = GenericEvent.builder()
                .eventId("evt-001")
                .eventType("ORDER_PLACED")
                .aggregateId("order-456")
                .occurredAt(now)
                .userId("user-789")
                .tenantId("tenant-001")
                .metadata(metadata)
                .payload("order-data")
                .build();

            assertThat(event.getEventId()).isEqualTo("evt-001");
            assertThat(event.getEventType()).isEqualTo("ORDER_PLACED");
            assertThat(event.getAggregateId()).isEqualTo("order-456");
            assertThat(event.getOccurredAt()).isEqualTo(now);
            assertThat(event.getUserId()).isEqualTo("user-789");
            assertThat(event.getTenantId()).isEqualTo("tenant-001");
            assertThat(event.getMetadata()).isEqualTo(metadata);
            assertThat(event.getPayload()).isEqualTo("order-data");
        }

        @Test
        @DisplayName("should throw for missing eventType")
        void shouldThrowForMissingEventType() {
            assertThatThrownBy(() ->
                GenericEvent.builder()
                    .aggregateId("id-1")
                    .build()
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("eventType is required");
        }

        @Test
        @DisplayName("should throw for blank eventType")
        void shouldThrowForBlankEventType() {
            assertThatThrownBy(() ->
                GenericEvent.builder()
                    .eventType("  ")
                    .aggregateId("id-1")
                    .build()
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("eventType is required");
        }

        @Test
        @DisplayName("should throw for missing aggregateId")
        void shouldThrowForMissingAggregateId() {
            assertThatThrownBy(() ->
                GenericEvent.builder()
                    .eventType("TEST")
                    .build()
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("aggregateId is required");
        }

        @Test
        @DisplayName("should throw for blank aggregateId")
        void shouldThrowForBlankAggregateId() {
            assertThatThrownBy(() ->
                GenericEvent.builder()
                    .eventType("TEST")
                    .aggregateId("  ")
                    .build()
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("aggregateId is required");
        }

        @Test
        @DisplayName("should auto-generate eventId when not set")
        void shouldAutoGenerateEventId() {
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("id-1")
                .build();

            assertThat(event.getEventId()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("should auto-generate occurredAt when not set")
        void shouldAutoGenerateOccurredAt() {
            Instant before = Instant.now();
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("id-1")
                .build();
            Instant after = Instant.now();

            assertThat(event.getOccurredAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("should store aggregateType in metadata")
        void shouldStoreAggregateTypeInMetadata() {
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("id-1")
                .aggregateType("User")
                .build();

            assertThat(event.getMetadata()).containsEntry("aggregateType", "User");
        }

        @Test
        @DisplayName("should support addMetadata for single entries")
        void shouldSupportAddMetadata() {
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("id-1")
                .addMetadata("source", "api")
                .addMetadata("version", "1.0")
                .build();

            assertThat(event.getMetadata())
                .containsEntry("source", "api")
                .containsEntry("version", "1.0");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringMethod {

        @Test
        @DisplayName("should include key fields in toString")
        void shouldIncludeKeyFields() {
            GenericEvent event = GenericEvent.builder()
                .eventType("TEST")
                .aggregateId("id-1")
                .userId("user-1")
                .build();

            String result = event.toString();

            assertThat(result).contains("eventType='TEST'");
            assertThat(result).contains("aggregateId='id-1'");
            assertThat(result).contains("userId='user-1'");
        }
    }
}
