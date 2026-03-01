package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.PublishResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RequestReplyTemplate")
@ExtendWith(MockitoExtension.class)
class RequestReplyTemplateTest {

    @Mock
    private BrokerStrategy brokerStrategy;

    @Mock
    private Subscription replySubscription;

    private RequestReplyTemplate template;

    @BeforeEach
    void setUp() {
        lenient().when(replySubscription.isActive()).thenReturn(true);
        lenient().when(brokerStrategy.subscribe(any(SubscribeRequest.class))).thenReturn(replySubscription);
        lenient().when(brokerStrategy.send(anyString(), any(byte[].class), any(MessageHeaders.class)))
                .thenReturn(new PublishResult("rec-1", "test", Instant.now()));

        template = new RequestReplyTemplate(brokerStrategy, "reply-channel", "reply-group");
    }

    @AfterEach
    void tearDown() {
        template.shutdown();
    }

    @Test
    @DisplayName("should enrich request with correlation ID and reply channel")
    void shouldEnrichRequest() {
        Message<byte[]> request = Message.ofBytes("request-channel", new byte[]{1, 2, 3});

        template.sendAndReceive(request, Duration.ofSeconds(5));

        ArgumentCaptor<MessageHeaders> headersCaptor = ArgumentCaptor.forClass(MessageHeaders.class);
        verify(brokerStrategy).send(eq("request-channel"), any(byte[].class), headersCaptor.capture());

        MessageHeaders headers = headersCaptor.getValue();
        assertThat(headers.correlationId()).isNotNull().isNotEmpty();
        assertThat(headers.replyChannel()).isEqualTo("reply-channel");
    }

    @Test
    @DisplayName("should create reply subscription")
    void shouldCreateReplySubscription() {
        Message<byte[]> request = Message.ofBytes("request-channel", new byte[]{1});

        template.sendAndReceive(request, Duration.ofSeconds(5));

        verify(brokerStrategy).ensureConsumerGroup("reply-channel", "reply-group");
        verify(brokerStrategy).subscribe(any(SubscribeRequest.class));
    }

    @Test
    @DisplayName("should timeout when no reply received")
    void shouldTimeoutWhenNoReply() {
        Message<byte[]> request = Message.ofBytes("request-channel", new byte[]{1});

        CompletableFuture<Message<byte[]>> future = template.sendAndReceive(request, Duration.ofMillis(100));

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(TimeoutException.class);
    }
}
