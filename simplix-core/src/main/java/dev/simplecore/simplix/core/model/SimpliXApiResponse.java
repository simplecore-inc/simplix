package dev.simplecore.simplix.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SimpliXApiResponse<T> {

    public enum ResponseType {
        SUCCESS,
        FAILURE,
        ERROR
    }

    private String type;

    private String message;          // Response message

    private T body;                  // Response data

    private OffsetDateTime timestamp; // Response timestamp (with timezone info)

    // Error-specific fields (only included when type is ERROR)
    private String errorCode;        // Specific error code (renamed from errorType)

    private Object errorDetail;      // Detailed error information (renamed from error)

    private SimpliXApiResponse(ResponseType response, String message, T body, String errorCode, Object errorDetail) {
        this.type = response.name();
        this.body = body;
        this.timestamp = OffsetDateTime.now(); // Current time with system timezone
        if (response != ResponseType.SUCCESS) {
            this.message = message;
            this.errorCode = errorCode;
            this.errorDetail = errorDetail;
        }
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
}