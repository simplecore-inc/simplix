package dev.simplecore.simplix.auth.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine Cache-based implementation of TokenBlacklistService.
 * Uses in-memory cache with automatic eviction.
 * Suitable for single-server deployments or development environments.
 */
@Service
@ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
@ConditionalOnMissingBean(name = "redisTemplate")
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
public class CaffeineTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(CaffeineTokenBlacklistService.class);
    private Cache<String, Boolean> cache;

    @PostConstruct
    public void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(7, TimeUnit.DAYS)  // Maximum retention period
                .maximumSize(100_000)  // Prevent memory overflow
                .recordStats()  // Enable statistics
                .build();

        log.info("Caffeine token blacklist initialized (not suitable for multi-server deployments)");
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        // Note: Caffeine doesn't support per-entry TTL
        // Using global expireAfterWrite instead
        cache.put(jti, Boolean.TRUE);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return cache.getIfPresent(jti) != null;
    }
}