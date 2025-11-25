package dev.simplecore.simplix.web.advice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a validation field error with field name and error message.
 * This class provides a type-safe alternative to using {@code Map<String, String>} for validation errors.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidationFieldError {

    /**
     * The name of the field that failed validation
     */
    private String field;

    /**
     * The localized error message describing the validation failure
     */
    private String message;

    /**
     * Optional: The rejected value that failed validation
     */
    private Object rejectedValue;

    /**
     * Optional: The validation constraint code (e.g., "NotBlank", "Length", "Min")
     */
    private String code;

    /**
     * Optional: The human-readable label for the field (e.g., "Given Name", "이름")
     * This can be used to display a localized field name in the UI
     */
    private String label;

    /**
     * Constructor for basic field error with field name and message
     */
    public ValidationFieldError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    /**
     * Constructor with field, message, rejected value, and code
     */
    public ValidationFieldError(String field, String message, Object rejectedValue, String code) {
        this.field = field;
        this.message = message;
        this.rejectedValue = rejectedValue;
        this.code = code;
    }
}
