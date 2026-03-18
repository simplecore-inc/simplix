package dev.simplecore.simplix.stream.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SimpliXStreamDistributedConfiguration.BroadcastMessageHandler.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BroadcastMessageHandler")
class BroadcastMessageHandlerTest {

    @Mock
    private RedisBroadcaster broadcaster;

    private ObjectMapper objectMapper;
    private SimpliXStreamDistributedConfiguration.BroadcastMessageHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        handler = new SimpliXStreamDistributedConfiguration.BroadcastMessageHandler(broadcaster, objectMapper);
    }

    @Nested
    @DisplayName("handleMessage()")
    class HandleMessage {

        @Test
        @DisplayName("should handle broadcast message")
        void shouldHandleBroadcastMessage() throws Exception {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150));
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    "source-instance", key.toKeyString(), Set.of("session-1"), message);

            String json = objectMapper.writeValueAsString(broadcastMsg);
            String channel = "stream:data:stock:{\"symbol\":\"AAPL\"}";

            handler.handleMessage(json, channel);

            verify(broadcaster).handleBroadcastMessage(any(RedisBroadcaster.BroadcastMessage.class));
        }

        @Test
        @DisplayName("should handle direct message")
        void shouldHandleDirectMessage() throws Exception {
            StreamMessage message = StreamMessage.data(
                    SubscriptionKey.of("stock", Map.of()), Map.of("price", 150));
            RedisBroadcaster.DirectMessage directMsg = new RedisBroadcaster.DirectMessage(
                    "source-instance", "session-1", message);

            String json = objectMapper.writeValueAsString(directMsg);
            String channel = "stream:data:direct:session-1";

            handler.handleMessage(json, channel);

            verify(broadcaster).handleDirectMessage(any(RedisBroadcaster.DirectMessage.class));
        }

        @Test
        @DisplayName("should handle invalid JSON gracefully")
        void shouldHandleInvalidJsonGracefully() {
            handler.handleMessage("invalid-json{{{", "stream:data:some-channel");

            verify(broadcaster, never()).handleBroadcastMessage(any());
            verify(broadcaster, never()).handleDirectMessage(any());
        }

        @Test
        @DisplayName("should handle exception from broadcaster gracefully")
        void shouldHandleExceptionFromBroadcaster() throws Exception {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150));
            RedisBroadcaster.BroadcastMessage broadcastMsg = new RedisBroadcaster.BroadcastMessage(
                    "source-instance", key.toKeyString(), Set.of("session-1"), message);

            String json = objectMapper.writeValueAsString(broadcastMsg);
            doThrow(new RuntimeException("handler error")).when(broadcaster)
                    .handleBroadcastMessage(any());

            // Should not throw
            handler.handleMessage(json, "stream:data:some-channel");
        }
    }
}
