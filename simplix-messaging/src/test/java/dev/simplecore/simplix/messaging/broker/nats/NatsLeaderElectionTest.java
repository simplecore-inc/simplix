package dev.simplecore.simplix.messaging.broker.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NatsLeaderElectionTest {

    private static final String KEY = "leader.election.test";
    private static final String INSTANCE_ID = "instance-A";
    private static final byte[] INSTANCE_ID_BYTES = INSTANCE_ID.getBytes(StandardCharsets.UTF_8);

    private KeyValue kv;
    private NatsLeaderElection election;

    @BeforeEach
    void setUp() {
        kv = mock(KeyValue.class);
        election = new NatsLeaderElection(kv, KEY, INSTANCE_ID);
    }

    @Test
    @DisplayName("tryAcquireLeadership returns true and isLeader is true when KV.create succeeds")
    void tryAcquireLeadership_succeeds_onFirstCall() throws Exception {
        when(kv.create(eq(KEY), any(byte[].class))).thenReturn(42L);

        boolean result = election.tryAcquireLeadership();

        assertThat(result).isTrue();
        assertThat(election.isLeader()).isTrue();
    }

    @Test
    @DisplayName("tryAcquireLeadership returns false when key is held by another instance")
    void tryAcquireLeadership_returnsFalse_whenAlreadyHeld() throws Exception {
        JetStreamApiException alreadyExists = mock(JetStreamApiException.class);
        when(kv.create(eq(KEY), any(byte[].class))).thenThrow(alreadyExists);

        KeyValueEntry entry = mock(KeyValueEntry.class);
        when(entry.getValue()).thenReturn("instance-B".getBytes(StandardCharsets.UTF_8));
        when(kv.get(KEY)).thenReturn(entry);

        boolean result = election.tryAcquireLeadership();

        assertThat(result).isFalse();
        assertThat(election.isLeader()).isFalse();
    }

    @Test
    @DisplayName("tryAcquireLeadership returns true when this instance already owns the key and updates revision")
    void tryAcquireLeadership_returnsTrue_whenOwnedByThisInstance_andUpdatesRevision() throws Exception {
        JetStreamApiException alreadyExists = mock(JetStreamApiException.class);
        when(kv.create(eq(KEY), any(byte[].class))).thenThrow(alreadyExists);

        KeyValueEntry entry = mock(KeyValueEntry.class);
        when(entry.getValue()).thenReturn(INSTANCE_ID_BYTES);
        when(entry.getRevision()).thenReturn(7L);
        when(kv.get(KEY)).thenReturn(entry);
        when(kv.update(eq(KEY), any(byte[].class), eq(7L))).thenReturn(8L);

        boolean result = election.tryAcquireLeadership();

        assertThat(result).isTrue();
        assertThat(election.isLeader()).isTrue();
        verify(kv).update(eq(KEY), any(byte[].class), eq(7L));
    }

    @Test
    @DisplayName("renew calls KV.update with last known revision and updates to new revision")
    void renew_callsUpdateWithLastRevision() throws Exception {
        // Acquire leadership first (revision 42)
        when(kv.create(eq(KEY), any(byte[].class))).thenReturn(42L);
        election.tryAcquireLeadership();

        when(kv.update(eq(KEY), any(byte[].class), eq(42L))).thenReturn(43L);

        boolean renewed = election.renew();

        assertThat(renewed).isTrue();
        verify(kv).update(eq(KEY), any(byte[].class), eq(42L));
        // After renew, next renew should use 43
        when(kv.update(eq(KEY), any(byte[].class), eq(43L))).thenReturn(44L);
        assertThat(election.renew()).isTrue();
        verify(kv).update(eq(KEY), any(byte[].class), eq(43L));
    }

    @Test
    @DisplayName("renew returns false and drops leadership when update fails")
    void renew_returnsFalseAndDropsLeadership_onUpdateFailure() throws Exception {
        when(kv.create(eq(KEY), any(byte[].class))).thenReturn(42L);
        election.tryAcquireLeadership();

        JetStreamApiException conflict = mock(JetStreamApiException.class);
        when(kv.update(eq(KEY), any(byte[].class), eq(42L))).thenThrow(conflict);

        boolean renewed = election.renew();

        assertThat(renewed).isFalse();
        assertThat(election.isLeader()).isFalse();
    }

    @Test
    @DisplayName("releaseLeadership calls KV.delete and sets isLeader to false")
    void releaseLeadership_callsKvDelete_andSetsLeaderFalse() throws Exception {
        when(kv.create(eq(KEY), any(byte[].class))).thenReturn(42L);
        election.tryAcquireLeadership();
        assertThat(election.isLeader()).isTrue();

        KeyValueEntry ownEntry = mock(KeyValueEntry.class);
        when(ownEntry.getValue()).thenReturn(INSTANCE_ID_BYTES);
        when(kv.get(KEY)).thenReturn(ownEntry);

        election.releaseLeadership();

        verify(kv).delete(KEY);
        assertThat(election.isLeader()).isFalse();
    }

    @Test
    @DisplayName("releaseLeadership does not delete when another instance owns the lock")
    void releaseLeadership_doesNotDelete_whenAnotherInstanceOwnsLock() throws Exception {
        // Acquire first (uses INSTANCE_ID_BYTES = "instance-A")
        when(kv.create(eq(KEY), any(byte[].class))).thenReturn(1L);
        election.tryAcquireLeadership();
        // But then another instance has taken over
        KeyValueEntry otherEntry = mock(KeyValueEntry.class);
        when(otherEntry.getValue()).thenReturn("other-instance".getBytes(StandardCharsets.UTF_8));
        when(otherEntry.getRevision()).thenReturn(99L);
        when(kv.get(KEY)).thenReturn(otherEntry);

        election.releaseLeadership();

        verify(kv, never()).delete(anyString());
        assertThat(election.isLeader()).isFalse();
    }

    @Test
    @DisplayName("isLeader returns false before any acquire attempt")
    void isLeader_returnsFalse_initially() {
        assertThat(election.isLeader()).isFalse();
    }
}
