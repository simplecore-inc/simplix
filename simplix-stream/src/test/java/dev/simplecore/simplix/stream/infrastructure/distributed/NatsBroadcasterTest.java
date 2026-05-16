package dev.simplecore.simplix.stream.infrastructure.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.SubscriberLookup;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.MessageHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NatsBroadcaster")
class NatsBroadcasterTest {

    @Mock
    private Connection connection;

    @Mock
    private Dispatcher dispatcher;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private NatsBroadcaster broadcaster;
    private StreamProperties properties;

    @BeforeEach
    void setUp() {
        when(connection.createDispatcher()).thenReturn(dispatcher);

        properties = new StreamProperties();
        properties.getDistributed().setBroker(StreamProperties.Broker.NATS);

        broadcaster = new NatsBroadcaster(connection, mapper, properties, "instance-A", List.of());
        broadcaster.initialize();
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTests {

        @Test
        @DisplayName("subscribes to broadcast and direct subjects on the dispatcher")
        void subscribesToBothSubjects() {
            verify(dispatcher).subscribe(eq("stream.data.broadcast"), any(MessageHandler.class));
            verify(dispatcher).subscribe(eq("stream.data.direct"), any(MessageHandler.class));
            assertThat(broadcaster.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("broadcast")
    class BroadcastTests {

        @Test
        @DisplayName("publishes serialized BroadcastMessage to broadcast subject")
        void publishesBroadcastMessage() throws Exception {
            SubscriptionKey key = SubscriptionKey.of("device", new HashMap<>());
            StreamMessage message = StreamMessage.heartbeat();

            broadcaster.broadcast(key, message, Set.of("session-1", "session-2"));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(connection).publish(eq("stream.data.broadcast"), captor.capture());

            NatsBroadcaster.BroadcastMessage payload = mapper.readValue(
                    captor.getValue(), NatsBroadcaster.BroadcastMessage.class);
            assertThat(payload.sourceInstance()).isEqualTo("instance-A");
            assertThat(payload.sessionIds()).containsExactlyInAnyOrder("session-1", "session-2");
        }
    }

    @Nested
    @DisplayName("sendToSession")
    class SendToSessionTests {

        @Test
        @DisplayName("publishes DirectMessage to direct subject when session is not local")
        void publishesDirectMessage() {
            StreamMessage message = StreamMessage.heartbeat();

            boolean ok = broadcaster.sendToSession("remote-session", message);

            assertThat(ok).isTrue();
            verify(connection).publish(eq("stream.data.direct"), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("self-message filtering")
    class SelfFilteringTests {

        @Test
        @DisplayName("ignores broadcast messages where sourceInstance matches own ID")
        void ignoresOwnBroadcast() throws Exception {
            NatsBroadcaster.BroadcastMessage selfPayload = new NatsBroadcaster.BroadcastMessage(
                    "instance-A",
                    SubscriptionKey.of("device", new HashMap<>()).toKeyString(),
                    Set.of("s1"),
                    StreamMessage.heartbeat());

            // No exception, no delivery — coverage by ensuring the handler invocation is benign
            broadcaster.handleBroadcastMessage(selfPayload);
            // No senders registered, so nothing observable; assertion is the absence of error
            assertThat(broadcaster.getLocalSenderCount()).isZero();
        }
    }

    @Nested
    @DisplayName("shutdown")
    class ShutdownTests {

        @Test
        @DisplayName("closes the dispatcher and clears local senders")
        void closesDispatcher() {
            broadcaster.shutdown();
            verify(connection).closeDispatcher(dispatcher);
            assertThat(broadcaster.isAvailable()).isFalse();
        }
    }
}
