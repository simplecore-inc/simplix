package dev.simplecore.simplix.stream.transport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket implementation of MessageSender.
 * <p>
 * Uses STOMP over WebSocket for message delivery via SimpMessagingTemplate.
 */
@Slf4j
public class WebSocketStreamSession implements MessageSender {

    private static final String USER_DESTINATION_PREFIX = "/queue/stream";

    @Getter
    private final StreamSession session;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean active = new AtomicBoolean(true);

    /**
     * Creates a new WebSocket stream session.
     *
     * @param session           the underlying stream session
     * @param messagingTemplate the STOMP messaging template
     * @param objectMapper      the object mapper for JSON serialization
     */
    public WebSocketStreamSession(
            StreamSession session,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.session = session;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(StreamMessage message) {
        if (!active.get()) {
            return false;
        }

        try {
            String destination = USER_DESTINATION_PREFIX + "/" + session.getId();
            messagingTemplate.convertAndSend(destination, message);

            log.trace("Sent WebSocket message to session {}: type={}",
                    session.getId(), message.getType());
            return true;
        } catch (Exception e) {
            log.debug("Failed to send WebSocket message to session {}: {}",
                    session.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Send a message to user's personal queue.
     *
     * @param message the message to send
     * @return true if sent successfully
     */
    public boolean sendToUser(StreamMessage message) {
        if (!active.get() || session.getUserId() == null) {
            return false;
        }

        try {
            messagingTemplate.convertAndSendToUser(
                    session.getUserId(),
                    "/queue/stream",
                    message
            );

            log.trace("Sent WebSocket message to user {}: type={}",
                    session.getUserId(), message.getType());
            return true;
        } catch (Exception e) {
            log.debug("Failed to send WebSocket message to user {}: {}",
                    session.getUserId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    @Override
    public void close() {
        active.set(false);
        log.debug("WebSocket session closed: {}", session.getId());
    }

    /**
     * Mark the session as active (on reconnect).
     */
    public void activate() {
        active.set(true);
    }

    /**
     * Get the session ID.
     *
     * @return the session ID
     */
    public String getSessionId() {
        return session.getId();
    }

    /**
     * Get the user ID.
     *
     * @return the user ID, or null if not authenticated
     */
    public String getUserId() {
        return session.getUserId();
    }
}
