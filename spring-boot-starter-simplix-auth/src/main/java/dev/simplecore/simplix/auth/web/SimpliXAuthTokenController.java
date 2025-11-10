package dev.simplecore.simplix.auth.web;

import com.nimbusds.jose.JOSEException;
import dev.simplecore.simplix.auth.exception.TokenValidationException;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@Tag(name = "Auth Token", description = "Token management endpoints")
@RestController
@RequestMapping("/auth/token")
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnBean(SimpliXJweTokenProvider.class)
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthTokenController {
    private final SimpliXJweTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final MessageSource messageSource;
    private final SimpliXAuthProperties properties;
    private final dev.simplecore.simplix.auth.security.SimpliXUserDetailsService userDetailsService;

    @Operation(
            summary = "Issue JWE token",
            description = "Issue a new JWE token using Basic authentication. Provide credentials in the Authorization header using Basic authentication format."
    )
    @SecurityRequirement(name = "Basic")
    @Parameter(
            name = "User-Agent",
            description = "Client user agent for tracking",
            required = false,
            in = ParameterIn.HEADER,
            schema = @Schema(type = "string")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token issued successfully",
                    content = @Content(schema = @Schema(implementation = SimpliXJweTokenProvider.TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = SimpliXApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - missing or malformed Authorization header",
                    content = @Content(schema = @Schema(implementation = SimpliXApiResponse.class))
            )
    })
    @RequestMapping(value = "/issue", method = {RequestMethod.GET})
    public ResponseEntity<SimpliXJweTokenProvider.TokenResponse> issueToken(HttpServletRequest request) throws JOSEException {
        String[] credentials = extractBasicAuthCredentials(request);
        if (credentials.length == 0) {
            throw new BadCredentialsException(
                    messageSource.getMessage("auth.basic.header.missing", null,
                            "Missing basic auth header",
                            LocaleContextHolder.getLocale())
            );
        }

        try {
            Authentication authRequest = new UsernamePasswordAuthenticationToken(credentials[0], credentials[1]);
            Authentication authentication = authenticationManager.authenticate(authRequest);

            // Set authentication in SecurityContextHolder
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(authentication);

            SimpliXJweTokenProvider.TokenResponse tokens = tokenProvider.createTokenPair(
                    authentication.getName(),
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            // Create session if configured
            if (properties.getToken().isCreateSessionOnTokenIssue()) {
                HttpSession session = request.getSession(true);

                // Set session timeout to match access token expiration
                int timeoutSeconds = properties.getToken().getAccessTokenExpirationMinutes() * 60;
                session.setMaxInactiveInterval(timeoutSeconds);

                // Save SecurityContext to session
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            }

            return ResponseEntity.ok(tokens);
        } catch (AuthenticationException e) {
            throw new BadCredentialsException(
                    messageSource.getMessage("auth.credentials.invalid", null,
                            "Invalid username/password",
                            LocaleContextHolder.getLocale())
            );
        }
    }

    @Operation(
            summary = "Refresh JWE token",
            description = "Issue a new access token using a valid refresh token. The refresh token must be provided in the X-Refresh-Token header."
    )
    @Parameters({
            @Parameter(
                    name = "X-Refresh-Token",
                    description = "Refresh token for obtaining new access token",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
            ),
            @Parameter(
                    name = "User-Agent",
                    description = "Client user agent for tracking",
                    required = false,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string")
            )
    })
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = SimpliXJweTokenProvider.TokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid or expired refresh token",
                    content = @Content(schema = @Schema(implementation = SimpliXApiResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - missing X-Refresh-Token header",
                    content = @Content(schema = @Schema(implementation = SimpliXApiResponse.class))
            )
    })
    @RequestMapping(value = "/refresh", method = {RequestMethod.GET})
    public ResponseEntity<SimpliXJweTokenProvider.TokenResponse> refreshToken(HttpServletRequest request) {
        try {
            String refreshToken = request.getHeader("X-Refresh-Token");
            if (refreshToken == null) {
                throw new TokenValidationException(
                        messageSource.getMessage("token.refresh.header.missing", null,
                                "Missing refresh token header",
                                LocaleContextHolder.getLocale()),
                        messageSource.getMessage("token.refresh.header.missing.detail", null,
                                "The X-Refresh-Token header must be provided",
                                LocaleContextHolder.getLocale())
                );
            }

            // Parse refresh token to extract username for session renewal
            var claims = tokenProvider.parseToken(refreshToken);
            String username = claims.getSubject();

            SimpliXJweTokenProvider.TokenResponse tokens = tokenProvider.refreshTokens(
                    refreshToken,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            // Update session with renewed authentication
            // This ensures the session is updated when tokens are refreshed
            var userDetails = userDetailsService.loadUserByUsername(username);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(authentication);

            // Renew session if configured (creates new session if expired)
            if (properties.getToken().isCreateSessionOnTokenIssue()) {
                HttpSession session = request.getSession(true);

                // Reset session timeout to match access token expiration
                int timeoutSeconds = properties.getToken().getAccessTokenExpirationMinutes() * 60;
                session.setMaxInactiveInterval(timeoutSeconds);

                // Save SecurityContext to session
                session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            }

            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            throw new TokenValidationException(
                    messageSource.getMessage("token.refresh.invalid", null,
                            "Invalid refresh token",
                            LocaleContextHolder.getLocale()),
                    messageSource.getMessage("token.refresh.invalid.detail", null,
                            "The refresh token is not valid or has expired",
                            LocaleContextHolder.getLocale())
            );
        }
    }

    //--------------------------------

    private String[] extractBasicAuthCredentials(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) {
            throw new BadCredentialsException(
                    messageSource.getMessage("auth.basic.header.missing", null,
                            "Missing basic auth header",
                            LocaleContextHolder.getLocale())
            );
        }
        String base64Credentials = header.substring("Basic ".length()).trim();
        String credentials = new String(Base64.getDecoder().decode(base64Credentials));
        return credentials.split(":", 2);
    }
} 