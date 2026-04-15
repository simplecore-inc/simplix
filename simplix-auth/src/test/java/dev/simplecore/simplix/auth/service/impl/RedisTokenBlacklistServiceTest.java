package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RedisTokenBlacklistService")
@ExtendWith(MockitoExtension.class)
class RedisTokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SimpliXAuthProperties properties;
    private RedisTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
        service = new RedisTokenBlacklistService(redisTemplate, properties);
    }

    @Test
    @DisplayName("should store JTI in Redis with prefix and TTL")
    void shouldStoreInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Duration ttl = Duration.ofMinutes(30);

        service.blacklist("jti-123", ttl);

        verify(valueOperations).set("simplix:token:bl:jti-123", "1", ttl);
    }

    @Test
    @DisplayName("should check Redis for blacklisted JTI")
    void shouldCheckRedisForBlacklistedJti() {
        when(redisTemplate.hasKey("simplix:token:bl:jti-123")).thenReturn(true);

        assertThat(service.isBlacklisted("jti-123")).isTrue();
    }

    @Test
    @DisplayName("should return false when JTI not in Redis")
    void shouldReturnFalseWhenNotInRedis() {
        when(redisTemplate.hasKey("simplix:token:bl:unknown")).thenReturn(false);

        assertThat(service.isBlacklisted("unknown")).isFalse();
    }

    @Test
    @DisplayName("should use correct key prefix")
    void shouldUseCorrectKeyPrefix() {
        when(redisTemplate.hasKey("simplix:token:bl:test-jti")).thenReturn(true);

        service.isBlacklisted("test-jti");

        verify(redisTemplate).hasKey("simplix:token:bl:test-jti");
    }

    @Test
    @DisplayName("should throw RedisConnectionFailureException when Redis fails in FAIL_CLOSED mode")
    void shouldThrowWhenRedisFailsInFailClosedMode() {
        properties.getToken().setBlacklistFailureMode(SimpliXAuthProperties.BlacklistFailureMode.FAIL_CLOSED);
        when(redisTemplate.hasKey("simplix:token:bl:jti-fail"))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> service.isBlacklisted("jti-fail"))
            .isInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    @DisplayName("should return false (allow) when Redis fails in FAIL_OPEN mode")
    void shouldAllowWhenRedisFailsInFailOpenMode() {
        properties.getToken().setBlacklistFailureMode(SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN);
        when(redisTemplate.hasKey("simplix:token:bl:jti-fail"))
            .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThat(service.isBlacklisted("jti-fail")).isFalse();
    }
}
