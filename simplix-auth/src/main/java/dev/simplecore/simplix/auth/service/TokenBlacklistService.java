package dev.simplecore.simplix.auth.service;

import java.time.Duration;

/**
 * Service interface for managing token blacklist.
 * Used to revoke tokens before their expiration time.
 */
public interface TokenBlacklistService {

    /**
     * Add a token JTI to the blacklist
     *
     * @param jti JWT ID to blacklist
     * @param ttl Time to keep the JTI in blacklist (should match token's remaining lifetime)
     */
    void blacklist(String jti, Duration ttl);

    /**
     * Check if a token JTI is blacklisted
     *
     * @param jti JWT ID to check
     * @return true if blacklisted, false otherwise
     */
    boolean isBlacklisted(String jti);
}