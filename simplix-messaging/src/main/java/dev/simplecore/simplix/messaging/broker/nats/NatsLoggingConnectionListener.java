package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import lombok.extern.slf4j.Slf4j;

/**
 * SLF4J {@link ConnectionListener} that makes the NATS connection lifecycle
 * observable — in particular successful recovery.
 * <p>
 * The jnats client fires {@code RECONNECTED}/{@code RESUBSCRIBED} only through
 * a registered listener; without one, a disconnect is logged (by the error
 * listener) but the automatic recovery happens in complete silence, leaving
 * the log trail looking like an unresolved outage. Severity policy:
 * <ul>
 *   <li>INFO — {@code CONNECTED}, {@code RECONNECTED}, {@code RESUBSCRIBED},
 *       {@code CLOSED}: normal lifecycle, recovery confirmations included.</li>
 *   <li>WARN — {@code DISCONNECTED}, {@code LAME_DUCK}: delivery is
 *       interrupted until the reconnect completes.</li>
 *   <li>DEBUG — {@code DISCOVERED_SERVERS}: topology chatter.</li>
 * </ul>
 */
@Slf4j
public class NatsLoggingConnectionListener implements ConnectionListener {

    @Override
    public void connectionEvent(Connection conn, Events type) {
        String name = connectionName(conn);
        String server = conn != null ? conn.getConnectedUrl() : null;
        switch (type) {
            case DISCONNECTED, LAME_DUCK ->
                    log.warn("NATS connection '{}' {}: reconnect policy takes over", name, type);
            case DISCOVERED_SERVERS ->
                    log.debug("NATS connection '{}' discovered servers (server={})", name, server);
            default ->
                    log.info("NATS connection '{}' {} (server={})", name, type, server);
        }
    }

    private String connectionName(Connection conn) {
        if (conn == null || conn.getOptions() == null) {
            return "unknown";
        }
        String name = conn.getOptions().getConnectionName();
        return name != null ? name : "unnamed";
    }
}
