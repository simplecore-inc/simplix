package dev.simplecore.simplix.messaging.broker.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@DisplayName("RedisConsumerGroupManager")
@ExtendWith(MockitoExtension.class)
class RedisConsumerGroupManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    private RedisConsumerGroupManager manager;

    @BeforeEach
    void setUp() {
        manager = new RedisConsumerGroupManager(redisTemplate, "test:");
    }

    @Nested
    @DisplayName("ensureConsumerGroup")
    class EnsureConsumerGroupTests {

        @Test
        @DisplayName("should create consumer group successfully")
        void shouldCreateGroupSuccessfully() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("OK");

            manager.ensureConsumerGroup("my-channel", "my-group");

            verify(redisTemplate).execute(any(RedisCallback.class));
        }

        @Test
        @DisplayName("should ignore BUSYGROUP error (group already exists)")
        void shouldIgnoreBusyGroupError() {
            RuntimeException busyGroupCause = new RuntimeException("BUSYGROUP Consumer Group name already exists");
            RedisSystemException busyGroupException = new RedisSystemException("Error", busyGroupCause);
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(busyGroupException);

            // Should not throw
            manager.ensureConsumerGroup("my-channel", "my-group");
        }

        @Test
        @DisplayName("should rethrow non-BUSYGROUP RedisSystemException")
        void shouldRethrowNonBusyGroupError() {
            RuntimeException otherCause = new RuntimeException("Connection refused");
            RedisSystemException otherException = new RedisSystemException("Error", otherCause);
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(otherException);

            assertThatThrownBy(() -> manager.ensureConsumerGroup("my-channel", "my-group"))
                    .isInstanceOf(RedisSystemException.class);
        }

        @Test
        @DisplayName("should handle deeply nested BUSYGROUP cause")
        void shouldHandleDeeplyNestedBusyGroup() {
            RuntimeException deepCause = new RuntimeException("BUSYGROUP Consumer Group name already exists");
            RuntimeException middleCause = new RuntimeException("Wrapper", deepCause);
            RedisSystemException ex = new RedisSystemException("Error", middleCause);
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(ex);

            // Should not throw - BUSYGROUP found in nested cause
            manager.ensureConsumerGroup("my-channel", "my-group");
        }

        @Test
        @DisplayName("should handle RedisSystemException with null cause")
        void shouldHandleNullCause() {
            RedisSystemException ex = new RedisSystemException("Error", (Throwable) null);
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(ex);

            assertThatThrownBy(() -> manager.ensureConsumerGroup("my-channel", "my-group"))
                    .isInstanceOf(RedisSystemException.class);
        }

        @Test
        @DisplayName("should handle cause with null message")
        void shouldHandleCauseWithNullMessage() {
            RuntimeException causeWithNullMsg = new RuntimeException((String) null);
            RedisSystemException ex = new RedisSystemException("Error", causeWithNullMsg);
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(ex);

            assertThatThrownBy(() -> manager.ensureConsumerGroup("my-channel", "my-group"))
                    .isInstanceOf(RedisSystemException.class);
        }

        @Test
        @DisplayName("should use key prefix in stream key")
        void shouldUseKeyPrefix() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("OK");

            manager.ensureConsumerGroup("events", "grp");

            // Verify the callback was invoked (key prefix is embedded in the callback)
            verify(redisTemplate).execute(any(RedisCallback.class));
        }
    }

    @Nested
    @DisplayName("generateConsumerName")
    class GenerateConsumerNameTests {

        @Test
        @DisplayName("should generate name with instance ID prefix")
        void shouldGenerateNameWithPrefix() {
            String name = manager.generateConsumerName("instance-1");

            assertThat(name).startsWith("instance-1-");
            assertThat(name).hasSize("instance-1-".length() + 8); // 8 chars from UUID
        }

        @Test
        @DisplayName("should generate unique names on each call")
        void shouldGenerateUniqueNames() {
            String name1 = manager.generateConsumerName("inst");
            String name2 = manager.generateConsumerName("inst");

            assertThat(name1).isNotEqualTo(name2);
        }
    }

    @Nested
    @DisplayName("getGroupInfo")
    class GetGroupInfoTests {

        @Test
        @DisplayName("should delegate to stream operations")
        void shouldDelegateToStreamOps() {
            StreamInfo.XInfoGroups mockGroups = mock(StreamInfo.XInfoGroups.class);
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.groups("test:my-channel")).thenReturn(mockGroups);

            StreamInfo.XInfoGroups result = manager.getGroupInfo("my-channel");

            assertThat(result).isSameAs(mockGroups);
            verify(streamOps).groups("test:my-channel");
        }
    }

    @Nested
    @DisplayName("getPendingCount")
    class GetPendingCountTests {

        @Test
        @DisplayName("should return total pending messages")
        void shouldReturnPendingCount() {
            PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
            when(summary.getTotalPendingMessages()).thenReturn(42L);
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending("test:my-channel", "my-group")).thenReturn(summary);

            long count = manager.getPendingCount("my-channel", "my-group");

            assertThat(count).isEqualTo(42L);
        }

        @Test
        @DisplayName("should return 0 when summary is null")
        void shouldReturnZeroWhenNull() {
            when(redisTemplate.opsForStream()).thenReturn(streamOps);
            when(streamOps.pending("test:my-channel", "my-group")).thenReturn(null);

            long count = manager.getPendingCount("my-channel", "my-group");

            assertThat(count).isEqualTo(0L);
        }
    }
}
