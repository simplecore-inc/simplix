package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

import java.util.Map;

/**
 * Exception thrown when access to a stream resource is denied.
 */
public class AuthorizationDeniedException extends StreamException {

    public AuthorizationDeniedException(String resource, String reason) {
        super(ErrorCode.AUTHZ_ACCESS_DENIED,
                "Access denied to resource: " + resource + " - " + reason,
                new DenialDetail(resource, reason, null));
    }

    public AuthorizationDeniedException(String resource, String reason, Map<String, Object> params) {
        super(ErrorCode.AUTHZ_ACCESS_DENIED,
                "Access denied to resource: " + resource + " - " + reason,
                new DenialDetail(resource, reason, params));
    }

    public record DenialDetail(String resource, String reason, Map<String, Object> params) {
    }
}
