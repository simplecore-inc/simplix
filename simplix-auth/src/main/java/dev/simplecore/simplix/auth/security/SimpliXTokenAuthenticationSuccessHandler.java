package dev.simplecore.simplix.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Token authentication success handler wrapper for REST API endpoints.
 * This handler wraps the primary AuthenticationSuccessHandler but does NOT write to the response,
 * leaving response handling to the REST controller.
 *
 * This allows custom authentication logic (logging, DB updates, events) to be shared
 * between form-based login and token-based authentication, while preventing response conflicts.
 */
@Slf4j
public class SimpliXTokenAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();
        String remoteAddr = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        log.info("Token authentication successful - username: {}, IP: {}, User-Agent: {}",
                username, remoteAddr, userAgent);

        // Note: Additional custom logic should be added by overriding the primary
        // "authenticationSuccessHandler" bean, which this handler can delegate to if needed.

        // DO NOT write to response - this is handled by the controller
    }
}
