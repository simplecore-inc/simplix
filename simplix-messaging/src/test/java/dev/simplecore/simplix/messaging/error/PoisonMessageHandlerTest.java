package dev.simplecore.simplix.messaging.error;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ProtocolException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("PoisonMessageHandler")
@ExtendWith(MockitoExtension.class)
class PoisonMessageHandlerTest {

    @Mock
    private DeadLetterStrategy deadLetterStrategy;

    @Mock
    private MessageAcknowledgment acknowledgment;

    private PoisonMessageHandler poisonMessageHandler;

    @BeforeEach
    void setUp() {
        poisonMessageHandler = new PoisonMessageHandler();
    }

    @Nested
    @DisplayName("isPoisonMessage()")
    class IsPoisonMessageTests {

        @Test
        @DisplayName("should return true for ProtocolException")
        void shouldDetectProtocolException() {
            assertThat(poisonMessageHandler.isPoisonMessage(new ProtocolException("bad protocol")))
                    .isTrue();
        }

        @Test
        @DisplayName("should return true for IOException")
        void shouldDetectIOException() {
            assertThat(poisonMessageHandler.isPoisonMessage(new IOException("read error")))
                    .isTrue();
        }

        @Test
        @DisplayName("should return true for ClassCastException")
        void shouldDetectClassCastException() {
            assertThat(poisonMessageHandler.isPoisonMessage(new ClassCastException("wrong type")))
                    .isTrue();
        }

        @Test
        @DisplayName("should return true for IllegalArgumentException")
        void shouldDetectIllegalArgumentException() {
            assertThat(poisonMessageHandler.isPoisonMessage(new IllegalArgumentException("bad arg")))
                    .isTrue();
        }

        @Test
        @DisplayName("should return true when cause is a poison exception type")
        void shouldDetectPoisonCause() {
            RuntimeException wrapper = new RuntimeException("wrapper", new IOException("cause"));
            assertThat(poisonMessageHandler.isPoisonMessage(wrapper)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-poison exceptions")
        void shouldReturnFalseForNonPoison() {
            assertThat(poisonMessageHandler.isPoisonMessage(new RuntimeException("transient")))
                    .isFalse();
            assertThat(poisonMessageHandler.isPoisonMessage(new InterruptedException("interrupted")))
                    .isFalse();
            assertThat(poisonMessageHandler.isPoisonMessage(new NullPointerException("npe")))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false for null exception")
        void shouldReturnFalseForNull() {
            assertThat(poisonMessageHandler.isPoisonMessage(null)).isFalse();
        }

        @Test
        @DisplayName("should detect ProtocolException as subclass of IOException")
        void shouldDetectProtocolExceptionAsIoSubclass() {
            // ProtocolException extends IOException, so it matches both
            ProtocolException protocolException = new ProtocolException("protocol error");
            assertThat(poisonMessageHandler.isPoisonMessage(protocolException)).isTrue();
        }
    }

    @Nested
    @DisplayName("handle()")
    class HandleTests {

        @Test
        @DisplayName("should route to DLQ and acknowledge when DLQ is available")
        void shouldRouteToDlqWhenAvailable() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[]{1, 2, 3})
                    .build();
            IOException exception = new IOException("Deserialization failed");

            poisonMessageHandler.handle(message, exception, deadLetterStrategy, acknowledgment);

            verify(deadLetterStrategy).send(any(), anyString());
            verify(acknowledgment).ack();
        }

        @Test
        @DisplayName("should acknowledge without DLQ when DLQ is not configured")
        void shouldAcknowledgeWithoutDlq() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[]{1})
                    .build();
            ClassCastException exception = new ClassCastException("Wrong type");

            poisonMessageHandler.handle(message, exception, null, acknowledgment);

            verify(acknowledgment).ack();
        }

        @Test
        @DisplayName("should acknowledge even when DLQ send fails")
        void shouldAcknowledgeWhenDlqFails() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("orders")
                    .payload(new byte[]{1})
                    .build();
            IOException exception = new IOException("bad data");

            org.mockito.Mockito.doThrow(new RuntimeException("DLQ unavailable"))
                    .when(deadLetterStrategy).send(any(), anyString());

            poisonMessageHandler.handle(message, exception, deadLetterStrategy, acknowledgment);

            verify(acknowledgment).ack();
        }

        @Test
        @DisplayName("should include exception details in DLQ reason")
        void shouldIncludeExceptionDetailsInReason() {
            Message<byte[]> message = Message.<byte[]>builder()
                    .channel("events")
                    .payload(new byte[0])
                    .build();
            IllegalArgumentException exception = new IllegalArgumentException("Invalid payload format");

            poisonMessageHandler.handle(message, exception, deadLetterStrategy, acknowledgment);

            org.mockito.ArgumentCaptor<String> reasonCaptor =
                    org.mockito.ArgumentCaptor.forClass(String.class);
            verify(deadLetterStrategy).send(any(), reasonCaptor.capture());

            String reason = reasonCaptor.getValue();
            assertThat(reason).contains("IllegalArgumentException");
            assertThat(reason).contains("Invalid payload format");
        }
    }
}
