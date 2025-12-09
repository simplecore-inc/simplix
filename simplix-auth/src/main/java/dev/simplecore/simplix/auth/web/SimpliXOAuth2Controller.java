package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService;
import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.handler.OAuth2LoginSuccessHandler;
import dev.simplecore.simplix.auth.oauth2.properties.SimpliXOAuth2Properties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Controller for OAuth2 account linking flow.
 *
 * <p>This controller handles the initiation of OAuth2 account linking.
 * It stores the current user's ID in the session and redirects to the
 * OAuth2 authorization endpoint. After successful OAuth2 authentication,
 * the {@link OAuth2LoginSuccessHandler} will detect the linking mode and
 * call {@link dev.simplecore.simplix.auth.oauth2.OAuth2AuthenticationService#linkSocialAccount}.</p>
 */
@Tag(name = "OAuth2 Social Login", description = "OAuth2/OIDC social login endpoints for authentication and account linking")
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "simplix.auth.oauth2", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXOAuth2Controller {

    private final SimpliXOAuth2Properties properties;
    private final OAuth2AuthenticationService authService;

    @Operation(
            summary = "Link Social Account",
            description = """
                    Links a social account to the currently authenticated user.
                    Requires valid authentication (JWT token or session).

                    **Use Case:** User already has an account and wants to add
                    social login as an additional authentication method.

                    **Flow:**
                    1. Authenticated user calls this endpoint
                    2. User's ID is stored in session
                    3. Redirect to OAuth2 provider's authorization page
                    4. User authenticates with the provider
                    5. Social account is linked to existing user
                    6. Redirect to success page
                    """
    )
    @SecurityRequirement(name = "Bearer")
    @Parameter(
            name = "provider",
            description = "OAuth2 provider name",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(
                    type = "string",
                    allowableValues = {"google", "kakao", "naver", "github", "facebook", "apple"},
                    example = "google"
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "302",
                    description = "Redirect to OAuth2 provider for linking"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or unsupported provider"
            )
    })
    @GetMapping("/link/{provider}")
    public RedirectView linkAccount(
            @PathVariable String provider,
            HttpServletRequest request) {

        // Validate provider
        try {
            OAuth2ProviderType.fromRegistrationId(provider);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid OAuth2 provider for linking: {}", provider);
            return new RedirectView(appendQueryParam(properties.getLinkFailureUrl(), "error", "INVALID_PROVIDER"));
        }

        // Get current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("Unauthenticated user attempted to link OAuth2 account");
            return new RedirectView(appendQueryParam(properties.getLinkFailureUrl(), "error", "UNAUTHENTICATED"));
        }

        // Extract user ID from authentication principal
        String userId = extractUserId(authentication);
        if (userId == null) {
            log.error("Could not extract user ID from authentication principal: {}",
                    authentication.getPrincipal().getClass().getName());
            return new RedirectView(appendQueryParam(properties.getLinkFailureUrl(), "error", "INVALID_PRINCIPAL"));
        }

        // Store user ID in session for the success handler
        HttpSession session = request.getSession(true);
        session.setAttribute(OAuth2LoginSuccessHandler.LINKING_USER_ID_ATTR, userId);

        log.debug("Initiating OAuth2 account linking: user={}, provider={}", userId, provider);

        // Redirect to OAuth2 authorization endpoint
        String authorizationUrl = properties.getAuthorizationBaseUrl() + "/" + provider;
        return new RedirectView(authorizationUrl);
    }

    @Operation(
            summary = "Start OAuth2 Authorization Flow",
            description = """
                    Initiates the OAuth2 authorization flow for the specified provider.
                    The user will be redirected to the provider's login page.

                    **Note:** This endpoint is handled by Spring Security's OAuth2 filter.
                    It is documented here for API reference purposes.

                    **Supported Providers:** Google, Kakao, Naver, GitHub, Facebook, Apple

                    **Flow:**
                    1. Client opens this URL in a popup or redirects
                    2. User authenticates with the provider
                    3. Provider redirects back to callback URL
                    4. Server issues JWT tokens
                    """
    )
    @Parameter(
            name = "provider",
            description = "OAuth2 provider name",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(
                    type = "string",
                    allowableValues = {"google", "kakao", "naver", "github", "facebook", "apple"},
                    example = "google"
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "302",
                    description = "Redirect to OAuth2 provider's authorization page"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or unsupported provider"
            )
    })
    @GetMapping("/authorize/{provider}")
    public void authorize(@PathVariable String provider) {
        // This endpoint is handled by Spring Security's OAuth2AuthorizationRequestRedirectFilter.
        // This method exists only for Swagger documentation purposes.
        // The actual request will never reach this method.
    }

    @Operation(
            summary = "OAuth2 Callback (Internal)",
            description = """
                    Handles the OAuth2 callback from the provider.
                    This endpoint is called by the OAuth2 provider after user authentication.

                    **Note:** This endpoint is handled by Spring Security's OAuth2 filter.
                    It is documented here for API reference purposes.

                    **Important:** This endpoint is typically not called directly by clients.
                    It processes the authorization code and issues JWT tokens.

                    **Success Response:** Redirects with tokens or renders postMessage page
                    **Error Response:** Redirects to error page with error code
                    """
    )
    @Parameter(
            name = "provider",
            description = "OAuth2 provider name",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(
                    type = "string",
                    allowableValues = {"google", "kakao", "naver", "github", "facebook", "apple"},
                    example = "google"
            )
    )
    @Parameter(
            name = "code",
            description = "Authorization code from provider",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string")
    )
    @Parameter(
            name = "state",
            description = "State parameter for CSRF protection",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "302",
                    description = "Redirect to success/failure URL or render postMessage page"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed"
            )
    })
    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable String provider) {
        // This endpoint is handled by Spring Security's OAuth2LoginAuthenticationFilter.
        // This method exists only for Swagger documentation purposes.
        // The actual request will never reach this method.
    }

    /**
     * Extracts the user ID from the authentication principal.
     * <p>
     * Supports:
     * <ul>
     *   <li>Custom UserDetails with getId() or getUserId() method</li>
     *   <li>OAuth2 authenticated users (looks up user ID by social connection)</li>
     *   <li>Standard UserDetails (falls back to username)</li>
     * </ul>
     */
    private String extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        // Try getId() method (common in custom UserDetails)
        try {
            var getIdMethod = principal.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(principal);
            if (id != null) {
                return id.toString();
            }
        } catch (NoSuchMethodException e) {
            // Method not found, try other approaches
        } catch (Exception e) {
            log.debug("Failed to invoke getId() on principal", e);
        }

        // Try getUserId() method
        try {
            var getUserIdMethod = principal.getClass().getMethod("getUserId");
            Object id = getUserIdMethod.invoke(principal);
            if (id != null) {
                return id.toString();
            }
        } catch (NoSuchMethodException e) {
            // Method not found, try other approaches
        } catch (Exception e) {
            log.debug("Failed to invoke getUserId() on principal", e);
        }

        // Handle OAuth2 authenticated users
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token &&
                principal instanceof OAuth2User oauth2User) {
            String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
            String providerId = oauth2User.getName(); // Provider's user ID (sub for OIDC)

            // First try to find by provider connection
            try {
                OAuth2ProviderType providerType = OAuth2ProviderType.fromRegistrationId(registrationId);
                String userId = authService.findUserIdByProviderConnection(providerType, providerId);
                if (userId != null) {
                    log.debug("Found user ID from OAuth2 connection: provider={}, userId={}", registrationId, userId);
                    return userId;
                }
                log.debug("No social connection found for provider={}, providerId={}", registrationId, providerId);
            } catch (Exception e) {
                log.debug("Failed to lookup user by OAuth2 connection", e);
            }

            // Fallback: try to find by email from OAuth2 attributes
            Object emailAttr = oauth2User.getAttributes().get("email");
            if (emailAttr != null) {
                String email = emailAttr.toString();
                try {
                    String userId = authService.findUserIdByEmail(email);
                    if (userId != null) {
                        log.debug("Found user ID by email: email={}, userId={}", email, userId);
                        return userId;
                    }
                    log.warn("No user found for email: {}", email);
                } catch (Exception e) {
                    log.debug("Failed to lookup user by email", e);
                }
            }
        }

        // Fallback to username as ID for standard UserDetails
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            log.warn("Using username as user ID for linking - consider implementing getId() in your UserDetails");
            return userDetails.getUsername();
        }

        return null;
    }

    /**
     * Appends a query parameter to a URL, handling existing query parameters correctly.
     *
     * @param url   base URL (may already contain query parameters)
     * @param key   parameter name
     * @param value parameter value
     * @return URL with the appended query parameter
     */
    private String appendQueryParam(String url, String key, String value) {
        if (url == null) {
            return "?" + key + "=" + value;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + key + "=" + value;
    }
}
