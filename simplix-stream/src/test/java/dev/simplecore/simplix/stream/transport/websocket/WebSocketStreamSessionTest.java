package dev.simplecore.simplix.stream.transport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebSocketStreamSession.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketStreamSession")
class WebSocketStreamSessionTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StreamSession streamSession;
    private ObjectMapper objectMapper;
    private WebSocketStreamSession wsSession;

    @BeforeEach
    void setUp() {
        streamSession = StreamSession.create("user-1", TransportType.WEBSOCKET);
        objectMapper = new ObjectMapper();
        wsSession = new WebSocketStreamSession(streamSession, messagingTemplate, objectMapper);
    }

    @Nested
    @DisplayName("send()")
    class SendMethod {

        @Test
        @DisplayName("should send message via messaging template")
        void shouldSendMessageViaTemplate() {
            StreamMessage message = StreamMessage.heartbeat();

            boolean result = wsSession.send(message);

            assertThat(result).isTrue();
            String expectedDestination = "/queue/stream/" + streamSession.getId();
            verify(messagingTemplate).convertAndSend(eq(expectedDestination), eq(message));
        }

        @Test
        @DisplayName("should return false when not active")
        void shouldReturnFalseWhenNotActive() {
            wsSession.close();

            boolean result = wsSession.send(StreamMessage.heartbeat());

            assertThat(result).isFalse();
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        @DisplayName("should return false on exception")
        void shouldReturnFalseOnException() {
            doThrow(new RuntimeException("messaging error"))
                    .when(messagingTemplate).convertAndSend(anyString(), any(StreamMessage.class));

            boolean result = wsSession.send(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("sendToUser()")
    class SendToUser {

        @Test
        @DisplayName("should send message to user queue")
        void shouldSendMessageToUserQueue() {
            StreamMessage message = StreamMessage.heartbeat();

            boolean result = wsSession.sendToUser(message);

            assertThat(result).isTrue();
            verify(messagingTemplate).convertAndSendToUser(
                    eq("user-1"), eq("/queue/stream"), eq(message));
        }

        @Test
        @DisplayName("should return false when not active")
        void shouldReturnFalseWhenNotActive() {
            wsSession.close();

            boolean result = wsSession.sendToUser(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when user ID is null")
        void shouldReturnFalseWhenUserIdNull() {
            StreamSession anonymousSession = StreamSession.create(null, TransportType.WEBSOCKET);
            WebSocketStreamSession anonWsSession = new WebSocketStreamSession(
                    anonymousSession, messagingTemplate, objectMapper);

            boolean result = anonWsSession.sendToUser(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false on exception")
        void shouldReturnFalseOnException() {
            doThrow(new RuntimeException("send error"))
                    .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

            boolean result = wsSession.sendToUser(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should be active initially")
        void shouldBeActiveInitially() {
            assertThat(wsSession.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after close")
        void shouldBeInactiveAfterClose() {
            wsSession.close();

            assertThat(wsSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseMethod {

        @Test
        @DisplayName("should deactivate session")
        void shouldDeactivateSession() {
            wsSession.close();

            assertThat(wsSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("activate()")
    class ActivateMethod {

        @Test
        @DisplayName("should reactivate closed session")
        void shouldReactivateClosedSession() {
            wsSession.close();
            assertThat(wsSession.isActive()).isFalse();

            wsSession.activate();

            assertThat(wsSession.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("accessor methods")
    class AccessorMethods {

        @Test
        @DisplayName("getSessionId should return session ID")
        void getSessionIdShouldReturnSessionId() {
            assertThat(wsSession.getSessionId()).isEqualTo(streamSession.getId());
        }

        @Test
        @DisplayName("getUserId should return user ID")
        void getUserIdShouldReturnUserId() {
            assertThat(wsSession.getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("getSession should return underlying session")
        void getSessionShouldReturnUnderlyingSession() {
            assertThat(wsSession.getSession()).isEqualTo(streamSession);
        }
    }
}
