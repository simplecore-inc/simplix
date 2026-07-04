package dev.simplecore.simplix.messaging.broker.nats;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Consumer;
import io.nats.client.Options;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the severity policy of the SLF4J NATS listeners: self-healing
 * transport blips must log at WARN (not the jnats default of SEVERE/ERROR),
 * recovery events at INFO, and genuine message-flow anomalies at ERROR.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NATS Logging Listeners")
class NatsLoggingListenersTest {

    @Mock
    private Connection connection;

    private ListAppender<ILoggingEvent> errorListenerEvents;
    private ListAppender<ILoggingEvent> connectionListenerEvents;

    private final NatsLoggingErrorListener errorListener = new NatsLoggingErrorListener();
    private final NatsLoggingConnectionListener connectionListener = new NatsLoggingConnectionListener();

    @BeforeEach
    void setUp() {
        Options options = new Options.Builder()
                .server("nats://localhost:4222")
                .connectionName("test-connection")
                .build();
        lenient().when(connection.getOptions()).thenReturn(options);

        errorListenerEvents = attachAppender(NatsLoggingErrorListener.class);
        connectionListenerEvents = attachAppender(NatsLoggingConnectionListener.class);
    }

    @AfterEach
    void tearDown() {
        detachAppender(NatsLoggingErrorListener.class, errorListenerEvents);
        detachAppender(NatsLoggingConnectionListener.class, connectionListenerEvents);
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> loggerClass) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggerClass)).addAppender(appender);
        return appender;
    }

    private void detachAppender(Class<?> loggerClass, ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(loggerClass)).detachAppender(appender);
    }

    private static Level singleLevel(List<ILoggingEvent> events) {
        assertThat(events).hasSize(1);
        return events.get(0).getLevel();
    }

    @Nested
    @DisplayName("NatsLoggingErrorListener")
    class ErrorListenerSeverity {

        @Test
        @DisplayName("transient server -ERR frame (e.g. Stale Connection) logs at WARN")
        void serverErrorLogsWarn() {
            errorListener.errorOccurred(connection, "Stale Connection");

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.WARN);
            assertThat(errorListenerEvents.list.get(0).getFormattedMessage())
                    .contains("test-connection")
                    .contains("Stale Connection");
        }

        @Test
        @DisplayName("security -ERR frames log at ERROR — auth/permission failures are not self-healing")
        void securityErrorLogsError() {
            errorListener.errorOccurred(connection, "Authorization Violation");
            errorListener.errorOccurred(connection, "user authentication expired");
            errorListener.errorOccurred(connection, "Permissions Violation for Subscription to \"pacs.txn.logs\"");

            assertThat(errorListenerEvents.list).hasSize(3);
            assertThat(errorListenerEvents.list)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
        }

        @Test
        @DisplayName("I/O exception during operation logs at WARN")
        void communicationExceptionLogsWarn() {
            errorListener.exceptionOccurred(connection, new IOException("Read channel closed."));

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.WARN);
        }

        @Test
        @DisplayName("socket write timeout logs at WARN")
        void socketWriteTimeoutLogsWarn() {
            errorListener.socketWriteTimeout(connection);

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.WARN);
        }

        @Test
        @DisplayName("heartbeat alarm logs at ERROR")
        void heartbeatAlarmLogsError() {
            errorListener.heartbeatAlarm(connection, null, 10L, 20L);

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.ERROR);
        }

        @Test
        @DisplayName("slow consumer logs at ERROR")
        void slowConsumerLogsError() {
            Consumer consumer = mock(Consumer.class);
            when(consumer.getPendingMessageCount()).thenReturn(100L);
            when(consumer.getDroppedCount()).thenReturn(5L);

            errorListener.slowConsumerDetected(connection, consumer);

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.ERROR);
        }

        @Test
        @DisplayName("discarded message logs at ERROR")
        void messageDiscardedLogsError() {
            errorListener.messageDiscarded(connection, null);

            assertThat(singleLevel(errorListenerEvents.list)).isEqualTo(Level.ERROR);
        }

        @Test
        @DisplayName("pull status: warning at WARN, error at ERROR")
        void pullStatusSeverities() {
            errorListener.pullStatusWarning(connection, null, null);
            errorListener.pullStatusError(connection, null, null);

            assertThat(errorListenerEvents.list).hasSize(2);
            assertThat(errorListenerEvents.list.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(errorListenerEvents.list.get(1).getLevel()).isEqualTo(Level.ERROR);
        }

        @Test
        @DisplayName("null connection is handled without throwing")
        void nullConnectionIsSafe() {
            errorListener.errorOccurred(null, "Stale Connection");

            assertThat(errorListenerEvents.list.get(0).getFormattedMessage()).contains("unknown");
        }
    }

    @Nested
    @DisplayName("NatsLoggingConnectionListener")
    class ConnectionListenerSeverity {

        @Test
        @DisplayName("DISCONNECTED and LAME_DUCK log at WARN")
        void disconnectLogsWarn() {
            connectionListener.connectionEvent(connection, ConnectionListener.Events.DISCONNECTED);
            connectionListener.connectionEvent(connection, ConnectionListener.Events.LAME_DUCK);

            assertThat(connectionListenerEvents.list).hasSize(2);
            assertThat(connectionListenerEvents.list)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }

        @Test
        @DisplayName("RECONNECTED and RESUBSCRIBED log at INFO — recovery is visible")
        void recoveryLogsInfo() {
            connectionListener.connectionEvent(connection, ConnectionListener.Events.RECONNECTED);
            connectionListener.connectionEvent(connection, ConnectionListener.Events.RESUBSCRIBED);

            assertThat(connectionListenerEvents.list).hasSize(2);
            assertThat(connectionListenerEvents.list)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
        }

        @Test
        @DisplayName("DISCOVERED_SERVERS logs at DEBUG")
        void discoveryLogsDebug() {
            connectionListener.connectionEvent(connection, ConnectionListener.Events.DISCOVERED_SERVERS);

            // The event only appears when DEBUG is enabled for the logger; either
            // way it must never be logged above DEBUG.
            assertThat(connectionListenerEvents.list)
                    .allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
        }
    }
}
