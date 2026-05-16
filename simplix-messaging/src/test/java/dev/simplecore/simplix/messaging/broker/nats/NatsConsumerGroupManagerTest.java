package dev.simplecore.simplix.messaging.broker.nats;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NatsConsumerGroupManagerTest {

    // ---------------------------------------------------------------
    // Test 1 & 2 (verbatim from plan)
    // ---------------------------------------------------------------

    @Test
    void resolveStreamName_appliesPrefix() {
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(
                mock(JetStreamManagement.class), props);
        assertThat(mgr.resolveStreamName("orders")).isEqualTo("simplix-orders");
        assertThat(mgr.resolveSubject("orders")).isEqualTo("simplix.orders");
    }

    @Test
    void ensureStream_callsAddStreamWithExpectedConfig() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureStream("orders");

        ArgumentCaptor<StreamConfiguration> captor =
                ArgumentCaptor.forClass(StreamConfiguration.class);
        verify(jsm).addStream(captor.capture());
        StreamConfiguration sc = captor.getValue();
        assertThat(sc.getName()).isEqualTo("simplix-orders");
        assertThat(sc.getSubjects()).containsExactly("simplix.orders");
    }

    // ---------------------------------------------------------------
    // Test 3: idempotent on already-exists (error code 10058 → updateStream)
    // ---------------------------------------------------------------

    @Test
    void ensureStream_isIdempotent_onAlreadyExists() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        // Stub addStream to throw "stream already exists" (code 10058)
        JetStreamApiException alreadyExists = mock(JetStreamApiException.class);
        when(alreadyExists.getApiErrorCode()).thenReturn(10058);
        doThrow(alreadyExists).when(jsm).addStream(any(StreamConfiguration.class));

        mgr.ensureStream("orders");

        // updateStream must be called with the same config
        ArgumentCaptor<StreamConfiguration> captor =
                ArgumentCaptor.forClass(StreamConfiguration.class);
        verify(jsm).updateStream(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("simplix-orders");
    }

    // ---------------------------------------------------------------
    // Test 4: other JetStreamApiException codes are rethrown
    // ---------------------------------------------------------------

    @Test
    void ensureStream_propagatesOtherJetStreamApiExceptions() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        JetStreamApiException otherError = mock(JetStreamApiException.class);
        when(otherError.getApiErrorCode()).thenReturn(10000); // some other code
        doThrow(otherError).when(jsm).addStream(any(StreamConfiguration.class));

        assertThatThrownBy(() -> mgr.ensureStream("orders"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(JetStreamApiException.class);
    }

    // ---------------------------------------------------------------
    // Test 5: ensureConsumerGroup calls addOrUpdateConsumer correctly
    // ---------------------------------------------------------------

    @Test
    void ensureConsumerGroup_callsAddOrUpdateConsumer() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureConsumerGroup("orders", "payments-group");

        ArgumentCaptor<ConsumerConfiguration> captor =
                ArgumentCaptor.forClass(ConsumerConfiguration.class);
        verify(jsm).addOrUpdateConsumer(eq("simplix-orders"), captor.capture());
        ConsumerConfiguration cc = captor.getValue();
        assertThat(cc.getDurable()).isEqualTo("payments-group");
        assertThat(cc.getAckPolicy()).isEqualTo(AckPolicy.Explicit);
        assertThat(cc.getFilterSubject()).isEqualTo("simplix.orders");
        assertThat(cc.getAckWait()).isNotNull();
        assertThat(cc.getMaxDeliver()).isGreaterThan(0);
    }

    // ---------------------------------------------------------------
    // Test 6: empty groupName → ephemeral consumer (no durable)
    // ---------------------------------------------------------------

    @Test
    void ensureConsumerGroup_emptyGroupName_isEphemeral() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureConsumerGroup("orders", "");

        ArgumentCaptor<ConsumerConfiguration> captor =
                ArgumentCaptor.forClass(ConsumerConfiguration.class);
        verify(jsm).addOrUpdateConsumer(eq("simplix-orders"), captor.capture());
        ConsumerConfiguration cc = captor.getValue();
        // Ephemeral: durable must be null or empty
        assertThat(cc.getDurable()).isNullOrEmpty();
    }

    // ---------------------------------------------------------------
    // Test 7: generateConsumerName returns distinct names per call
    // ---------------------------------------------------------------

    @Test
    void generateConsumerName_returnsDistinctNamesPerInstance() {
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(
                mock(JetStreamManagement.class), props);

        String name1 = mgr.generateConsumerName("node-1");
        String name2 = mgr.generateConsumerName("node-1");

        assertThat(name1).startsWith("node-1-");
        assertThat(name2).startsWith("node-1-");
        assertThat(name1).isNotEqualTo(name2);
        // suffix must be 8 chars
        assertThat(name1.substring("node-1-".length())).hasSize(8);
    }

    // ---------------------------------------------------------------
    // Test 8: getPendingCount returns ConsumerInfo.getNumPending()
    // ---------------------------------------------------------------

    @Test
    void getPendingCount_returnsConsumerInfoNumPending() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        ConsumerInfo info = mock(ConsumerInfo.class);
        when(info.getNumPending()).thenReturn(42L);
        when(jsm.getConsumerInfo("simplix-orders", "my-group")).thenReturn(info);

        long pending = mgr.getPendingCount("orders", "my-group");
        assertThat(pending).isEqualTo(42L);
    }

    // ---------------------------------------------------------------
    // Test 9a: streamExists returns true when stream is found
    // ---------------------------------------------------------------

    @Test
    void streamExists_returnsTrue_whenStreamFound() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        when(jsm.getStreamInfo("simplix-orders")).thenReturn(mock(StreamInfo.class));

        assertThat(mgr.streamExists("orders")).isTrue();
    }

    // ---------------------------------------------------------------
    // Test 9b: streamExists returns false when stream is not found
    // ---------------------------------------------------------------

    @Test
    void streamExists_returnsFalse_whenNotFound() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        when(jsm.getStreamInfo("simplix-orders"))
                .thenThrow(mock(JetStreamApiException.class));

        assertThat(mgr.streamExists("orders")).isFalse();
    }

    // ---------------------------------------------------------------
    // Follow-ups: auto-create / auto-update / per-channel overrides
    // ---------------------------------------------------------------

    @Test
    void ensureStream_skipsAddStream_whenAutoCreateDisabledAndStreamPresent() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        props.getNats().setAutoCreateStreams(false);
        when(jsm.getStreamInfo("simplix-orders")).thenReturn(mock(StreamInfo.class));
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureStream("orders");

        verify(jsm, never()).addStream(any(StreamConfiguration.class));
        verify(jsm, never()).updateStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStream_throws_whenAutoCreateDisabledAndStreamMissing() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        props.getNats().setAutoCreateStreams(false);
        when(jsm.getStreamInfo("simplix-orders"))
                .thenThrow(mock(JetStreamApiException.class));
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        assertThatThrownBy(() -> mgr.ensureStream("orders"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not provisioned")
                .hasMessageContaining("auto-create-streams=false");
        verify(jsm, never()).addStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStream_skipsUpdate_whenAutoUpdateDisabledAndAlreadyExists() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        props.getNats().setAutoUpdateStreams(false);
        JetStreamApiException exists = mock(JetStreamApiException.class);
        when(exists.getApiErrorCode()).thenReturn(10058);
        when(jsm.addStream(any(StreamConfiguration.class))).thenThrow(exists);
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureStream("orders");

        verify(jsm).addStream(any(StreamConfiguration.class));
        verify(jsm, never()).updateStream(any(StreamConfiguration.class));
    }

    @Test
    void ensureStream_usesPerChannelDuplicateWindow_whenSet() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        MessagingProperties.ChannelProperties ch = new MessagingProperties.ChannelProperties();
        ch.setDuplicateWindow(java.time.Duration.ofMinutes(5));
        props.getChannels().put("orders", ch);
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureStream("orders");

        ArgumentCaptor<StreamConfiguration> captor = ArgumentCaptor.forClass(StreamConfiguration.class);
        verify(jsm).addStream(captor.capture());
        assertThat(captor.getValue().getDuplicateWindow())
                .isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    void ensureConsumerGroup_usesGlobalDeliverPolicy() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        props.getNats().setDeliverPolicy("new");
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureConsumerGroup("orders", "g1");

        ArgumentCaptor<ConsumerConfiguration> captor = ArgumentCaptor.forClass(ConsumerConfiguration.class);
        verify(jsm).addOrUpdateConsumer(eq("simplix-orders"), captor.capture());
        assertThat(captor.getValue().getDeliverPolicy())
                .isEqualTo(io.nats.client.api.DeliverPolicy.New);
    }

    @Test
    void ensureConsumerGroup_perChannelDeliverPolicyOverridesGlobal() throws Exception {
        JetStreamManagement jsm = mock(JetStreamManagement.class);
        MessagingProperties props = new MessagingProperties();
        props.getNats().setDeliverPolicy("all");
        MessagingProperties.ChannelProperties ch = new MessagingProperties.ChannelProperties();
        ch.setDeliverPolicy("new");
        props.getChannels().put("orders", ch);
        NatsConsumerGroupManager mgr = new NatsConsumerGroupManager(jsm, props);

        mgr.ensureConsumerGroup("orders", "g1");

        ArgumentCaptor<ConsumerConfiguration> captor = ArgumentCaptor.forClass(ConsumerConfiguration.class);
        verify(jsm).addOrUpdateConsumer(eq("simplix-orders"), captor.capture());
        assertThat(captor.getValue().getDeliverPolicy())
                .isEqualTo(io.nats.client.api.DeliverPolicy.New);
    }
}
