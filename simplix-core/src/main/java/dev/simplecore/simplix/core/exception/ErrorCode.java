package dev.simplecore.simplix.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Centralized error codes for the SimpliX framework
 * Error codes are structured as CATEGORY_SPECIFIC_ERROR
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // General errors (GEN_)
    GEN_INTERNAL_SERVER_ERROR("GEN_INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.GENERAL),
    GEN_BAD_REQUEST("GEN_BAD_REQUEST", "Bad request", HttpStatus.BAD_REQUEST, ErrorCategory.GENERAL),
    GEN_NOT_FOUND("GEN_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND, ErrorCategory.GENERAL),
    GEN_METHOD_NOT_ALLOWED("GEN_METHOD_NOT_ALLOWED", "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED, ErrorCategory.GENERAL),
    GEN_CONFLICT("GEN_CONFLICT", "Conflict", HttpStatus.CONFLICT, ErrorCategory.GENERAL),
    GEN_SERVICE_UNAVAILABLE("GEN_SERVICE_UNAVAILABLE", "Service unavailable", HttpStatus.SERVICE_UNAVAILABLE, ErrorCategory.GENERAL),
    GEN_TIMEOUT("GEN_TIMEOUT", "Request timeout", HttpStatus.REQUEST_TIMEOUT, ErrorCategory.GENERAL),
    
    // Authentication errors (AUTH_) - 401
    AUTH_AUTHENTICATION_REQUIRED("AUTH_AUTHENTICATION_REQUIRED", "Authentication required", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid credentials", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    AUTH_TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "Token expired", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    AUTH_TOKEN_INVALID("AUTH_TOKEN_INVALID", "Invalid token", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    AUTH_SESSION_EXPIRED("AUTH_SESSION_EXPIRED", "Session expired", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    
    // Authorization errors (AUTHZ_) - 403
    AUTHZ_INSUFFICIENT_PERMISSIONS("AUTHZ_INSUFFICIENT_PERMISSIONS", "Insufficient permissions", HttpStatus.FORBIDDEN, ErrorCategory.AUTHORIZATION),
    AUTHZ_ACCESS_DENIED("AUTHZ_ACCESS_DENIED", "Access denied", HttpStatus.FORBIDDEN, ErrorCategory.AUTHORIZATION),
    AUTHZ_RESOURCE_FORBIDDEN("AUTHZ_RESOURCE_FORBIDDEN", "Resource access forbidden", HttpStatus.FORBIDDEN, ErrorCategory.AUTHORIZATION),
    
    // Validation errors (VAL_) - 400
    VAL_VALIDATION_FAILED("VAL_VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    VAL_INVALID_PARAMETER("VAL_INVALID_PARAMETER", "Invalid parameter", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    VAL_MISSING_PARAMETER("VAL_MISSING_PARAMETER", "Missing required parameter", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    VAL_INVALID_FORMAT("VAL_INVALID_FORMAT", "Invalid format", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    VAL_CONSTRAINT_VIOLATION("VAL_CONSTRAINT_VIOLATION", "Constraint violation", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    
    // Search/Query errors (SEARCH_) - 400
    SEARCH_INVALID_PARAMETER("SEARCH_INVALID_PARAMETER", "Invalid search parameter", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    SEARCH_INVALID_SORT_FIELD("SEARCH_INVALID_SORT_FIELD", "Invalid sort field", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    SEARCH_INVALID_FILTER_OPERATOR("SEARCH_INVALID_FILTER_OPERATOR", "Invalid filter operator", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    SEARCH_INVALID_QUERY_SYNTAX("SEARCH_INVALID_QUERY_SYNTAX", "Invalid query syntax", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    
    // Business logic errors (BIZ_) - 422
    BIZ_BUSINESS_LOGIC_ERROR("BIZ_BUSINESS_LOGIC_ERROR", "Business logic error", HttpStatus.UNPROCESSABLE_ENTITY, ErrorCategory.BUSINESS),
    BIZ_DUPLICATE_RESOURCE("BIZ_DUPLICATE_RESOURCE", "Duplicate resource", HttpStatus.CONFLICT, ErrorCategory.BUSINESS),
    BIZ_RESOURCE_LOCKED("BIZ_RESOURCE_LOCKED", "Resource is locked", HttpStatus.LOCKED, ErrorCategory.BUSINESS),
    BIZ_INVALID_STATE("BIZ_INVALID_STATE", "Invalid state transition", HttpStatus.UNPROCESSABLE_ENTITY, ErrorCategory.BUSINESS),
    BIZ_QUOTA_EXCEEDED("BIZ_QUOTA_EXCEEDED", "Quota exceeded", HttpStatus.TOO_MANY_REQUESTS, ErrorCategory.BUSINESS),
    
    // Database errors (DB_) - 500
    DB_DATABASE_ERROR("DB_DATABASE_ERROR", "Database error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.DATABASE),
    DB_TRANSACTION_FAILED("DB_TRANSACTION_FAILED", "Transaction failed", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.DATABASE),
    DB_CONNECTION_ERROR("DB_CONNECTION_ERROR", "Database connection error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.DATABASE),
    DB_DEADLOCK_DETECTED("DB_DEADLOCK_DETECTED", "Database deadlock detected", HttpStatus.CONFLICT, ErrorCategory.DATABASE),
    
    // External service errors (EXT_) - 502/503
    EXT_SERVICE_ERROR("EXT_SERVICE_ERROR", "External service error", HttpStatus.BAD_GATEWAY, ErrorCategory.EXTERNAL),
    EXT_SERVICE_UNAVAILABLE("EXT_SERVICE_UNAVAILABLE", "External service unavailable", HttpStatus.SERVICE_UNAVAILABLE, ErrorCategory.EXTERNAL),
    EXT_SERVICE_TIMEOUT("EXT_SERVICE_TIMEOUT", "External service timeout", HttpStatus.GATEWAY_TIMEOUT, ErrorCategory.EXTERNAL),
    
    // Legacy codes for backward compatibility
    @Deprecated
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR, ErrorCategory.GENERAL),
    @Deprecated
    AUTHENTICATION_FAILED("AUTHENTICATION_FAILED", "Authentication failed", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION),
    @Deprecated
    INSUFFICIENT_PERMISSIONS("INSUFFICIENT_PERMISSIONS", "Insufficient permissions", HttpStatus.FORBIDDEN, ErrorCategory.AUTHORIZATION),
    @Deprecated
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND, ErrorCategory.GENERAL),
    @Deprecated
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST, ErrorCategory.VALIDATION),
    @Deprecated
    INVALID_SEARCH_PARAMETER("INVALID_SEARCH_PARAMETER", "Invalid search parameter", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    @Deprecated
    INVALID_SORT_FIELD("INVALID_SORT_FIELD", "Invalid sort field", HttpStatus.BAD_REQUEST, ErrorCategory.SEARCH),
    @Deprecated
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token expired", HttpStatus.UNAUTHORIZED, ErrorCategory.AUTHENTICATION);
    
    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;
    private final ErrorCategory category;
    
    /**
     * Error category enumeration
     */
    public enum ErrorCategory {
        GENERAL,
        AUTHENTICATION,
        AUTHORIZATION,
        VALIDATION,
        SEARCH,
        BUSINESS,
        DATABASE,
        EXTERNAL
    }
    
    /**
     * Get error code by name
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return GEN_INTERNAL_SERVER_ERROR;
    }
    
    /**
     * Check if this is an authentication error (401)
     */
    public boolean isAuthenticationError() {
        return category == ErrorCategory.AUTHENTICATION;
    }
    
    /**
     * Check if this is an authorization error (403)
     */
    public boolean isAuthorizationError() {
        return category == ErrorCategory.AUTHORIZATION;
    }
    
    /**
     * Check if this is a client error (4xx)
     */
    public boolean isClientError() {
        return httpStatus.is4xxClientError();
    }
    
    /**
     * Check if this is a server error (5xx)
     */
    public boolean isServerError() {
        return httpStatus.is5xxServerError();
    }
} 