package dev.simplecore.simplix.stream.security;

import java.util.Map;

/**
 * Interface for authorizing access to stream resources.
 * <p>
 * Implementations provide resource-specific authorization logic.
 * Each resource can have its own authorizer registered.
 */
public interface ResourceAuthorizer {

    /**
     * Get the resource name this authorizer handles.
     *
     * @return the resource name
     */
    String getResource();

    /**
     * Get the required permission for this resource.
     * <p>
     * This is used for Spring Security integration (e.g., hasAuthority).
     *
     * @return the required permission, or null if no specific permission required
     */
    default String getRequiredPermission() {
        return null;
    }

    /**
     * Authorize access to the resource for a user.
     *
     * @param userId the user ID
     * @param params the subscription parameters
     * @return true if authorized
     */
    boolean authorize(String userId, Map<String, Object> params);

    /**
     * Get the denial reason when authorization fails.
     *
     * @param userId the user ID
     * @param params the subscription parameters
     * @return the denial reason
     */
    default String getDenialReason(String userId, Map<String, Object> params) {
        return "Access denied to resource: " + getResource();
    }
}
