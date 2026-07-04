package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.support.Status;
import lombok.extern.slf4j.Slf4j;

/**
 * SLF4J {@link ErrorListener} with severity matched to recoverability.
 * <p>
 * The jnats default listener logs every callback at SEVERE, so a routine
 * transient disconnect (stale connection from a missed ping, a dropped socket)
 * lands in the error log even though the client reconnects and resubscribes
 * automatically. This listener keeps ERROR for conditions that mean message
 * loss or a stalled consumer, and logs self-healing transport blips at WARN:
 * <ul>
 *   <li>WARN — transient server {@code -ERR} frames, I/O exceptions, socket
 *       write timeouts, unhandled/warning pull statuses: the connection
 *       recovers via the configured reconnect policy, and a
 *       {@link NatsLoggingConnectionListener} makes the recovery visible.</li>
 *   <li>ERROR — security {@code -ERR} frames (authorization, authentication,
 *       permissions violations — the client abandons reconnecting after
 *       repeated authentication errors, and a subscription permissions
 *       violation kills only that subscription while the connection stays up),
 *       heartbeat alarms, discarded messages, slow consumers, and pull status
 *       errors: these indicate lost or stuck message flow and require
 *       attention regardless of the connection state.</li>
 *   <li>DEBUG — flow-control housekeeping: routine protocol chatter.</li>
 * </ul>
 */
@Slf4j
public class NatsLoggingErrorListener implements ErrorListener {

    @Override
    public void errorOccurred(Connection conn, String error) {
        if (isSecurityError(error)) {
            log.error("NATS security error on connection '{}': {} (not self-healing — check credentials/permissions)",
                    connectionName(conn), error);
        } else {
            log.warn("NATS server error on connection '{}': {} (client reconnects automatically)",
                    connectionName(conn), error);
        }
    }

    /**
     * Security {@code -ERR} frames are not self-healing: jnats abandons the
     * reconnect loop after repeated authentication errors on the same server,
     * and a subscription permissions violation arrives on a live connection,
     * silently terminating only that subscription. The match set mirrors the
     * client's own authentication-error detection plus the per-subscription
     * permissions frame.
     */
    private static boolean isSecurityError(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase();
        return lower.contains("authorization violation")
                || lower.contains("authentication")
                || lower.contains("permissions violation");
    }

    @Override
    public void exceptionOccurred(Connection conn, Exception exp) {
        log.warn("NATS communication exception on connection '{}': {} (client reconnects automatically)",
                connectionName(conn), exp.toString());
    }

    @Override
    public void socketWriteTimeout(Connection conn) {
        log.warn("NATS socket write timeout on connection '{}' (client reconnects automatically)",
                connectionName(conn));
    }

    @Override
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        log.error("NATS slow consumer detected on connection '{}': pending={}, dropped={}",
                connectionName(conn),
                consumer != null ? consumer.getPendingMessageCount() : -1,
                consumer != null ? consumer.getDroppedCount() : -1);
    }

    @Override
    public void messageDiscarded(Connection conn, Message msg) {
        log.error("NATS outgoing message discarded on connection '{}': subject={}",
                connectionName(conn), msg != null ? msg.getSubject() : null);
    }

    @Override
    public void heartbeatAlarm(Connection conn, JetStreamSubscription sub,
                               long lastStreamSequence, long lastConsumerSequence) {
        log.error("NATS JetStream heartbeat alarm on connection '{}': lastStreamSeq={}, lastConsumerSeq={}",
                connectionName(conn), lastStreamSequence, lastConsumerSequence);
    }

    @Override
    public void unhandledStatus(Connection conn, JetStreamSubscription sub, Status status) {
        log.warn("NATS unhandled status on connection '{}': {}", connectionName(conn), status);
    }

    @Override
    public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
        log.warn("NATS pull status warning on connection '{}': {}", connectionName(conn), status);
    }

    @Override
    public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
        log.error("NATS pull status error on connection '{}': {}", connectionName(conn), status);
    }

    @Override
    public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String id,
                                     FlowControlSource source) {
        log.debug("NATS flow control processed on connection '{}': source={}", connectionName(conn), source);
    }

    private String connectionName(Connection conn) {
        if (conn == null || conn.getOptions() == null) {
            return "unknown";
        }
        String name = conn.getOptions().getConnectionName();
        return name != null ? name : "unnamed";
    }
}
