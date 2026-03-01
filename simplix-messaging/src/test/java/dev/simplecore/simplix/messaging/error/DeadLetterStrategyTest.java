package dev.simplecore.simplix.messaging.error;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DeadLetterStrategy")
@ExtendWith(MockitoExtension.class)
class DeadLetterStrategyTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    private DeadLetterStrategy deadLetterStrategy;

    @BeforeEach
    void setUp() {
        deadLetterStrategy = new DeadLetterStrategy(brokerStrategy);
    }

    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("should route message to DLQ channel with .dlq suffix")
        void shouldRouteToDlqChannel() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[]{1, 2, 3})
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("dlq-1", "orders.dlq", Instant.now()));

            deadLetterStrategy.send(message, "Processing failed");

            verify(brokerStrategy).send(eq("orders.dlq"), any(byte[].class), any(MessageHeaders.class));
        }

        @Test
        @DisplayName("should add dead letter headers to the message")
        void shouldAddDeadLetterHeaders() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("events")
                    .payload("test".getBytes())
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("dlq-2", "events.dlq", Instant.now()));

            deadLetterStrategy.send(message, "Deserialization error");

            ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
            verify(brokerStrategy).send(eq("events.dlq"), any(), headersCaptor.capture());

            MessageHeaders dlqHeaders = headersCaptor.getValue();
            assertThat(dlqHeaders.get(MessageHeaders.DEAD_LETTER_REASON))
                    .hasValue("Deserialization error");
            assertThat(dlqHeaders.get(MessageHeaders.ORIGINAL_CHANNEL))
                    .hasValue("events");
            assertThat(dlqHeaders.get(MessageHeaders.RETRY_COUNT))
                    .hasValue("0");
            assertThat(dlqHeaders.get(MessageHeaders.DEAD_LETTERED_AT))
                    .isPresent();
        }

        @Test
        @DisplayName("should preserve existing retry count from original message")
        void shouldPreserveExistingRetryCount() {
            MessageHeaders originalHeaders = MessageHeaders.empty()
                    .with(MessageHeaders.RETRY_COUNT, "3");
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[]{1})
                    .headers(originalHeaders)
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("dlq-3", "orders.dlq", Instant.now()));

            deadLetterStrategy.send(message, "Max retries exceeded");

            ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
            verify(brokerStrategy).send(any(), any(), headersCaptor.capture());

            assertThat(headersCaptor.getValue().get(MessageHeaders.RETRY_COUNT))
                    .hasValue("3");
        }

        @Test
        @DisplayName("should preserve the original payload")
        void shouldPreserveOriginalPayload() {
            byte[] originalPayload = {10, 20, 30, 40};
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(originalPayload)
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("dlq-4", "orders.dlq", Instant.now()));

            deadLetterStrategy.send(message, "Failure");

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(brokerStrategy).send(any(), payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue()).isEqualTo(originalPayload);
        }

        @Test
        @DisplayName("should handle null payload by sending empty bytes")
        void shouldHandleNullPayload() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(null)
                    .build();
            when(brokerStrategy.send(any(), any(), any()))
                    .thenReturn(new PublishResult("dlq-5", "orders.dlq", Instant.now()));

            deadLetterStrategy.send(message, "Null payload");

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(brokerStrategy).send(any(), payloadCaptor.capture(), any());
            assertThat(payloadCaptor.getValue()).isEmpty();
        }

        @Test
        @DisplayName("should return the publish result from broker")
        void shouldReturnPublishResult() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[0])
                    .build();
            PublishResult expectedResult = new PublishResult("dlq-6", "orders.dlq", Instant.now());
            when(brokerStrategy.send(any(), any(), any())).thenReturn(expectedResult);

            PublishResult result = deadLetterStrategy.send(message, "Error");

            assertThat(result).isEqualTo(expectedResult);
        }
    }

    @Nested
    @DisplayName("dlqChannelFor()")
    class DlqChannelForTests {

        @Test
        @DisplayName("should append .dlq suffix to channel name")
        void shouldAppendDlqSuffix() {
            assertThat(DeadLetterStrategy.dlqChannelFor("orders")).isEqualTo("orders.dlq");
            assertThat(DeadLetterStrategy.dlqChannelFor("events.stream")).isEqualTo("events.stream.dlq");
        }
    }
}
