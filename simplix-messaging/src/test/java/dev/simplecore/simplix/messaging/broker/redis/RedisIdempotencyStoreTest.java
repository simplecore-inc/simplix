package dev.simplecore.simplix.messaging.broker.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisIdempotencyStoreTest {

    @Test
    void firstAcquireTrue_secondFalse() {
        StringRedisTemplate tpl = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(tpl.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(any(), eq("1"), any())).thenReturn(true).thenReturn(false);

        RedisIdempotencyStore s = new RedisIdempotencyStore(tpl, Duration.ofMinutes(1));
        assertThat(s.tryAcquire("ch", "g", "m1")).isTrue();
        assertThat(s.tryAcquire("ch", "g", "m1")).isFalse();
    }

    @Test
    void redisFailureFailsOpen() {
        StringRedisTemplate tpl = mock(StringRedisTemplate.class);
        when(tpl.opsForValue()).thenThrow(new RuntimeException("connection lost"));
        RedisIdempotencyStore s = new RedisIdempotencyStore(tpl, Duration.ofMinutes(1));
        assertThat(s.tryAcquire("ch", "g", "m1")).isTrue();
    }
}
