package dev.simplecore.simplix.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2AuthenticationException")
class OAuth2AuthenticationExceptionTest {

    @Test
    @DisplayName("should create with error code and message")
    void shouldCreateWithErrorCodeAndMessage() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                OAuth2AuthenticationException.EMAIL_ALREADY_EXISTS,
                "Email already registered");

        assertThat(exception.getErrorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(exception.getMessage()).isEqualTo("Email already registered");
    }

    @Test
    @DisplayName("should create with error code, message, and cause")
    void shouldCreateWithCause() {
        RuntimeException cause = new RuntimeException("underlying error");
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                OAuth2AuthenticationException.PROVIDER_ERROR,
                "Provider communication failed",
                cause);

        assertThat(exception.getErrorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(exception.getMessage()).isEqualTo("Provider communication failed");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("should have all expected error code constants")
    void shouldHaveAllErrorCodeConstants() {
        assertThat(OAuth2AuthenticationException.EMAIL_ALREADY_EXISTS).isEqualTo("EMAIL_ALREADY_EXISTS");
        assertThat(OAuth2AuthenticationException.SOCIAL_ALREADY_LINKED).isEqualTo("SOCIAL_ALREADY_LINKED");
        assertThat(OAuth2AuthenticationException.PROVIDER_ALREADY_LINKED).isEqualTo("PROVIDER_ALREADY_LINKED");
        assertThat(OAuth2AuthenticationException.LAST_LOGIN_METHOD).isEqualTo("LAST_LOGIN_METHOD");
        assertThat(OAuth2AuthenticationException.PROVIDER_ERROR).isEqualTo("PROVIDER_ERROR");
        assertThat(OAuth2AuthenticationException.LINKING_FAILED).isEqualTo("LINKING_FAILED");
        assertThat(OAuth2AuthenticationException.USER_NOT_FOUND).isEqualTo("USER_NOT_FOUND");
        assertThat(OAuth2AuthenticationException.NO_LINKED_ACCOUNT).isEqualTo("NO_LINKED_ACCOUNT");
        assertThat(OAuth2AuthenticationException.EMAIL_ACCOUNT_EXISTS_NOT_LINKED).isEqualTo("EMAIL_ACCOUNT_EXISTS_NOT_LINKED");
        assertThat(OAuth2AuthenticationException.SOCIAL_ALREADY_REGISTERED).isEqualTo("SOCIAL_ALREADY_REGISTERED");
    }

    @Test
    @DisplayName("should extend Spring AuthenticationException")
    void shouldExtendAuthenticationException() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                "TEST", "test message");

        assertThat(exception).isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }
}
