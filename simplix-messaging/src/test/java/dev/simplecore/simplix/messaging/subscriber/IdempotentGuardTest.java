package dev.simplecore.simplix.messaging.subscriber;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("IdempotentGuard")
@ExtendWith(MockitoExtension.class)
class IdempotentGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Duration TTL = Duration.ofHours(24);

    @Nested
    @DisplayName("No-op mode (null redisTemplate)")
    class NoOpModeTests {

        @Test
        @DisplayName("should always return true when redisTemplate is null")
        void shouldAlwaysReturnTrueInNoOpMode() {
            IdempotentGuard guard = new IdempotentGuard(null, TTL);

            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isTrue();
            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isTrue();
        }

        @Test
        @DisplayName("should work without group name in no-op mode")
        void shouldWorkWithoutGroupInNoOpMode() {
            IdempotentGuard guard = new IdempotentGuard(null, TTL);
            assertThat(guard.tryAcquire("channel", "", "msg-1")).isTrue();
        }
    }

    @Nested
    @DisplayName("Redis mode")
    class RedisModeTests {

        @Test
        @DisplayName("should return true on first acquisition")
        void shouldReturnTrueOnFirstAcquisition() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(TTL)))
                    .thenReturn(Boolean.TRUE);

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isTrue();
        }

        @Test
        @DisplayName("should return false on duplicate message")
        void shouldReturnFalseOnDuplicate() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(TTL)))
                    .thenReturn(Boolean.FALSE);

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isFalse();
        }

        @Test
        @DisplayName("should include group name in key when group is not empty")
        void shouldIncludeGroupInKey() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("messaging:idempotent:my-group:my-channel:msg-1"),
                    eq("1"), eq(TTL)))
                    .thenReturn(Boolean.TRUE);

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            assertThat(guard.tryAcquire("my-channel", "my-group", "msg-1")).isTrue();
            verify(valueOperations).setIfAbsent(
                    "messaging:idempotent:my-group:my-channel:msg-1", "1", TTL);
        }

        @Test
        @DisplayName("should exclude group name from key when group is empty")
        void shouldExcludeGroupWhenEmpty() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("messaging:idempotent:my-channel:msg-1"),
                    eq("1"), eq(TTL)))
                    .thenReturn(Boolean.TRUE);

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            assertThat(guard.tryAcquire("my-channel", "", "msg-1")).isTrue();
            verify(valueOperations).setIfAbsent(
                    "messaging:idempotent:my-channel:msg-1", "1", TTL);
        }

        @Test
        @DisplayName("should fail open when Redis operation throws exception")
        void shouldFailOpenOnRedisException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(TTL)))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            // Should return true (fail open) to allow processing
            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isTrue();
        }

        @Test
        @DisplayName("should return false when setIfAbsent returns null")
        void shouldReturnFalseWhenNullResult() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(TTL)))
                    .thenReturn(null);

            IdempotentGuard guard = new IdempotentGuard(redisTemplate, TTL);

            assertThat(guard.tryAcquire("channel", "group", "msg-1")).isFalse();
        }
    }
}
