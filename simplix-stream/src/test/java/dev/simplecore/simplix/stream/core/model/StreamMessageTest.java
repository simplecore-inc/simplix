package dev.simplecore.simplix.stream.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StreamMessage.
 */
@DisplayName("StreamMessage")
class StreamMessageTest {

    @Nested
    @DisplayName("MessageType")
    class MessageTypeEnum {

        @Test
        @DisplayName("should have all expected message types")
        void shouldHaveAllExpectedMessageTypes() {
            StreamMessage.MessageType[] values = StreamMessage.MessageType.values();

            assertThat(values).hasSize(7);
            assertThat(values).containsExactly(
                    StreamMessage.MessageType.DATA,
                    StreamMessage.MessageType.HEARTBEAT,
                    StreamMessage.MessageType.ERROR,
                    StreamMessage.MessageType.SUBSCRIPTION_REMOVED,
                    StreamMessage.MessageType.CONNECTED,
                    StreamMessage.MessageType.RECONNECTED,
                    StreamMessage.MessageType.SESSION_TERMINATED
            );
        }
    }

    @Nested
    @DisplayName("data()")
    class DataMethod {

        @Test
        @DisplayName("should create data message with key and payload")
        void shouldCreateDataMessageWithKeyAndPayload() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Object payload = Map.of("price", 150.0);

            StreamMessage message = StreamMessage.data(key, payload);

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.DATA);
            assertThat(message.getSubscriptionKey()).isEqualTo(key.toKeyString());
            assertThat(message.getResource()).isEqualTo("stock");
            assertThat(message.getPayload()).isEqualTo(payload);
            assertThat(message.getTimestamp()).isNotNull();
            assertThat(message.getErrorCode()).isNull();
            assertThat(message.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("heartbeat()")
    class HeartbeatMethod {

        @Test
        @DisplayName("should create heartbeat message")
        void shouldCreateHeartbeatMessage() {
            StreamMessage message = StreamMessage.heartbeat();

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.HEARTBEAT);
            assertThat(message.getTimestamp()).isNotNull();
            assertThat(message.getSubscriptionKey()).isNull();
            assertThat(message.getResource()).isNull();
            assertThat(message.getPayload()).isNull();
        }
    }

    @Nested
    @DisplayName("error()")
    class ErrorMethod {

        @Test
        @DisplayName("should create error message with key")
        void shouldCreateErrorMessageWithKey() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            StreamMessage message = StreamMessage.error(key, "COLLECTOR_ERROR", "Data collection failed");

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.ERROR);
            assertThat(message.getSubscriptionKey()).isEqualTo(key.toKeyString());
            assertThat(message.getResource()).isEqualTo("stock");
            assertThat(message.getErrorCode()).isEqualTo("COLLECTOR_ERROR");
            assertThat(message.getMessage()).isEqualTo("Data collection failed");
            assertThat(message.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should handle null key gracefully")
        void shouldHandleNullKeyGracefully() {
            StreamMessage message = StreamMessage.error(null, "AUTH_ERROR", "Unauthorized");

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.ERROR);
            assertThat(message.getSubscriptionKey()).isNull();
            assertThat(message.getResource()).isNull();
            assertThat(message.getErrorCode()).isEqualTo("AUTH_ERROR");
            assertThat(message.getMessage()).isEqualTo("Unauthorized");
        }
    }

    @Nested
    @DisplayName("subscriptionRemoved()")
    class SubscriptionRemovedMethod {

        @Test
        @DisplayName("should create subscription removed message")
        void shouldCreateSubscriptionRemovedMessage() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));

            StreamMessage message = StreamMessage.subscriptionRemoved(key, "Session terminated");

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.SUBSCRIPTION_REMOVED);
            assertThat(message.getSubscriptionKey()).isEqualTo(key.toKeyString());
            assertThat(message.getResource()).isEqualTo("stock");
            assertThat(message.getMessage()).isEqualTo("Session terminated");
            assertThat(message.getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("connected()")
    class ConnectedMethod {

        @Test
        @DisplayName("should create connected message with session ID")
        void shouldCreateConnectedMessageWithSessionId() {
            StreamMessage message = StreamMessage.connected("session-123");

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.CONNECTED);
            assertThat(message.getTimestamp()).isNotNull();
            assertThat(message.getPayload()).isInstanceOf(StreamMessage.ConnectedPayload.class);

            StreamMessage.ConnectedPayload payload = (StreamMessage.ConnectedPayload) message.getPayload();
            assertThat(payload.sessionId()).isEqualTo("session-123");
            assertThat(payload.serverTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("reconnected()")
    class ReconnectedMethod {

        @Test
        @DisplayName("should create reconnected message with session ID and restored keys")
        void shouldCreateReconnectedMessage() {
            List<String> restoredKeys = List.of("stock:abc123", "forex:def456");

            StreamMessage message = StreamMessage.reconnected("session-456", restoredKeys);

            assertThat(message.getType()).isEqualTo(StreamMessage.MessageType.RECONNECTED);
            assertThat(message.getTimestamp()).isNotNull();
            assertThat(message.getPayload()).isInstanceOf(StreamMessage.ReconnectedPayload.class);

            StreamMessage.ReconnectedPayload payload = (StreamMessage.ReconnectedPayload) message.getPayload();
            assertThat(payload.sessionId()).isEqualTo("session-456");
            assertThat(payload.restoredSubscriptions()).containsExactly("stock:abc123", "forex:def456");
            assertThat(payload.serverTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("ConnectedPayload record")
    class ConnectedPayloadRecord {

        @Test
        @DisplayName("should store session ID and server time")
        void shouldStoreSessionIdAndServerTime() {
            Instant now = Instant.now();
            StreamMessage.ConnectedPayload payload = new StreamMessage.ConnectedPayload("sess-1", now);

            assertThat(payload.sessionId()).isEqualTo("sess-1");
            assertThat(payload.serverTime()).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("ReconnectedPayload record")
    class ReconnectedPayloadRecord {

        @Test
        @DisplayName("should store session ID, subscriptions, and server time")
        void shouldStoreAllFields() {
            Instant now = Instant.now();
            List<String> subscriptions = List.of("key1", "key2");
            StreamMessage.ReconnectedPayload payload = new StreamMessage.ReconnectedPayload("sess-2", subscriptions, now);

            assertThat(payload.sessionId()).isEqualTo("sess-2");
            assertThat(payload.restoredSubscriptions()).containsExactly("key1", "key2");
            assertThat(payload.serverTime()).isEqualTo(now);
        }
    }
}
