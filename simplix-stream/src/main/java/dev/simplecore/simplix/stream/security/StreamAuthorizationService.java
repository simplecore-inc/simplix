package dev.simplecore.simplix.stream.security;

import dev.simplecore.simplix.stream.core.model.SubscriptionKey;
import dev.simplecore.simplix.stream.exception.AuthorizationDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for authorizing stream subscriptions.
 * <p>
 * Manages resource authorizers and provides authorization checks
 * for subscription requests.
 */
@Slf4j
public class StreamAuthorizationService {

    private final Map<String, ResourceAuthorizer> authorizers = new ConcurrentHashMap<>();
    private final boolean enforceAuthorization;

    /**
     * Creates a new authorization service.
     *
     * @param enforceAuthorization whether to enforce authorization (deny if no authorizer found)
     */
    public StreamAuthorizationService(boolean enforceAuthorization) {
        this.enforceAuthorization = enforceAuthorization;
    }

    /**
     * Set authorizers from Spring context.
     *
     * @param authorizerList list of resource authorizers
     */
    @Autowired(required = false)
    public void setAuthorizers(List<ResourceAuthorizer> authorizerList) {
        if (authorizerList != null) {
            for (ResourceAuthorizer authorizer : authorizerList) {
                registerAuthorizer(authorizer);
            }
        }
    }

    /**
     * Register a resource authorizer.
     *
     * @param authorizer the authorizer
     */
    public void registerAuthorizer(ResourceAuthorizer authorizer) {
        authorizers.put(authorizer.getResource(), authorizer);
        log.debug("Registered authorizer for resource: {}", authorizer.getResource());
    }

    /**
     * Check if a user is authorized to subscribe to a resource.
     *
     * @param userId the user ID
     * @param key    the subscription key
     * @return authorization result
     */
    public AuthorizationResult checkAuthorization(String userId, SubscriptionKey key) {
        ResourceAuthorizer authorizer = authorizers.get(key.getResource());

        if (authorizer == null) {
            if (enforceAuthorization) {
                log.debug("No authorizer found for resource {}, denying access", key.getResource());
                return AuthorizationResult.deny("No authorizer configured for resource: " + key.getResource());
            }
            // No authorizer and not enforcing - allow access
            return AuthorizationResult.allow();
        }

        try {
            boolean authorized = authorizer.authorize(userId, key.getParams());

            if (authorized) {
                log.trace("Authorization granted for user {} on resource {}",
                        userId, key.getResource());
                return AuthorizationResult.allow();
            } else {
                String reason = authorizer.getDenialReason(userId, key.getParams());
                log.debug("Authorization denied for user {} on resource {}: {}",
                        userId, key.getResource(), reason);
                return AuthorizationResult.deny(reason);
            }
        } catch (Exception e) {
            log.warn("Authorization check failed for user {} on resource {}: {}",
                    userId, key.getResource(), e.getMessage());
            return AuthorizationResult.deny("Authorization check failed: " + e.getMessage());
        }
    }

    /**
     * Authorize a subscription, throwing if denied.
     *
     * @param userId the user ID
     * @param key    the subscription key
     * @throws AuthorizationDeniedException if authorization is denied
     */
    public void authorize(String userId, SubscriptionKey key) {
        AuthorizationResult result = checkAuthorization(userId, key);

        if (!result.isAllowed()) {
            throw new AuthorizationDeniedException(key.getResource(), result.getReason());
        }
    }

    /**
     * Check if an authorizer is registered for a resource.
     *
     * @param resource the resource name
     * @return true if an authorizer exists
     */
    public boolean hasAuthorizer(String resource) {
        return authorizers.containsKey(resource);
    }

    /**
     * Get the required permission for a resource.
     *
     * @param resource the resource name
     * @return the required permission, or null
     */
    public String getRequiredPermission(String resource) {
        ResourceAuthorizer authorizer = authorizers.get(resource);
        return authorizer != null ? authorizer.getRequiredPermission() : null;
    }

    /**
     * Result of an authorization check.
     */
    public record AuthorizationResult(boolean granted, String reason) {

        public static AuthorizationResult allow() {
            return new AuthorizationResult(true, null);
        }

        public static AuthorizationResult deny(String reason) {
            return new AuthorizationResult(false, reason);
        }

        public boolean isAllowed() {
            return granted;
        }

        public boolean isDenied() {
            return !granted;
        }

        public String getReason() {
            return reason;
        }
    }
}
