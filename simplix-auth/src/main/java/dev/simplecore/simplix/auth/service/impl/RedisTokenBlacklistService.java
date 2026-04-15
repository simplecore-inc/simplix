package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-based implementation of TokenBlacklistService.
 * Uses Redis for distributed token blacklist management.
 * Recommended for production environments with multiple servers.
 *
 * <p>When Redis is unavailable, behavior depends on {@code blacklistFailureMode}:
 * <ul>
 *   <li>FAIL_CLOSED (default): treats all tokens as blacklisted (denies access)</li>
 *   <li>FAIL_OPEN: allows tokens through with a warning log</li>
 * </ul>
 */
@Service
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@ConditionalOnBean(name = "redisTemplate")
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);

    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final SimpliXAuthProperties properties;
    private static final String BLACKLIST_PREFIX = "simplix:token:bl:";

    public RedisTokenBlacklistService(
            org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate,
            SimpliXAuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        String key = BLACKLIST_PREFIX + jti;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RedisConnectionFailureException e) {
            SimpliXAuthProperties.BlacklistFailureMode failureMode =
                properties.getToken().getBlacklistFailureMode();

            if (failureMode == SimpliXAuthProperties.BlacklistFailureMode.FAIL_OPEN) {
                log.warn("Redis unavailable for blacklist check (FAIL_OPEN mode) — allowing token through. JTI: {}", jti);
                return false;
            } else {
                log.error("Redis unavailable for blacklist check (FAIL_CLOSED mode) — denying token. JTI: {}", jti);
                throw e; // Propagate so caller can classify as BLACKLIST_SERVICE_ERROR
            }
        }
    }
}