package dev.simplecore.simplix.stream.transport.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.simplix.stream.core.enums.TransportType;
import dev.simplecore.simplix.stream.core.model.StreamMessage;
import dev.simplecore.simplix.stream.core.model.StreamSession;
import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SseStreamSession.
 * <p>
 * {@code send()} is a non-blocking enqueue; the actual emitter writes run on
 * the injected executor. Most tests use a same-thread executor so writes
 * happen synchronously inside {@code send()}; queueing/overflow tests use a
 * manual executor to control exactly when the drain runs, and the slow-client
 * tests use a real thread to prove producer isolation from a blocked write.
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
@DisplayName("SseStreamSession")
class SseStreamSessionTest {

    private static final int QUEUE_CAPACITY = 8;

    @Mock
    private SseEmitter emitter;

    private StreamSession streamSession;
    private ObjectMapper objectMapper;
    /** Session backed by a same-thread executor: writes complete inside send(). */
    private SseStreamSession sseSession;

    private ExecutorService realExecutor;

    @BeforeEach
    void setUp() {
        streamSession = StreamSession.create("user-1", TransportType.SSE);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sseSession = newSession(Runnable::run, QUEUE_CAPACITY);
    }

    @AfterEach
    void tearDown() {
        if (realExecutor != null) {
            realExecutor.shutdownNow();
        }
    }

    private SseStreamSession newSession(Executor executor, int capacity) {
        return new SseStreamSession(streamSession, emitter, objectMapper, executor, capacity);
    }

    /** Executor that holds submitted tasks until the test runs them explicitly. */
    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        void runAll() {
            Runnable task;
            while ((task = tasks.pollFirst()) != null) {
                task.run();
            }
        }
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met within 5s");
            }
            Thread.onSpinWait();
        }
    }

    @Nested
    @DisplayName("send()")
    class SendMethod {

        @Test
        @DisplayName("should write the message through the emitter")
        void shouldSendMessageSuccessfully() throws IOException {
            StreamMessage message = StreamMessage.heartbeat();

            boolean result = sseSession.send(message);

            assertThat(result).isTrue();
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("should return false when not active")
        void shouldReturnFalseWhenNotActive() {
            sseSession.close();

            boolean result = sseSession.send(StreamMessage.heartbeat());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should deactivate and complete the emitter when the write fails")
        void shouldDeactivateOnIOException() throws IOException {
            doThrow(new IOException("broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            // The enqueue itself is accepted; the failure surfaces on the write,
            // which deactivates the session and finalizes the emitter so the
            // client sees a closed connection instead of a silent zombie.
            sseSession.send(StreamMessage.heartbeat());

            assertThat(sseSession.isActive()).isFalse();
            verify(emitter).complete();
            assertThat(sseSession.send(StreamMessage.heartbeat())).isFalse();
        }

        @Test
        @DisplayName("should include subscription key in message ID for data messages")
        void shouldIncludeSubscriptionKeyInMessageId() throws IOException {
            SubscriptionKey key = SubscriptionKey.of("stock", Map.of("symbol", "AAPL"));
            StreamMessage message = StreamMessage.data(key, Map.of("price", 150));

            sseSession.send(message);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("should deliver queued messages in FIFO order")
        void shouldDeliverInFifoOrder() throws Exception {
            ManualExecutor executor = new ManualExecutor();
            ObjectMapper spyMapper = spy(objectMapper);
            SseStreamSession session = new SseStreamSession(
                    streamSession, emitter, spyMapper, executor, QUEUE_CAPACITY);

            StreamMessage first = StreamMessage.heartbeat();
            StreamMessage second = StreamMessage.data(
                    SubscriptionKey.of("stock", Map.of()), Map.of("price", 1));

            assertThat(session.send(first)).isTrue();
            assertThat(session.send(second)).isTrue();
            verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));

            executor.runAll();

            verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
            var inOrder = inOrder(spyMapper);
            inOrder.verify(spyMapper).writeValueAsString(first);
            inOrder.verify(spyMapper).writeValueAsString(second);
        }
    }

    @Nested
    @DisplayName("queue overflow")
    class QueueOverflow {

        @Test
        @DisplayName("should close the session when the client falls too far behind")
        void shouldCloseOnOverflow() {
            ManualExecutor executor = new ManualExecutor();
            SseStreamSession session = new SseStreamSession(
                    streamSession, emitter, objectMapper, executor, 2);

            assertThat(session.send(StreamMessage.heartbeat())).isTrue();
            assertThat(session.send(StreamMessage.heartbeat())).isTrue();

            // Third message exceeds the capacity of 2: the client cannot keep
            // up, so the session closes instead of silently dropping updates.
            assertThat(session.send(StreamMessage.heartbeat())).isFalse();
            assertThat(session.isActive()).isFalse();

            // The pending drain observes the cleared queue and completes the
            // emitter, signalling the client to reconnect and re-bootstrap.
            executor.runAll();
            verify(emitter).complete();
            assertThat(session.send(StreamMessage.heartbeat())).isFalse();
        }
    }

    @Nested
    @DisplayName("slow client isolation")
    class SlowClientIsolation {

        @Test
        @DisplayName("a blocked write must never block the producer thread")
        void producerNeverBlocksOnSlowClient() throws Exception {
            realExecutor = Executors.newSingleThreadExecutor();
            CountDownLatch writeBlocked = new CountDownLatch(1);
            CountDownLatch releaseWrite = new CountDownLatch(1);
            doAnswer(invocation -> {
                writeBlocked.countDown();
                releaseWrite.await(5, TimeUnit.SECONDS);
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            SseStreamSession session = new SseStreamSession(
                    streamSession, emitter, objectMapper, realExecutor, QUEUE_CAPACITY);

            // First message: the writer thread picks it up and blocks inside
            // emitter.send() — the simulated stuck TCP write.
            assertThat(session.send(StreamMessage.heartbeat())).isTrue();
            assertThat(writeBlocked.await(5, TimeUnit.SECONDS)).isTrue();

            // While the write is stuck, every producer send must return
            // immediately. Filling the whole queue is bounded to prove no call
            // ever waits on the blocked socket.
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
                for (int i = 0; i < QUEUE_CAPACITY; i++) {
                    assertThat(session.send(StreamMessage.heartbeat())).isTrue();
                }
            });
            assertThat(session.isActive()).isTrue();

            releaseWrite.countDown();
            awaitTrue(() -> {
                try {
                    verify(emitter, times(1 + QUEUE_CAPACITY)).send(any(SseEmitter.SseEventBuilder.class));
                    return true;
                } catch (AssertionError e) {
                    return false;
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
        }

        @Test
        @DisplayName("overflow during a stuck write closes the session once the write resolves")
        void overflowDuringStuckWriteClosesAfterUnblock() throws Exception {
            realExecutor = Executors.newSingleThreadExecutor();
            CountDownLatch writeBlocked = new CountDownLatch(1);
            CountDownLatch releaseWrite = new CountDownLatch(1);
            doAnswer(invocation -> {
                writeBlocked.countDown();
                releaseWrite.await(5, TimeUnit.SECONDS);
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            SseStreamSession session = new SseStreamSession(
                    streamSession, emitter, objectMapper, realExecutor, 2);

            assertThat(session.send(StreamMessage.heartbeat())).isTrue();
            assertThat(writeBlocked.await(5, TimeUnit.SECONDS)).isTrue();

            // Fill the queue behind the stuck write, then overflow it.
            assertThat(session.send(StreamMessage.heartbeat())).isTrue();
            assertThat(session.send(StreamMessage.heartbeat())).isTrue();
            assertThat(session.send(StreamMessage.heartbeat())).isFalse();
            assertThat(session.isActive()).isFalse();

            // Once the stuck write resolves, the writer finalizes the emitter
            // so the client reconnects — the session never lingers as a zombie.
            releaseWrite.countDown();
            awaitTrue(() -> {
                try {
                    verify(emitter).complete();
                    return true;
                } catch (AssertionError e) {
                    return false;
                }
            });
        }
    }

    @Nested
    @DisplayName("serialization sharing")
    class SerializationSharing {

        @Test
        @DisplayName("should serialize a broadcast message once across sessions")
        void shouldSerializeOnceAcrossSessions() throws Exception {
            ObjectMapper spyMapper = spy(objectMapper);
            SseStreamSession first = new SseStreamSession(
                    streamSession, emitter, spyMapper, Runnable::run, QUEUE_CAPACITY);
            SseStreamSession second = new SseStreamSession(
                    StreamSession.create("user-2", TransportType.SSE), emitter, spyMapper,
                    Runnable::run, QUEUE_CAPACITY);

            StreamMessage shared = StreamMessage.data(
                    SubscriptionKey.of("stock", Map.of()), Map.of("price", 1));

            first.send(shared);
            second.send(shared);

            verify(spyMapper, times(1)).writeValueAsString(shared);
            verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("isActive()")
    class IsActive {

        @Test
        @DisplayName("should be active initially")
        void shouldBeActiveInitially() {
            assertThat(sseSession.isActive()).isTrue();
        }

        @Test
        @DisplayName("should be inactive after close")
        void shouldBeInactiveAfterClose() {
            sseSession.close();

            assertThat(sseSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("close()")
    class CloseMethod {

        @Test
        @DisplayName("should complete emitter on close")
        void shouldCompleteEmitterOnClose() {
            sseSession.close();

            verify(emitter).complete();
            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should drain queued messages before completing")
        void shouldDrainBeforeCompleting() throws IOException {
            ManualExecutor executor = new ManualExecutor();
            SseStreamSession session = new SseStreamSession(
                    streamSession, emitter, objectMapper, executor, QUEUE_CAPACITY);

            session.send(StreamMessage.sessionTerminated("bye"));
            session.close();
            executor.runAll();

            var inOrder = inOrder(emitter);
            inOrder.verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            inOrder.verify(emitter).complete();
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            sseSession.close();
            sseSession.close();

            verify(emitter, times(1)).complete();
        }

        @Test
        @DisplayName("should handle exception during emitter complete")
        void shouldHandleExceptionDuringComplete() {
            doThrow(new RuntimeException("already completed")).when(emitter).complete();

            sseSession.close();

            assertThat(sseSession.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("accessor methods")
    class AccessorMethods {

        @Test
        @DisplayName("getSessionId should return session ID")
        void getSessionIdShouldReturnSessionId() {
            assertThat(sseSession.getSessionId()).isEqualTo(streamSession.getId());
        }

        @Test
        @DisplayName("getUserId should return user ID")
        void getUserIdShouldReturnUserId() {
            assertThat(sseSession.getUserId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("getSession should return underlying session")
        void getSessionShouldReturnUnderlyingSession() {
            assertThat(sseSession.getSession()).isEqualTo(streamSession);
        }

        @Test
        @DisplayName("getEmitter should return underlying emitter")
        void getEmitterShouldReturnUnderlyingEmitter() {
            assertThat(sseSession.getEmitter()).isEqualTo(emitter);
        }
    }

    @Nested
    @DisplayName("emitter callbacks")
    class EmitterCallbacks {

        @Test
        @DisplayName("should register completion callback")
        void shouldRegisterCompletionCallback() {
            verify(emitter).onCompletion(any());
        }

        @Test
        @DisplayName("should register timeout callback")
        void shouldRegisterTimeoutCallback() {
            verify(emitter).onTimeout(any());
        }

        @Test
        @DisplayName("should register error callback")
        void shouldRegisterErrorCallback() {
            verify(emitter).onError(any());
        }

        @Test
        @DisplayName("should deactivate session on completion callback")
        void shouldDeactivateOnCompletion() {
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onCompletion(captor.capture());

            captor.getValue().run();

            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should deactivate session on timeout callback")
        void shouldDeactivateOnTimeout() {
            ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            verify(emitter).onTimeout(captor.capture());

            captor.getValue().run();

            assertThat(sseSession.isActive()).isFalse();
        }

        @Test
        @DisplayName("should deactivate session on error callback")
        void shouldDeactivateOnError() {
            ArgumentCaptor<java.util.function.Consumer<Throwable>> captor =
                    ArgumentCaptor.forClass(java.util.function.Consumer.class);
            verify(emitter).onError(captor.capture());

            captor.getValue().accept(new RuntimeException("test error"));

            assertThat(sseSession.isActive()).isFalse();
        }
    }
}
