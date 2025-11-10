package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based implementation of TokenBlacklistService.
 * Uses Redis for distributed token blacklist management.
 * Recommended for production environments with multiple servers.
 */
@Service
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@ConditionalOnBean(name = "redisTemplate")
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "simplix:token:bl:";

    public RedisTokenBlacklistService(org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        String key = BLACKLIST_PREFIX + jti;
        return redisTemplate.hasKey(key);
    }
}