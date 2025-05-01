package dev.simplecore.simplix.core.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SimpliXApiResponse<T> {

    @Getter
    public enum ResponseType {
        SUCCESS("Success"),
        FAILURE("Failure"),
        ERROR("Error");

        private final String message;

        ResponseType(String message) {
            this.message = message;
        }

    }

    private ResponseType type;

    private String message;          // Response message

    private T body;                  // Response data on success

    private LocalDateTime timestamp; // Response timestamp

    private String errorType;
    
    private Object error;            // Error message or object on failure

    private SimpliXApiResponse(ResponseType type, String message, T body, String errorType, Object error) {
        this.type = type;
        this.message = message != null ? message : type.getMessage();
        this.body = body;
        this.timestamp = LocalDateTime.now();
        this.errorType = errorType;
        this.error = error;
    }


    public static <T> SimpliXApiResponse<T> success(T body) {
        return new SimpliXApiResponse<>(ResponseType.SUCCESS, null, body, null, null);
    }

    public static <T> SimpliXApiResponse<T> success(T body, String message) {
        return new SimpliXApiResponse<>(ResponseType.SUCCESS, message, body, null, null);
    }

    public static <T> SimpliXApiResponse<T> failure(T body, String message) {
        return new SimpliXApiResponse<>(ResponseType.FAILURE, message, body, null, null);
    }


    public static <T> SimpliXApiResponse<T> error(String message, String errorType, Object error) {
        return new SimpliXApiResponse<>(ResponseType.ERROR, message, null, errorType, error);
    }
}