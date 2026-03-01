package dev.simplecore.simplix.messaging.core;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DefaultMessagePublisher")
@ExtendWith(MockitoExtension.class)
class DefaultMessagePublisherTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    private DefaultMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DefaultMessagePublisher(brokerStrategy);
    }

    @Nested
    @DisplayName("publish()")
    class PublishTests {

        @Test
        @DisplayName("should delegate byte[] payload to broker strategy")
        void shouldDelegateBytePayload() {
            byte[] payload = {1, 2, 3};
            Message<byte[]> message = Message.ofBytes("test-channel", payload);
            PublishResult expectedResult = new PublishResult("rec-1", "test-channel", Instant.now());
            when(brokerStrategy.send(eq("test-channel"), any(byte[].class), any()))
                    .thenReturn(expectedResult);

            PublishResult result = publisher.publish(message);

            assertThat(result).isEqualTo(expectedResult);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(brokerStrategy).send(eq("test-channel"), payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue()).isEqualTo(payload);
        }

        @Test
        @DisplayName("should delegate String payload as UTF-8 bytes")
        void shouldDelegateStringPayload() {
            Message<String> message = Message.<String>builder()
                    .channel("str-channel")
                    .payload("hello world")
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("rec-2", "str-channel", Instant.now()));

            publisher.publish(message);

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(brokerStrategy).send(eq("str-channel"), payloadCaptor.capture(), any());
            assertThat(new String(payloadCaptor.getValue())).isEqualTo("hello world");
        }

        @Test
        @DisplayName("should enrich headers with messageId and timestamp")
        void shouldEnrichHeaders() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .messageId("msg-123")
                    .channel("ch")
                    .payload(new byte[0])
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("rec-3", "ch", Instant.now()));

            publisher.publish(message);

            ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
            verify(brokerStrategy).send(any(), any(), headersCaptor.capture());
            MessageHeaders enriched = headersCaptor.getValue();
            assertThat(enriched.get(MessageHeaders.MESSAGE_ID)).hasValue("msg-123");
            assertThat(enriched.get(MessageHeaders.TIMESTAMP)).isPresent();
        }

        @Test
        @DisplayName("should throw for unsupported payload type")
        void shouldThrowForUnsupportedPayload() {
            Message<Integer> message = Message.<Integer>builder()
                    .channel("ch")
                    .payload(42)
                    .build();

            assertThatThrownBy(() -> publisher.publish(message))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported payload type");
        }

        @Test
        @DisplayName("should handle null payload")
        void shouldHandleNullPayload() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("ch")
                    .payload(null)
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("rec-4", "ch", Instant.now()));

            publisher.publish(message);

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(brokerStrategy).send(any(), payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("publishAsync()")
    class PublishAsyncTests {

        @Test
        @DisplayName("should return CompletableFuture with result")
        void shouldReturnFuture() throws Exception {
            Message<byte[]> message = Message.ofBytes("ch", new byte[]{1});
            PublishResult expected = new PublishResult("async-1", "ch", Instant.now());
            when(brokerStrategy.send(any(), any(), any())).thenReturn(expected);

            CompletableFuture<PublishResult> future = publisher.publishAsync(message);
            PublishResult result = future.get();

            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("isAvailable()")
    class IsAvailableTests {

        @Test
        @DisplayName("should delegate to broker strategy")
        void shouldDelegateToBroker() {
            when(brokerStrategy.isReady()).thenReturn(true);
            assertThat(publisher.isAvailable()).isTrue();

            when(brokerStrategy.isReady()).thenReturn(false);
            assertThat(publisher.isAvailable()).isFalse();
        }
    }
}
