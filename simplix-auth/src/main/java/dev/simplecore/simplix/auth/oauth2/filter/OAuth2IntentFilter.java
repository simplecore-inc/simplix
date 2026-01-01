package dev.simplecore.simplix.auth.oauth2.filter;

import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that handles OAuth2 login and register intent.
 * <p>
 * This filter intercepts requests to login and register endpoints,
 * stores the intent in session, and redirects to the standard authorization endpoint.
 * <p>
 * Supported intents:
 * <ul>
 *   <li>{@code login} - Only authenticate existing users, reject if no linked account</li>
 *   <li>{@code register} - Create new account if not exists (default OAuth2 behavior)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2IntentFilter extends OncePerRequestFilter {

    /**
     * Session attribute key for storing OAuth2 intent.
     */
    public static final String OAUTH2_INTENT_ATTR = "oauth2_intent";

    /**
     * Intent value for login-only mode.
     */
    public static final String INTENT_LOGIN = "login";

    /**
     * Intent value for registration mode.
     */
    public static final String INTENT_REGISTER = "register";

    private final SimpliXOAuth2Properties properties;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String loginBaseUrl = properties.getLoginBaseUrl();
        String registerBaseUrl = properties.getRegisterBaseUrl();
        String authorizationBaseUrl = properties.getAuthorizationBaseUrl();

        log.trace("OAuth2IntentFilter processing request: uri={}, loginBaseUrl={}, registerBaseUrl={}",
                requestUri, loginBaseUrl, registerBaseUrl);

        // Handle login endpoint
        if (requestUri.startsWith(loginBaseUrl + "/")) {
            String provider = extractProvider(requestUri, loginBaseUrl);
            if (provider != null && !provider.isEmpty()) {
                log.trace("OAuth2 login intent detected for provider: {}", provider);
                request.getSession().setAttribute(OAUTH2_INTENT_ATTR, INTENT_LOGIN);
                String redirectUrl = authorizationBaseUrl + "/" + provider;
                log.info("OAuth2 login intent stored in session (id={}), redirecting to: {}",
                        request.getSession().getId(), redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            }
        }

        // Handle register endpoint
        if (requestUri.startsWith(registerBaseUrl + "/")) {
            String provider = extractProvider(requestUri, registerBaseUrl);
            if (provider != null && !provider.isEmpty()) {
                log.trace("OAuth2 register intent detected for provider: {}", provider);
                request.getSession().setAttribute(OAUTH2_INTENT_ATTR, INTENT_REGISTER);
                String redirectUrl = authorizationBaseUrl + "/" + provider;
                log.info("OAuth2 register intent stored in session (id={}), redirecting to: {}",
                        request.getSession().getId(), redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract provider name from request URI.
     */
    private String extractProvider(String requestUri, String baseUrl) {
        if (requestUri.length() > baseUrl.length() + 1) {
            return requestUri.substring(baseUrl.length() + 1);
        }
        return null;
    }
}
