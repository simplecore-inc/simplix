package dev.simplecore.simplix.auth.service.impl;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.service.TokenBlacklistService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of TokenBlacklistService.
 * Uses ConcurrentHashMap with scheduled cleanup.
 * Only suitable for development/testing or single-server deployments.
 * Will lose all data on server restart.
 */
@Service
@ConditionalOnMissingBean(TokenBlacklistService.class)
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
@ConditionalOnExpression(
        "'${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('AUTO') "
                + "or '${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('MEMORY') "
                + "or '${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('NATS')")
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTokenBlacklistService.class);
    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();
    private final SimpliXAuthProperties properties;

    public InMemoryTokenBlacklistService(SimpliXAuthProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (properties.getToken().getBlacklistStore() == SimpliXAuthProperties.BlacklistStore.NATS) {
            log.warn("simplix.auth.token.blacklist-store=NATS was requested but no "
                    + "io.nats.client.Connection bean is available — falling back to in-memory blacklist. "
                    + "Data will be lost on restart and will not propagate across instances.");
        } else {
            log.warn("Using in-memory token blacklist - data will be lost on restart");
            log.warn("Not suitable for production or multi-server deployments");
        }
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        Instant expiryTime = Instant.now().plus(ttl);
        blacklist.put(jti, expiryTime);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        Instant expiryTime = blacklist.get(jti);
        if (expiryTime == null) {
            return false;
        }

        // Check if expired
        if (expiryTime.isBefore(Instant.now())) {
            blacklist.remove(jti);  // Clean up immediately
            return false;
        }

        return true;
    }

    /**
     * Scheduled cleanup task to remove expired entries
     * Runs every minute to prevent memory leaks
     */
    @Scheduled(fixedRate = 60000)  // Every 1 minute
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        int removed = 0;

        for (var entry : blacklist.entrySet()) {
            if (entry.getValue().isBefore(now)) {
                blacklist.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("Cleaned up {} expired blacklist entries", removed);
        }
    }
}
