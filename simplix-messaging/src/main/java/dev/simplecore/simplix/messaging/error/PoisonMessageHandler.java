package dev.simplecore.simplix.messaging.error;

import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Set;

/**
 * Detects and handles poison messages that cannot be processed due to
 * unrecoverable deserialization or type conversion failures.
 *
 * <p>Poison messages are identified by examining the exception type. If the
 * exception indicates a fundamental incompatibility (e.g., corrupt payload,
 * wrong type), the message is routed to the dead letter queue (if available)
 * or acknowledged and discarded to prevent infinite redelivery loops.
 */
@Slf4j
public class PoisonMessageHandler {

    private static final Set<Class<? extends Exception>> POISON_EXCEPTION_TYPES = Set.of(
            java.net.ProtocolException.class,
            IOException.class,
            ClassCastException.class,
            IllegalArgumentException.class
    );

    /**
     * Determine whether the given exception indicates a poison message.
     *
     * <p>A message is considered "poison" if the exception is one of:
     * <ul>
     *   <li>{@link java.net.ProtocolException} - protocol/encoding errors</li>
     *   <li>{@link IOException} - I/O deserialization failures</li>
     *   <li>{@link ClassCastException} - type mismatch during processing</li>
     *   <li>{@link IllegalArgumentException} - invalid payload content</li>
     * </ul>
     *
     * @param exception the exception thrown during message processing
     * @return {@code true} if the message should be treated as poison
     */
    public boolean isPoisonMessage(Exception exception) {
        if (exception == null) {
            return false;
        }
        for (Class<? extends Exception> poisonType : POISON_EXCEPTION_TYPES) {
            if (poisonType.isInstance(exception)) {
                return true;
            }
        }
        // Also check the cause chain (e.g., RuntimeException wrapping an IOException)
        Throwable cause = exception.getCause();
        if (cause instanceof Exception causeException) {
            for (Class<? extends Exception> poisonType : POISON_EXCEPTION_TYPES) {
                if (poisonType.isInstance(causeException)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle a poison message by routing it to the dead letter queue or
     * acknowledging it to prevent redelivery.
     *
     * @param message            the poison message
     * @param exception          the exception that identified it as poison
     * @param deadLetterStrategy the dead letter strategy; may be {@code null} if DLQ is not configured
     * @param acknowledgment     the message acknowledgment handle
     */
    public void handle(Message<byte[]> message,
                       Exception exception,
                       DeadLetterStrategy deadLetterStrategy,
                       MessageAcknowledgment acknowledgment) {
        String channel = message.getChannel();
        String messageId = message.getMessageId();
        String exceptionType = exception.getClass().getSimpleName();

        if (deadLetterStrategy != null) {
            log.warn("Poison message detected on channel='{}' messageId='{}', routing to DLQ. "
                            + "Exception: {} - {}",
                    channel, messageId, exceptionType, exception.getMessage());
            try {
                deadLetterStrategy.send(message, "Poison message: " + exceptionType + " - " + exception.getMessage());
                acknowledgment.ack();
            } catch (Exception dlqException) {
                log.error("Failed to route poison message to DLQ for channel='{}' messageId='{}'. "
                                + "Acknowledging to prevent redelivery loop.",
                        channel, messageId, dlqException);
                acknowledgment.ack();
            }
        } else {
            log.warn("Poison message detected on channel='{}' messageId='{}', no DLQ configured. "
                            + "Acknowledging to prevent redelivery loop. Exception: {} - {}",
                    channel, messageId, exceptionType, exception.getMessage());
            acknowledgment.ack();
        }
    }
}
