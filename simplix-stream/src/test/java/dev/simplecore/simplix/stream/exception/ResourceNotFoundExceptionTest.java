package dev.simplecore.simplix.stream.exception;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResourceNotFoundException.
 */
@DisplayName("ResourceNotFoundException")
class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("should include resource name in message")
    void shouldIncludeResourceNameInMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("stock-price");

        assertThat(ex.getMessage()).contains("stock-price");
        assertThat(ex.getMessage()).contains("Stream resource not found");
    }

    @Test
    @DisplayName("should use GEN_NOT_FOUND error code")
    void shouldUseNotFoundErrorCode() {
        ResourceNotFoundException ex = new ResourceNotFoundException("test");

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.GEN_NOT_FOUND);
    }

    @Test
    @DisplayName("should store resource name as detail")
    void shouldStoreResourceNameAsDetail() {
        ResourceNotFoundException ex = new ResourceNotFoundException("order-updates");

        assertThat(ex.getDetail()).isEqualTo("order-updates");
    }

    @Test
    @DisplayName("should be a StreamException")
    void shouldBeStreamException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("test");

        assertThat(ex).isInstanceOf(StreamException.class);
    }
}
