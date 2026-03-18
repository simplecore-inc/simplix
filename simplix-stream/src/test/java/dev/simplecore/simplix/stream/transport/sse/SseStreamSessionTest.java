package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SseStreamSession.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("SseStreamSession")
class SseStreamSessionTest {

    @Mock
    private SseEmitter emitter;

    private StreamSession streamSession;
    private ObjectMapper objectMapper;
    private SseStreamSession sseSession;

    @BeforeEach
    void setUp() {
        streamSession = StreamSession.create("user-1", TransportType.SSE);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sseSession = new SseStreamSession(streamSession, emitter, objectMapper);
    }

    @Nested
    @DisplayName("send()")
    class SendMethod {

        @Test
        @DisplayName("should send message successfully")
        void shouldSendMessageSuccessfully() throws IOException {
            StreamMessage message = StreamMessage.heartbeat();

            boolean result = sseSession.send(message);

            assertThat(result).isTrue();
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("should return false when not active")
        void shouldReturnFalseWhenNotActive() {
            sseSession.close();

            boolean result = sseSession.send(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false and deactivate on IOException")
        void shouldReturnFalseOnIOException() throws IOException {
            doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            boolean result = sseSession.send(StreamMessage.heartbeat());

            assertThat(result).isFalse();
            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should include subscription key in message ID for data messages")
        void shouldIncludeSubscriptionKeyInMessageId() throws IOException {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150));

            sseSession.send(message);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should be active initially")
        void shouldBeActiveInitially() {
            assertThat(sseSession.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after close")
        void shouldBeInactiveAfterClose() {
            sseSession.close();

            assertThat(sseSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseMethod {

        @Test
        @DisplayName("should complete emitter on close")
        void shouldCompleteEmitterOnClose() {
            sseSession.close();

            verify(emitter).complete();
            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            sseSession.close();
            sseSession.close();

            verify(emitter, times(1)).complete();
        }

        @Test
        @DisplayName("should handle exception during emitter complete")
        void shouldHandleExceptionDuringComplete() {
            doThrow(new RuntimeException("already completed")).when(emitter).complete();

            sseSession.close();

            assertThat(sseSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("accessor methods")
    class AccessorMethods {

        @Test
        @DisplayName("getSessionId should return session ID")
        void getSessionIdShouldReturnSessionId() {
            assertThat(sseSession.getSessionId()).isEqualTo(streamSession.getId());
        }

        @Test
        @DisplayName("getUserId should return user ID")
        void getUserIdShouldReturnUserId() {
            assertThat(sseSession.getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("getSession should return underlying session")
        void getSessionShouldReturnUnderlyingSession() {
            assertThat(sseSession.getSession()).isEqualTo(streamSession);
        }

        @Test
        @DisplayName("getEmitter should return underlying emitter")
        void getEmitterShouldReturnUnderlyingEmitter() {
            assertThat(sseSession.getEmitter()).isEqualTo(emitter);
        }
    }

    @Nested
    @DisplayName("emitter callbacks")
    class EmitterCallbacks {

        @Test
        @DisplayName("should register completion callback")
        void shouldRegisterCompletionCallback() {
            verify(emitter).onCompletion(any());
        }

        @Test
        @DisplayName("should register timeout callback")
        void shouldRegisterTimeoutCallback() {
            verify(emitter).onTimeout(any());
        }

        @Test
        @DisplayName("should register error callback")
        void shouldRegisterErrorCallback() {
            verify(emitter).onError(any());
        }

        @Test
        @DisplayName("should deactivate session on completion callback")
        void shouldDeactivateOnCompletion() {
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(captor.capture());

            captor.getValue().run();

            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should deactivate session on timeout callback")
        void shouldDeactivateOnTimeout() {
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(captor.capture());

            captor.getValue().run();

            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should deactivate session on error callback")
        void shouldDeactivateOnError() {
            ArgumentCaptor<java.util.function.Consumer<Throwable>> captor =
                    ArgumentCaptor.forClass(java.util.function.Consumer.class);
            verify(emitter).onError(captor.capture());

            captor.getValue().accept(new RuntimeException("test error"));

            assertThat(sseSession.isActive()).isFalse();
        }
    }
}
