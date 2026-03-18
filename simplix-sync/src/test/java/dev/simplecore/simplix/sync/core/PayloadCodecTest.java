package dev.simplecore.simplix.sync.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PayloadCodec")
class PayloadCodecTest {

    @Nested
    @DisplayName("of factory method")
    class OfFactoryMethodTests {

        @Test
        @DisplayName("should create codec that encodes and decodes correctly")
        void shouldCreateWorkingCodec() throws IOException {
            PayloadCodec<String> codec = PayloadCodec.of(
                    message -> message.getBytes(StandardCharsets.UTF_8),
                    payload -> new String(payload, StandardCharsets.UTF_8)
            );

            byte[] encoded = codec.encode("hello");
            String decoded = codec.decode(encoded);

            assertThat(decoded).isEqualTo("hello");
        }

        @Test
        @DisplayName("should encode empty string to empty bytes")
        void shouldEncodeEmptyString() {
            PayloadCodec<String> codec = PayloadCodec.of(
                    message -> message.getBytes(StandardCharsets.UTF_8),
                    payload -> new String(payload, StandardCharsets.UTF_8)
            );

            byte[] encoded = codec.encode("");
            assertThat(encoded).isEmpty();
        }

        @Test
        @DisplayName("should decode empty bytes to empty string")
        void shouldDecodeEmptyBytes() throws IOException {
            PayloadCodec<String> codec = PayloadCodec.of(
                    message -> message.getBytes(StandardCharsets.UTF_8),
                    payload -> new String(payload, StandardCharsets.UTF_8)
            );

            String decoded = codec.decode(new byte[0]);
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("should work with integer codec")
        void shouldWorkWithIntegerCodec() throws IOException {
            PayloadCodec<Integer> codec = PayloadCodec.of(
                    message -> String.valueOf(message).getBytes(StandardCharsets.UTF_8),
                    payload -> Integer.parseInt(new String(payload, StandardCharsets.UTF_8))
            );

            byte[] encoded = codec.encode(42);
            Integer decoded = codec.decode(encoded);

            assertThat(decoded).isEqualTo(42);
        }

        @Test
        @DisplayName("should propagate IOException from decoder")
        void shouldPropagateIOException() {
            PayloadCodec<String> codec = PayloadCodec.of(
                    message -> message.getBytes(StandardCharsets.UTF_8),
                    payload -> {
                        throw new IOException("Decode failed");
                    }
            );

            assertThatThrownBy(() -> codec.decode(new byte[]{1, 2, 3}))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Decode failed");
        }

        @Test
        @DisplayName("should handle unicode characters")
        void shouldHandleUnicode() throws IOException {
            PayloadCodec<String> codec = PayloadCodec.of(
                    message -> message.getBytes(StandardCharsets.UTF_8),
                    payload -> new String(payload, StandardCharsets.UTF_8)
            );

            byte[] encoded = codec.encode("Hello World");
            String decoded = codec.decode(encoded);

            assertThat(decoded).isEqualTo("Hello World");
        }
    }
}
