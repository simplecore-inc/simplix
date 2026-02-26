package dev.simplecore.simplix.stream.core.subscription;

import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.Subscription;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.exception.SubscriptionLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionManager.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionManager")
class SubscriptionManagerTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private ScheduledExecutorService scheduledExecutor;

    private StreamProperties properties;
    private SessionManager sessionManager;
    private SubscriptionManager subscriptionManager;
    private StreamSession session;

    @BeforeEach
    void setUp() {
        properties = new StreamProperties();
        properties.setSubscription(new StreamProperties.SubscriptionConfig());
        properties.getSubscription().setMaxPerSession(10);
        properties.setScheduler(new StreamProperties.SchedulerConfig());
        properties.getScheduler().setMinInterval(Duration.ofMillis(100));
        properties.getScheduler().setMaxInterval(Duration.ofMinutes(1));
        properties.setSession(new StreamProperties.SessionConfig());

        sessionManager = new SessionManager(sessionRegistry, properties, scheduledExecutor);
        subscriptionManager = new SubscriptionManager(sessionManager, properties);

        session = StreamSession.create("user123", TransportType.SSE);
    }

    @Nested
    @DisplayName("updateSubscriptions()")
    class UpdateSubscriptionsMethod {

        @Test
        @DisplayName("should add new subscriptions")
        void shouldAddNewSubscriptions() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey key1 = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            SubscriptionKey key2 = SubscriptionKey.of("forex", Map.of("pair", "EUR/USD"));
            Subscription sub1 = Subscription.of(key1, Duration.ofSeconds(1));
            Subscription sub2 = Subscription.of(key2, Duration.ofSeconds(1));

            SubscriptionManager.SubscriptionUpdateResult result =
                    subscriptionManager.updateSubscriptions(session.getId(), List.of(sub1, sub2));

            assertEquals(2, result.active().size());
            assertTrue(session.hasSubscription(sub1.getKey()));
            assertTrue(session.hasSubscription(sub2.getKey()));
        }

        @Test
        @DisplayName("should remove subscriptions not in requested list")
        void shouldRemoveSubscriptionsNotInRequestedList() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey existingKey = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));
            session.addSubscription(existingKey);

            SubscriptionKey newKey = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Subscription newSub = Subscription.of(newKey, Duration.ofSeconds(1));

            subscriptionManager.updateSubscriptions(session.getId(), List.of(newSub));

            assertFalse(session.hasSubscription(existingKey));
            assertTrue(session.hasSubscription(newSub.getKey()));
        }

        @Test
        @DisplayName("should throw exception when exceeding subscription limit")
        void shouldThrowExceptionWhenExceedingLimit() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));
            properties.getSubscription().setMaxPerSession(2);

            List<Subscription> subscriptions = List.of(
                    Subscription.of(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")), Duration.ofSeconds(1)),
                    Subscription.of(SubscriptionKey.of("stock", Map.of("symbol", "GOOG")), Duration.ofSeconds(1)),
                    Subscription.of(SubscriptionKey.of("stock", Map.of("symbol", "MSFT")), Duration.ofSeconds(1))
            );

            assertThrows(SubscriptionLimitExceededException.class,
                    () -> subscriptionManager.updateSubscriptions(session.getId(), subscriptions));
        }

        @Test
        @DisplayName("should trigger onSubscriptionAdded callback")
        void shouldTriggerOnSubscriptionAddedCallback() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            AtomicReference<SubscriptionKey> addedKey = new AtomicReference<>();
            AtomicReference<String> addedSessionId = new AtomicReference<>();

            subscriptionManager.setOnSubscriptionAdded((key, sid) -> {
                addedKey.set(key);
                addedSessionId.set(sid);
            });

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Subscription sub = Subscription.of(key, Duration.ofSeconds(1));
            subscriptionManager.updateSubscriptions(session.getId(), List.of(sub));

            assertEquals(sub.getKey(), addedKey.get());
            assertEquals(session.getId(), addedSessionId.get());
        }

        @Test
        @DisplayName("should trigger onSubscriptionRemoved callback")
        void shouldTriggerOnSubscriptionRemovedCallback() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey existingKey = SubscriptionKey.of("stock", Map.of("symbol", "GOOG"));
            session.addSubscription(existingKey);

            AtomicReference<SubscriptionKey> removedKey = new AtomicReference<>();

            subscriptionManager.setOnSubscriptionRemoved((key, sid) -> removedKey.set(key));

            subscriptionManager.updateSubscriptions(session.getId(), List.of());

            assertEquals(existingKey, removedKey.get());
        }

        @Test
        @DisplayName("should not trigger callbacks for unchanged subscriptions")
        void shouldNotTriggerCallbacksForUnchangedSubscriptions() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Subscription sub = Subscription.of(key, Duration.ofSeconds(1));
            session.addSubscription(sub.getKey());

            AtomicInteger addedCount = new AtomicInteger(0);
            AtomicInteger removedCount = new AtomicInteger(0);

            subscriptionManager.setOnSubscriptionAdded((k, sid) -> addedCount.incrementAndGet());
            subscriptionManager.setOnSubscriptionRemoved((k, sid) -> removedCount.incrementAndGet());

            subscriptionManager.updateSubscriptions(session.getId(), List.of(sub));

            assertEquals(0, addedCount.get());
            assertEquals(0, removedCount.get());
        }

        @Test
        @DisplayName("should return subscription info with correct interval")
        void shouldReturnSubscriptionInfoWithCorrectInterval() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Subscription sub = Subscription.of(key, Duration.ofSeconds(5));

            SubscriptionManager.SubscriptionUpdateResult result =
                    subscriptionManager.updateSubscriptions(session.getId(), List.of(sub));

            assertEquals(1, result.active().size());
            assertEquals(5000, result.active().get(0).intervalMs());
        }
    }

    @Nested
    @DisplayName("clearSubscriptions()")
    class ClearSubscriptionsMethod {

        @Test
        @DisplayName("should clear all subscriptions for session")
        void shouldClearAllSubscriptionsForSession() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            session.addSubscription(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")));
            session.addSubscription(SubscriptionKey.of("forex", Map.of("pair", "EUR/USD")));

            subscriptionManager.clearSubscriptions(session.getId());

            assertEquals(0, session.getSubscriptionCount());
        }

        @Test
        @DisplayName("should trigger onSubscriptionRemoved for each subscription")
        void shouldTriggerOnSubscriptionRemovedForEach() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            session.addSubscription(SubscriptionKey.of("stock", Map.of("symbol", "AAPL")));
            session.addSubscription(SubscriptionKey.of("forex", Map.of("pair", "EUR/USD")));

            AtomicReference<Integer> removedCount = new AtomicReference<>(0);
            subscriptionManager.setOnSubscriptionRemoved((key, sid) -> removedCount.updateAndGet(v -> v + 1));

            subscriptionManager.clearSubscriptions(session.getId());

            assertEquals(2, removedCount.get());
        }

        @Test
        @DisplayName("should do nothing for non-existing session")
        void shouldDoNothingForNonExistingSession() {
            when(sessionRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> subscriptionManager.clearSubscriptions("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getActualInterval()")
    class GetActualIntervalMethod {

        @Test
        @DisplayName("should return existing interval for known subscription")
        void shouldReturnExistingIntervalForKnownSubscription() {
            when(sessionRegistry.findById(session.getId())).thenReturn(Optional.of(session));

            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            Subscription sub = Subscription.of(key, Duration.ofSeconds(5));
            subscriptionManager.updateSubscriptions(session.getId(), List.of(sub));

            Duration actual = subscriptionManager.getActualInterval(sub.getKey(), Duration.ofSeconds(10));

            assertEquals(Duration.ofSeconds(5), actual);
        }

        @Test
        @DisplayName("should clamp interval to minimum")
        void shouldClampIntervalToMinimum() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            Duration actual = subscriptionManager.getActualInterval(key, Duration.ofMillis(10));

            assertEquals(Duration.ofMillis(100), actual);
        }

        @Test
        @DisplayName("should clamp interval to maximum")
        void shouldClampIntervalToMaximum() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            Duration actual = subscriptionManager.getActualInterval(key, Duration.ofHours(1));

            assertEquals(Duration.ofMinutes(1), actual);
        }

        @Test
        @DisplayName("should return requested interval when within bounds")
        void shouldReturnRequestedIntervalWhenWithinBounds() {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));

            Duration actual = subscriptionManager.getActualInterval(key, Duration.ofSeconds(30));

            assertEquals(Duration.ofSeconds(30), actual);
        }
    }
}
