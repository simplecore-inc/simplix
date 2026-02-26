package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;

/**
 * Exception thrown when a stream resource (data collector) is not found.
 */
public class ResourceNotFoundException extends StreamException {

    public ResourceNotFoundException(String resource) {
        super(ErrorCode.GEN_NOT_FOUND,
                "Stream resource not found: " + resource,
                resource);
    }
}
