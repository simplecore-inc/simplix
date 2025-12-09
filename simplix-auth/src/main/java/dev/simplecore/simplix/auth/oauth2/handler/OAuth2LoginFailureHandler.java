package dev.simplecore.simplix.auth.oauth2.handler;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationException;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Handles OAuth2 authentication failures.
 * Redirects to the configured failure URL with error information.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final SimpliXOAuth2Properties properties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        String errorCode;

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            errorCode = oauthEx.getErrorCode();
            log.warn("OAuth2 authentication failed: {} - {}", errorCode, exception.getMessage());
        } else if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth2Ex) {
            errorCode = oauth2Ex.getError().getErrorCode();
            log.warn("OAuth2 provider error: {} - {}", errorCode, oauth2Ex.getMessage());
        } else {
            errorCode = OAuth2AuthenticationException.PROVIDER_ERROR;
            log.error("Unexpected authentication failure", exception);
        }

        String redirectUrl = UriComponentsBuilder
                .fromUriString(properties.getFailureUrl())
                .queryParam("error", errorCode)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
