package dev.simplecore.simplix.web.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValidationFieldError - validation field error DTO")
class ValidationFieldErrorTest {

    @Test
    @DisplayName("Should create instance with no-arg constructor and set fields via setters")
    void noArgConstructorAndSetters() {
        ValidationFieldError error = new ValidationFieldError();
        error.setField("email");
        error.setMessage("must not be blank");
        error.setRejectedValue("");
        error.setCode("NotBlank");
        error.setLabel("Email Address");

        assertThat(error.getField()).isEqualTo("email");
        assertThat(error.getMessage()).isEqualTo("must not be blank");
        assertThat(error.getRejectedValue()).isEqualTo("");
        assertThat(error.getCode()).isEqualTo("NotBlank");
        assertThat(error.getLabel()).isEqualTo("Email Address");
    }

    @Test
    @DisplayName("Should create instance with two-arg constructor (field, message)")
    void twoArgConstructor() {
        ValidationFieldError error = new ValidationFieldError("name", "required");

        assertThat(error.getField()).isEqualTo("name");
        assertThat(error.getMessage()).isEqualTo("required");
        assertThat(error.getRejectedValue()).isNull();
        assertThat(error.getCode()).isNull();
        assertThat(error.getLabel()).isNull();
    }

    @Test
    @DisplayName("Should create instance with four-arg constructor (field, message, rejectedValue, code)")
    void fourArgConstructor() {
        ValidationFieldError error = new ValidationFieldError("age", "must be at least 18", 10, "Min");

        assertThat(error.getField()).isEqualTo("age");
        assertThat(error.getMessage()).isEqualTo("must be at least 18");
        assertThat(error.getRejectedValue()).isEqualTo(10);
        assertThat(error.getCode()).isEqualTo("Min");
        assertThat(error.getLabel()).isNull();
    }

    @Test
    @DisplayName("Should create instance with all-args constructor")
    void allArgsConstructor() {
        ValidationFieldError error = new ValidationFieldError("username", "too short", "ab", "Length", "Username");

        assertThat(error.getField()).isEqualTo("username");
        assertThat(error.getMessage()).isEqualTo("too short");
        assertThat(error.getRejectedValue()).isEqualTo("ab");
        assertThat(error.getCode()).isEqualTo("Length");
        assertThat(error.getLabel()).isEqualTo("Username");
    }

    @Test
    @DisplayName("Should allow null values for all fields")
    void nullValues() {
        ValidationFieldError error = new ValidationFieldError(null, null, null, null);

        assertThat(error.getField()).isNull();
        assertThat(error.getMessage()).isNull();
        assertThat(error.getRejectedValue()).isNull();
        assertThat(error.getCode()).isNull();
    }
}
