package dev.simplecore.simplix.messaging.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Message")
class MessageTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should create message with all fields")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            MessageHeaders headers = MessageHeaders.empty()
                    .with(MessageHeaders.CONTENT_TYPE, "application/json");

            Message<String> message = Message.<String>builder()
                    .messageId("test-id")
                    .channel("my-channel")
                    .payload("hello")
                    .headers(headers)
                    .timestamp(now)
                    .build();

            assertThat(message.getMessageId()).isEqualTo("test-id");
            assertThat(message.getChannel()).isEqualTo("my-channel");
            assertThat(message.getPayload()).isEqualTo("hello");
            assertThat(message.getHeaders().contentType()).isEqualTo("application/json");
            assertThat(message.getTimestamp()).isEqualTo(now);
        }

        @Test
        @DisplayName("should auto-generate messageId when not specified")
        void shouldAutoGenerateMessageId() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("test")
                    .payload(new byte[]{1, 2, 3})
                    .build();

            assertThat(message.getMessageId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should auto-generate timestamp when not specified")
        void shouldAutoGenerateTimestamp() {
            Instant before = Instant.now();
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("test")
                    .payload(new byte[0])
                    .build();
            Instant after = Instant.now();

            assertThat(message.getTimestamp()).isBetween(before, after);
        }

        @Test
        @DisplayName("should default to empty headers when not specified")
        void shouldDefaultToEmptyHeaders() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("test")
                    .payload(new byte[0])
                    .build();

            assertThat(message.getHeaders()).isNotNull();
            assertThat(message.getHeaders().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should throw when channel is null")
        void shouldThrowWhenChannelNull() {
            assertThatThrownBy(() -> Message.<byte[]>builder().build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("channel");
        }
    }

    @Nested
    @DisplayName("ofBytes factory")
    class OfBytesTests {

        @Test
        @DisplayName("should create byte message with correct content type")
        void shouldCreateByteMessage() {
            byte[] data = {10, 20, 30};
            Message<byte[]> message = Message.ofBytes("raw-channel", data);

            assertThat(message.getChannel()).isEqualTo("raw-channel");
            assertThat(message.getPayload()).isEqualTo(data);
            assertThat(message.getHeaders().contentType()).isEqualTo("application/octet-stream");
        }
    }

    @Nested
    @DisplayName("Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal when messageId matches")
        void shouldEqualByMessageId() {
            Message<byte[]> a = Message.<byte[]>builder()
                    .messageId("same-id")
                    .channel("ch1")
                    .payload(new byte[]{1})
                    .build();

            Message<byte[]> b = Message.<byte[]>builder()
                    .messageId("same-id")
                    .channel("ch2")
                    .payload(new byte[]{2})
                    .build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("should not be equal when messageId differs")
        void shouldNotEqualByDifferentMessageId() {
            Message<byte[]> a = Message.<byte[]>builder()
                    .messageId("id-1")
                    .channel("ch")
                    .build();

            Message<byte[]> b = Message.<byte[]>builder()
                    .messageId("id-2")
                    .channel("ch")
                    .build();

            assertThat(a).isNotEqualTo(b);
        }
    }
}
