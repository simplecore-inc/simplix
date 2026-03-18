package dev.simplecore.simplix.core.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventMessage")
class EventMessageTest {

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("should create with 5-arg factory without changedProperties")
        void shouldCreateWith5ArgFactory() {
            Map<String, Object> payload = Map.of("key", "value");
            Map<String, Object> metadata = Map.of("actorId", "user1");

            EventMessage msg = EventMessage.of("123", "User", "USER_CREATED", payload, metadata);

            assertThat(msg.aggregateId()).isEqualTo("123");
            assertThat(msg.aggregateType()).isEqualTo("User");
            assertThat(msg.eventType()).isEqualTo("USER_CREATED");
            assertThat(msg.payload()).isEqualTo(payload);
            assertThat(msg.metadata()).isEqualTo(metadata);
            assertThat(msg.changedProperties()).isNull();
            assertThat(msg.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should create with 6-arg factory with changedProperties")
        void shouldCreateWith6ArgFactory() {
            Set<String> changed = Set.of("name", "email");
            EventMessage msg = EventMessage.of("456", "User", "USER_UPDATED",
                    Map.of(), Map.of(), changed);

            assertThat(msg.changedProperties()).isEqualTo(changed);
        }

        @Test
        @DisplayName("should create with full constructor")
        void shouldCreateWithFullConstructor() {
            Instant now = Instant.now();
            EventMessage msg = new EventMessage("1", "Order", "ORDER_PLACED",
                    Map.of(), Map.of(), Set.of("status"), now);

            assertThat(msg.occurredAt()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("Payload helpers")
    class PayloadHelpers {

        @Test
        @DisplayName("payloadString should return string value")
        void payloadStringShouldReturnValue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("name", "John"), Map.of());

            assertThat(msg.payloadString("name")).isEqualTo("John");
        }

        @Test
        @DisplayName("payloadString should return null for missing key")
        void payloadStringShouldReturnNullForMissing() {
            EventMessage msg = EventMessage.of("1", "T", "E", Map.of(), Map.of());
            assertThat(msg.payloadString("missing")).isNull();
        }

        @Test
        @DisplayName("payloadString should return null when payload is null")
        void payloadStringShouldReturnNullWhenPayloadNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.payloadString("key")).isNull();
        }

        @Test
        @DisplayName("payloadLong should return Long value")
        void payloadLongShouldReturnValue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", 42L), Map.of());

            assertThat(msg.payloadLong("count")).isEqualTo(42L);
        }

        @Test
        @DisplayName("payloadLong should convert Number to Long")
        void payloadLongShouldConvertNumber() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", 42), Map.of());

            assertThat(msg.payloadLong("count")).isEqualTo(42L);
        }

        @Test
        @DisplayName("payloadLong should parse String to Long")
        void payloadLongShouldParseString() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", "99"), Map.of());

            assertThat(msg.payloadLong("count")).isEqualTo(99L);
        }

        @Test
        @DisplayName("payloadLong should return null for invalid string")
        void payloadLongShouldReturnNullForInvalid() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", "abc"), Map.of());

            assertThat(msg.payloadLong("count")).isNull();
        }

        @Test
        @DisplayName("payloadLong should return null for other types")
        void payloadLongShouldReturnNullForOtherTypes() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("data", List.of(1, 2)), Map.of());

            assertThat(msg.payloadLong("data")).isNull();
        }

        @Test
        @DisplayName("payloadLong should return null when payload is null")
        void payloadLongShouldReturnNullWhenPayloadNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.payloadLong("key")).isNull();
        }

        @Test
        @DisplayName("payloadInt should return Integer value")
        void payloadIntShouldReturnValue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", 10), Map.of());

            assertThat(msg.payloadInt("count")).isEqualTo(10);
        }

        @Test
        @DisplayName("payloadInt should convert Number to Integer")
        void payloadIntShouldConvertNumber() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", 10L), Map.of());

            assertThat(msg.payloadInt("count")).isEqualTo(10);
        }

        @Test
        @DisplayName("payloadInt should parse String to Integer")
        void payloadIntShouldParseString() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", "7"), Map.of());

            assertThat(msg.payloadInt("count")).isEqualTo(7);
        }

        @Test
        @DisplayName("payloadInt should return null for invalid string")
        void payloadIntShouldReturnNullForInvalid() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("count", "xyz"), Map.of());

            assertThat(msg.payloadInt("count")).isNull();
        }

        @Test
        @DisplayName("payloadInt should return null for other types")
        void payloadIntShouldReturnNullForOtherTypes() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("data", true), Map.of());

            assertThat(msg.payloadInt("data")).isNull();
        }

        @Test
        @DisplayName("payloadInt should return null when payload is null")
        void payloadIntShouldReturnNullWhenPayloadNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.payloadInt("key")).isNull();
        }

        @Test
        @DisplayName("payloadList should return list value")
        void payloadListShouldReturnValue() {
            List<String> expected = List.of("a", "b", "c");
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("items", expected), Map.of());

            List<String> result = msg.payloadList("items");
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("payloadList should return empty list for non-list")
        void payloadListShouldReturnEmptyForNonList() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of("items", "not-a-list"), Map.of());

            assertThat(msg.<String>payloadList("items")).isEmpty();
        }

        @Test
        @DisplayName("payloadList should return empty list when payload is null")
        void payloadListShouldReturnEmptyWhenPayloadNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.<String>payloadList("items")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Metadata helpers")
    class MetadataHelpers {

        @Test
        @DisplayName("actor should return actorId from metadata")
        void actorShouldReturnActorId() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of("actorId", "admin"));

            assertThat(msg.actor()).isEqualTo("admin");
        }

        @Test
        @DisplayName("metadataString should return value")
        void metadataStringShouldReturnValue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of("source", "api"));

            assertThat(msg.metadataString("source")).isEqualTo("api");
        }

        @Test
        @DisplayName("metadataString should return null for missing key")
        void metadataStringShouldReturnNullForMissing() {
            EventMessage msg = EventMessage.of("1", "T", "E", Map.of(), Map.of());
            assertThat(msg.metadataString("missing")).isNull();
        }

        @Test
        @DisplayName("metadataString should return null when metadata is null")
        void metadataStringShouldReturnNullWhenNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.metadataString("key")).isNull();
            assertThat(msg.actor()).isNull();
        }

        @Test
        @DisplayName("isSoftDelete should return true when softDelete is true")
        void isSoftDeleteShouldReturnTrue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of("softDelete", Boolean.TRUE));

            assertThat(msg.isSoftDelete()).isTrue();
        }

        @Test
        @DisplayName("isSoftDelete should return false when not present")
        void isSoftDeleteShouldReturnFalse() {
            EventMessage msg = EventMessage.of("1", "T", "E", Map.of(), Map.of());
            assertThat(msg.isSoftDelete()).isFalse();
        }

        @Test
        @DisplayName("isSoftDelete should return false when metadata is null")
        void isSoftDeleteShouldReturnFalseWhenNull() {
            EventMessage msg = new EventMessage("1", "T", "E", null, null, null, Instant.now());
            assertThat(msg.isSoftDelete()).isFalse();
        }
    }

    @Nested
    @DisplayName("Type checks")
    class TypeChecks {

        @Test
        @DisplayName("isType should match event type")
        void isTypeShouldMatch() {
            EventMessage msg = EventMessage.of("1", "T", "USER_CREATED", Map.of(), Map.of());
            assertThat(msg.isType("USER_CREATED")).isTrue();
            assertThat(msg.isType("OTHER")).isFalse();
            assertThat(msg.isType(null)).isFalse();
        }

        @Test
        @DisplayName("isAggregate should match aggregate type")
        void isAggregateShouldMatch() {
            EventMessage msg = EventMessage.of("1", "User", "E", Map.of(), Map.of());
            assertThat(msg.isAggregate("User")).isTrue();
            assertThat(msg.isAggregate("Order")).isFalse();
            assertThat(msg.isAggregate(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Changed property checks")
    class ChangedPropertyChecks {

        @Test
        @DisplayName("hasChangedProperty should return true when property changed")
        void hasChangedPropertyShouldReturnTrue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of(), Set.of("name", "email"));

            assertThat(msg.hasChangedProperty("name")).isTrue();
            assertThat(msg.hasChangedProperty("email")).isTrue();
        }

        @Test
        @DisplayName("hasChangedProperty should return false when property not changed")
        void hasChangedPropertyShouldReturnFalse() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of(), Set.of("name"));

            assertThat(msg.hasChangedProperty("age")).isFalse();
        }

        @Test
        @DisplayName("hasChangedProperty should return false when changedProperties is null")
        void hasChangedPropertyShouldReturnFalseWhenNull() {
            EventMessage msg = EventMessage.of("1", "T", "E", Map.of(), Map.of());
            assertThat(msg.hasChangedProperty("name")).isFalse();
        }

        @Test
        @DisplayName("hasAnyChangedProperty should return true for any match")
        void hasAnyChangedPropertyShouldReturnTrue() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of(), Set.of("name", "email"));

            assertThat(msg.hasAnyChangedProperty("age", "email")).isTrue();
        }

        @Test
        @DisplayName("hasAnyChangedProperty should return false for no match")
        void hasAnyChangedPropertyShouldReturnFalse() {
            EventMessage msg = EventMessage.of("1", "T", "E",
                    Map.of(), Map.of(), Set.of("name"));

            assertThat(msg.hasAnyChangedProperty("age", "phone")).isFalse();
        }

        @Test
        @DisplayName("hasAnyChangedProperty should return false when changedProperties is null")
        void hasAnyChangedPropertyShouldReturnFalseWhenNull() {
            EventMessage msg = EventMessage.of("1", "T", "E", Map.of(), Map.of());
            assertThat(msg.hasAnyChangedProperty("name", "email")).isFalse();
        }
    }
}
