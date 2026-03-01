package dev.simplecore.simplix.messaging.subscriber;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a message handler for a specific channel.
 *
 * <p>The annotated method's first parameter must be {@code Message<T>} where {@code T}
 * determines the deserialization strategy. An optional second parameter of type
 * {@link dev.simplecore.simplix.messaging.core.MessageAcknowledgment} receives the
 * acknowledgment handle.
 *
 * <p>Usage example:
 * <pre>{@code
 * @MessageHandler(channel = "order-events", group = "order-service")
 * public void handleOrder(Message<OrderProto> message) {
 *     // process order
 * }
 *
 * @MessageHandler(channel = "${app.sync.channel}", idempotent = true, autoAck = false)
 * public void handleSync(Message<SyncCommand> message, MessageAcknowledgment ack) {
 *     // process and manually ack
 *     ack.ack();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageHandler {

    /**
     * Channel/stream/topic name. Supports {@code ${property}} placeholders.
     */
    String channel();

    /**
     * Consumer group name.
     */
    String group() default "";

    /**
     * Concurrent consumer count.
     */
    int concurrency() default 1;

    /**
     * Auto-ACK on successful handler return.
     */
    boolean autoAck() default true;

    /**
     * Enable idempotent guard (messageId-based deduplication).
     */
    boolean idempotent() default false;
}
