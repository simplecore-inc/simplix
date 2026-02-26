package dev.simplecore.simplix.stream.infrastructure.local;

import dev.simplecore.simplix.stream.core.broadcast.MessageSender;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LocalBroadcaster.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalBroadcaster")
class LocalBroadcasterTest {

    private LocalBroadcaster broadcaster;

    @Mock
    private MessageSender sender1;

    @Mock
    private MessageSender sender2;

    @BeforeEach
    void setUp() {
        broadcaster = new LocalBroadcaster();
        broadcaster.initialize();
    }

    @Nested
    @DisplayName("registerSender()")
    class RegisterSenderMethod {

        @Test
        @DisplayName("should register sender for session")
        void shouldRegisterSenderForSession() {
            broadcaster.registerSender("session1", sender1);

            assertEquals(1, broadcaster.getSenderCount());
        }

        @Test
        @DisplayName("should replace existing sender")
        void shouldReplaceExistingSender() {
            broadcaster.registerSender("session1", sender1);
            broadcaster.registerSender("session1", sender2);

            assertEquals(1, broadcaster.getSenderCount());
        }
    }

    @Nested
    @DisplayName("unregisterSender()")
    class UnregisterSenderMethod {

        @Test
        @DisplayName("should remove registered sender")
        void shouldRemoveRegisteredSender() {
            broadcaster.registerSender("session1", sender1);

            broadcaster.unregisterSender("session1");

            assertEquals(0, broadcaster.getSenderCount());
        }

        @Test
        @DisplayName("should do nothing for non-existing session")
        void shouldDoNothingForNonExistingSession() {
            assertDoesNotThrow(() -> broadcaster.unregisterSender("nonexistent"));
        }
    }

    @Nested
    @DisplayName("broadcast()")
    class BroadcastMethod {

        @Test
        @DisplayName("should send message to all specified sessions")
        void shouldSendMessageToAllSpecifiedSessions() {
            when(sender1.isActive()).thenReturn(true);
            when(sender1.send(any())).thenReturn(true);
            when(sender2.isActive()).thenReturn(true);
            when(sender2.send(any())).thenReturn(true);

            broadcaster.registerSender("session1", sender1);
            broadcaster.registerSender("session2", sender2);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            broadcaster.broadcast(key, message, Set.of("session1", "session2"));

            verify(sender1).send(message);
            verify(sender2).send(message);
        }

        @Test
        @DisplayName("should skip sessions without registered senders")
        void shouldSkipSessionsWithoutRegisteredSenders() {
            when(sender1.isActive()).thenReturn(true);
            when(sender1.send(any())).thenReturn(true);

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            broadcaster.broadcast(key, message, Set.of("session1", "session2"));

            verify(sender1).send(message);
        }

        @Test
        @DisplayName("should skip inactive senders")
        void shouldSkipInactiveSenders() {
            when(sender1.isActive()).thenReturn(false);

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            broadcaster.broadcast(key, message, Set.of("session1"));

            verify(sender1, never()).send(any());
        }

        @Test
        @DisplayName("should remove sender when inactive")
        void shouldRemoveSenderWhenInactive() {
            when(sender1.isActive()).thenReturn(false);

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            broadcaster.broadcast(key, message, Set.of("session1"));

            assertEquals(0, broadcaster.getSenderCount());
        }
    }

    @Nested
    @DisplayName("sendToSession()")
    class SendToSessionMethod {

        @Test
        @DisplayName("should return true on successful send")
        void shouldReturnTrueOnSuccessfulSend() {
            when(sender1.isActive()).thenReturn(true);
            when(sender1.send(any())).thenReturn(true);

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            boolean result = broadcaster.sendToSession("session1", message);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false when sender not found")
        void shouldReturnFalseWhenSenderNotFound() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            boolean result = broadcaster.sendToSession("session1", message);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false when sender is not active")
        void shouldReturnFalseWhenSenderIsNotActive() {
            when(sender1.isActive()).thenReturn(false);

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            boolean result = broadcaster.sendToSession("session1", message);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false when send throws exception")
        void shouldReturnFalseWhenSendThrowsException() {
            when(sender1.isActive()).thenReturn(true);
            when(sender1.send(any())).thenThrow(new RuntimeException("Send failed"));

            broadcaster.registerSender("session1", sender1);

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150.0));

            boolean result = broadcaster.sendToSession("session1", message);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("lifecycle methods")
    class LifecycleMethods {

        @Test
        @DisplayName("initialize should set available to true")
        void initializeShouldSetAvailableToTrue() {
            LocalBroadcaster newBroadcaster = new LocalBroadcaster();
            assertFalse(newBroadcaster.isAvailable());

            newBroadcaster.initialize();

            assertTrue(newBroadcaster.isAvailable());
        }

        @Test
        @DisplayName("shutdown should set available to false and close senders")
        void shutdownShouldSetAvailableToFalseAndCloseSenders() {
            broadcaster.registerSender("session1", sender1);

            broadcaster.shutdown();

            assertFalse(broadcaster.isAvailable());
            assertEquals(0, broadcaster.getSenderCount());
            verify(sender1).close();
        }

        @Test
        @DisplayName("shutdown should handle close exceptions gracefully")
        void shutdownShouldHandleCloseExceptionsGracefully() {
            doThrow(new RuntimeException("Close failed")).when(sender1).close();

            broadcaster.registerSender("session1", sender1);

            assertDoesNotThrow(() -> broadcaster.shutdown());
        }
    }
}
