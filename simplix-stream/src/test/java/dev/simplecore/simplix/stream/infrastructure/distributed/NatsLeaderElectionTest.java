package dev.simplecore.simplix.stream.infrastructure.distributed;

import dev.simplecore.simplix.stream.config.StreamProperties;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NatsLeaderElection")
class NatsLeaderElectionTest {

    @Mock
    private Connection connection;

    @Mock
    private KeyValueManagement kvManagement;

    @Mock
    private KeyValue keyValue;

    @Mock
    private KeyValueStatus kvStatus;

    @Mock
    private ScheduledExecutorService executor;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private NatsLeaderElection leaderElection;

    @BeforeEach
    void setUp() throws Exception {
        when(connection.keyValueManagement()).thenReturn(kvManagement);
        when(kvManagement.create(any())).thenReturn(kvStatus);
        when(connection.keyValue(anyString())).thenReturn(keyValue);
        when(executor.scheduleAtFixedRate(any(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> scheduledFuture);

        StreamProperties properties = new StreamProperties();
        leaderElection = new NatsLeaderElection(connection, executor, "instance-A", properties);
    }

    @Nested
    @DisplayName("tryBecomeLeader")
    class TryBecomeLeaderTests {

        @Test
        @DisplayName("returns true when create() succeeds (first-write-wins)")
        void firstWriteWins() throws Exception {
            when(keyValue.create(anyString(), any(byte[].class))).thenReturn(1L);

            boolean acquired = leaderElection.tryBecomeLeader("sub-1", null);

            assertThat(acquired).isTrue();
            assertThat(leaderElection.getLeadershipCount()).isEqualTo(1);
            verify(executor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @DisplayName("returns false when key exists and is owned by another instance")
        void anotherInstanceOwnsKey() throws Exception {
            when(keyValue.create(anyString(), any(byte[].class)))
                    .thenThrow(mock(JetStreamApiException.class));
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-B".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            boolean acquired = leaderElection.tryBecomeLeader("sub-1", null);

            assertThat(acquired).isFalse();
            assertThat(leaderElection.getLeadershipCount()).isZero();
        }
    }

    @Nested
    @DisplayName("isLeader")
    class IsLeaderTests {

        @Test
        @DisplayName("returns true when KV value matches own instance ID")
        void leaderWhenValueMatches() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-A".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            assertThat(leaderElection.isLeader("sub-1")).isTrue();
        }

        @Test
        @DisplayName("returns false when KV value differs")
        void notLeaderWhenValueDiffers() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-B".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            assertThat(leaderElection.isLeader("sub-1")).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseLeadership")
    class ReleaseTests {

        @Test
        @DisplayName("cancels renewal task and deletes KV when own value")
        void releasesOwnLeadership() throws Exception {
            when(keyValue.create(anyString(), any(byte[].class))).thenReturn(1L);
            leaderElection.tryBecomeLeader("sub-1", null);

            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-A".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            leaderElection.releaseLeadership("sub-1");

            verify(scheduledFuture).cancel(false);
            verify(keyValue).delete(anyString());
            assertThat(leaderElection.getLeadershipCount()).isZero();
        }

        @Test
        @DisplayName("releaseAll releases all currently held leaderships")
        void releaseAllClearsAll() throws Exception {
            when(keyValue.create(anyString(), any(byte[].class))).thenReturn(1L);
            leaderElection.tryBecomeLeader("sub-1", null);
            leaderElection.tryBecomeLeader("sub-2", null);

            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-A".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            leaderElection.releaseAll();

            assertThat(leaderElection.getLeadershipCount()).isZero();
        }
    }

    @Nested
    @DisplayName("getLeader")
    class GetLeaderTests {

        @Test
        @DisplayName("returns the current leader string from KV")
        void returnsLeaderFromKv() throws Exception {
            KeyValueEntry entry = mock(KeyValueEntry.class);
            when(entry.getValue()).thenReturn("instance-Z".getBytes(StandardCharsets.UTF_8));
            when(keyValue.get(anyString())).thenReturn(entry);

            assertThat(leaderElection.getLeader("sub-1")).isEqualTo("instance-Z");
        }

        @Test
        @DisplayName("returns null when no entry exists")
        void returnsNullWhenAbsent() throws Exception {
            when(keyValue.get(anyString())).thenReturn(null);
            assertThat(leaderElection.getLeader("sub-1")).isNull();
        }
    }

}
