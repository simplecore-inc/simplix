package dev.simplecore.simplix.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * Token authentication failure handler wrapper for REST API endpoints.
 * This handler wraps the primary AuthenticationFailureHandler but does NOT write to the response,
 * leaving response handling to the REST controller/exception handler.
 *
 * This allows custom authentication failure logic (logging, tracking, events) to be shared
 * between form-based login and token-based authentication, while preventing response conflicts.
 */
@Slf4j
public class SimpliXTokenAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String remoteAddr = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String authHeader = request.getHeader("Authorization");

        // Extract username from Basic auth header if available
        String username = extractUsername(authHeader);

        log.warn("Token authentication failed - username: {}, IP: {}, User-Agent: {}, reason: {}",
                username != null ? username : "unknown", remoteAddr, userAgent, exception.getMessage());

        // Note: Additional custom logic should be added by overriding the primary
        // "authenticationFailureHandler" bean, which this handler can delegate to if needed.

        // DO NOT write to response - this is handled by the controller/exception handler
    }

    private String extractUsername(String authHeader) {
        // Null and empty check
        if (authHeader == null || authHeader.isEmpty()) {
            return null;
        }

        // Check if it's Basic auth
        if (authHeader.startsWith("Basic ")) {
            try {
                // Extract base64 encoded credentials (trim for safety)
                String base64Credentials = authHeader.substring("Basic ".length()).trim();
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

                // Split username:password
                String[] parts = credentials.split(":", 2);
                return parts.length > 0 ? parts[0] : null;
            } catch (IllegalArgumentException e) {
                log.debug("Failed to decode Basic auth header", e);
                return null;
            }
        }

        // Not a Basic auth header (e.g., Bearer token)
        return null;
    }
}
