package dev.simplecore.simplix.messaging.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageHeaders")
class MessageHeadersTest {

    @Test
    @DisplayName("empty() should create headers with no entries")
    void emptyShouldHaveNoEntries() {
        MessageHeaders headers = MessageHeaders.empty();

        assertThat(headers.isEmpty()).isTrue();
        assertThat(headers.size()).isZero();
        assertThat(headers.toMap()).isEmpty();
    }

    @Test
    @DisplayName("of() should create headers from a map")
    void ofShouldCreateFromMap() {
        MessageHeaders headers = MessageHeaders.of(Map.of(
                MessageHeaders.CONTENT_TYPE, "application/json",
                MessageHeaders.SOURCE, "test-service"
        ));

        assertThat(headers.contentType()).isEqualTo("application/json");
        assertThat(headers.source()).isEqualTo("test-service");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("with() should return new instance with added entry")
    void withShouldReturnNewInstance() {
        MessageHeaders original = MessageHeaders.empty();
        MessageHeaders modified = original.with(MessageHeaders.CORRELATION_ID, "corr-123");

        assertThat(original.correlationId()).isNull();
        assertThat(original.isEmpty()).isTrue();

        assertThat(modified.correlationId()).isEqualTo("corr-123");
        assertThat(modified.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("with() should replace existing entry")
    void withShouldReplaceExisting() {
        MessageHeaders headers = MessageHeaders.empty()
                .with(MessageHeaders.CONTENT_TYPE, "application/json")
                .with(MessageHeaders.CONTENT_TYPE, "application/protobuf");

        assertThat(headers.contentType()).isEqualTo("application/protobuf");
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("get() should return Optional for existing key")
    void getShouldReturnOptionalForExistingKey() {
        MessageHeaders headers = MessageHeaders.empty()
                .with("custom-header", "custom-value");

        Optional<String> result = headers.get("custom-header");
        assertThat(result).isPresent().hasValue("custom-value");
    }

    @Test
    @DisplayName("get() should return empty for missing key")
    void getShouldReturnEmptyForMissingKey() {
        MessageHeaders headers = MessageHeaders.empty();

        Optional<String> result = headers.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("toMap() should return unmodifiable map")
    void toMapShouldReturnUnmodifiable() {
        MessageHeaders headers = MessageHeaders.of(Map.of("key", "value"));

        Map<String, String> map = headers.toMap();
        assertThat(map).hasSize(1);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> map.put("new", "entry")
        );
    }

    @Test
    @DisplayName("convenience accessors should return correct values")
    void convenienceAccessorsShouldWork() {
        MessageHeaders headers = MessageHeaders.empty()
                .with(MessageHeaders.CORRELATION_ID, "corr-1")
                .with(MessageHeaders.CONTENT_TYPE, "text/plain")
                .with(MessageHeaders.SOURCE, "source-1")
                .with(MessageHeaders.REPLY_CHANNEL, "reply-ch")
                .with(MessageHeaders.RETRY_COUNT, "3");

        assertThat(headers.correlationId()).isEqualTo("corr-1");
        assertThat(headers.contentType()).isEqualTo("text/plain");
        assertThat(headers.source()).isEqualTo("source-1");
        assertThat(headers.replyChannel()).isEqualTo("reply-ch");
        assertThat(headers.retryCount()).isEqualTo("3");
    }

    @Test
    @DisplayName("equals and hashCode should compare by entries")
    void equalsShouldCompareByEntries() {
        MessageHeaders a = MessageHeaders.of(Map.of("k1", "v1", "k2", "v2"));
        MessageHeaders b = MessageHeaders.empty().with("k1", "v1").with("k2", "v2");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
