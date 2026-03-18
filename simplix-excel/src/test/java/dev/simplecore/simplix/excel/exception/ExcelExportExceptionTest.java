package dev.simplecore.simplix.excel.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelExportException")
class ExcelExportExceptionTest {

    @Test
    @DisplayName("should create exception with default constructor")
    void shouldCreateWithDefaultConstructor() {
        ExcelExportException ex = new ExcelExportException();
        assertThat(ex.getMessage()).isEqualTo("Excel export operation failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("should create exception with message")
    void shouldCreateWithMessage() {
        ExcelExportException ex = new ExcelExportException("Custom error");
        assertThat(ex.getMessage()).isEqualTo("Custom error");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("should create exception with cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        ExcelExportException ex = new ExcelExportException(cause);
        assertThat(ex.getMessage()).isEqualTo("Excel export operation failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should create exception with message and cause")
    void shouldCreateWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        ExcelExportException ex = new ExcelExportException("Custom error", cause);
        assertThat(ex.getMessage()).isEqualTo("Custom error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        ExcelExportException ex = new ExcelExportException();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
