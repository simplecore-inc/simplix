package dev.simplecore.simplix.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXApiResponse")
class SimpliXApiResponseTest {

    @Nested
    @DisplayName("success")
    class Success {

        @Test
        @DisplayName("should create success response with body")
        void shouldCreateSuccessWithBody() {
            SimpliXApiResponse<String> response = SimpliXApiResponse.success("data");

            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo("data");
            assertThat(response.getTimestamp()).isNotNull();
            assertThat(response.getMessage()).isNull();
            assertThat(response.getErrorCode()).isNull();
            assertThat(response.getErrorDetail()).isNull();
        }

        @Test
        @DisplayName("should create success response with body and message")
        void shouldCreateSuccessWithBodyAndMessage() {
            SimpliXApiResponse<String> response = SimpliXApiResponse.success("data", "Created");

            assertThat(response.getType()).isEqualTo("SUCCESS");
            assertThat(response.getBody()).isEqualTo("data");
            // Message is not set for SUCCESS type
            assertThat(response.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("failure")
    class Failure {

        @Test
        @DisplayName("should create failure response with message")
        void shouldCreateFailureWithMessage() {
            SimpliXApiResponse<Void> response = SimpliXApiResponse.failure("Something went wrong");

            assertThat(response.getType()).isEqualTo("FAILURE");
            assertThat(response.getMessage()).isEqualTo("Something went wrong");
            assertThat(response.getBody()).isNull();
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should create failure response with data and message")
        void shouldCreateFailureWithDataAndMessage() {
            SimpliXApiResponse<String> response = SimpliXApiResponse.failure("error-data", "Validation failed");

            assertThat(response.getType()).isEqualTo("FAILURE");
            assertThat(response.getMessage()).isEqualTo("Validation failed");
            assertThat(response.getBody()).isEqualTo("error-data");
        }
    }

    @Nested
    @DisplayName("error")
    class Error {

        @Test
        @DisplayName("should create error response with message")
        void shouldCreateErrorWithMessage() {
            SimpliXApiResponse<Object> response = SimpliXApiResponse.error("Internal error");

            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getMessage()).isEqualTo("Internal error");
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should create error response with message and error code")
        void shouldCreateErrorWithMessageAndCode() {
            SimpliXApiResponse<Object> response = SimpliXApiResponse.error("Not found", "ERR_404");

            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getMessage()).isEqualTo("Not found");
            assertThat(response.getErrorCode()).isEqualTo("ERR_404");
        }

        @Test
        @DisplayName("should create error response with message, code, and detail")
        void shouldCreateErrorWithAllFields() {
            SimpliXApiResponse<Object> response = SimpliXApiResponse.error(
                "Validation error", "ERR_VALIDATION", "Field 'name' is required"
            );

            assertThat(response.getType()).isEqualTo("ERROR");
            assertThat(response.getMessage()).isEqualTo("Validation error");
            assertThat(response.getErrorCode()).isEqualTo("ERR_VALIDATION");
            assertThat(response.getErrorDetail()).isEqualTo("Field 'name' is required");
        }
    }

    @Nested
    @DisplayName("ResponseType enum")
    class ResponseTypeEnum {

        @Test
        @DisplayName("should have expected values")
        void shouldHaveExpectedValues() {
            assertThat(SimpliXApiResponse.ResponseType.values()).containsExactly(
                SimpliXApiResponse.ResponseType.SUCCESS,
                SimpliXApiResponse.ResponseType.FAILURE,
                SimpliXApiResponse.ResponseType.ERROR
            );
        }
    }
}
