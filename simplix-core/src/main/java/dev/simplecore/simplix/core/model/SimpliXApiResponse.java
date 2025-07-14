package dev.simplecore.simplix.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpliXApiResponse<T> {

    public enum ResponseType {
        SUCCESS,
        FAILURE,
        ERROR
    }

    private ResponseType type;

    private String message;          // Response message

    private T body;                  // Response data

    private OffsetDateTime timestamp; // Response timestamp (with timezone info)

    // Error-specific fields (only included when type is ERROR)
    private String errorCode;        // Specific error code (renamed from errorType)
    
    private Object errorDetail;      // Detailed error information (renamed from error)

    private String traceId;  // ID for error tracking

    private SimpliXApiResponse(ResponseType type, String message, T body, String errorCode, Object errorDetail) {
        this.type = type;
        this.message = message;
        this.body = body;
        this.timestamp = OffsetDateTime.now(); // Current time with system timezone
        this.errorCode = errorCode;
        this.errorDetail = errorDetail;
        
        // Automatically generate traceId for error responses
        if (type == ResponseType.ERROR && this.traceId == null) {
            this.traceId = generateTraceId();
        }
    }

    /**
     * Generate unique trace ID for error tracking
     * Format: YYYYMMDD-HHMMSS-UUID(8chars)
     * Uses UTC time for consistent logging across timezones
     */
    private static String generateTraceId() {
        String timestamp = Instant.now().atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s-%s", timestamp, uuid);
    }

    // Success responses
    public static <T> SimpliXApiResponse<T> success(T body) {
        return new SimpliXApiResponse<>(ResponseType.SUCCESS, "Success", body, null, null);
    }

    public static <T> SimpliXApiResponse<T> success(T body, String message) {
        return new SimpliXApiResponse<>(ResponseType.SUCCESS, message, body, null, null);
    }

    // Failure responses (business logic failures, not errors)
    public static SimpliXApiResponse<Void> failure(String message) {
        return new SimpliXApiResponse<>(ResponseType.FAILURE, message, null, null, null);
    }

    public static <T> SimpliXApiResponse<T> failure(T data, String message) {
        return new SimpliXApiResponse<>(ResponseType.FAILURE, message, data, null, null);
    }

    // Error responses (system errors, exceptions)
    public static <T> SimpliXApiResponse<T> error(String message) {
        return new SimpliXApiResponse<>(ResponseType.ERROR, message, null, null, null);
    }

    public static <T> SimpliXApiResponse<T> error(String message, String errorCode) {
        return new SimpliXApiResponse<>(ResponseType.ERROR, message, null, errorCode, null);
    }

    public static <T> SimpliXApiResponse<T> error(String message, String errorCode, Object errorDetail) {
        return new SimpliXApiResponse<>(ResponseType.ERROR, message, null, errorCode, errorDetail);
    }

    // // Backward compatibility methods for deprecated fields
    // @Deprecated
    // public String getErrorType() {
    //     return errorCode;
    // }

    // @Deprecated
    // public void setErrorType(String errorType) {
    //     this.errorCode = errorType;
    // }

    // @Deprecated
    // public Object getError() {
    //     return errorDetail;
    // }

    // @Deprecated
    // public void setError(Object error) {
    //     this.errorDetail = error;
    // }
}