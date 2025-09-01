package dev.simplecore.simplix.auth.web;

import com.nimbusds.jose.JOSEException;
import dev.simplecore.simplix.auth.exception.TokenValidationException;
import dev.simplecore.simplix.auth.security.SimpliXJweTokenProvider;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;

@Tag(name = "Auth Token", description = "Token management endpoints")
@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnBean(SimpliXJweTokenProvider.class)
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthTokenController {
    private final SimpliXJweTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final MessageSource messageSource;

    @Operation(
        summary = "Issue JWE token",
        description = "Issue a new JWE token using basic authentication"
    )
    @SecurityRequirement(name = "Basic")
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
            SecurityContextHolder.getContext().setAuthentication(authentication);

            SimpliXJweTokenProvider.TokenResponse tokens = tokenProvider.createTokenPair(
                authentication.getName(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
            );

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
        description = "Issue a new JWE token using an existing valid token"
    )
    @SecurityRequirement(name = "Bearer")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully",
            content = @Content(schema = @Schema(implementation = SimpliXJweTokenProvider.TokenResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired token",
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

            SimpliXJweTokenProvider.TokenResponse tokens = tokenProvider.refreshTokens(
                refreshToken,
                request.getRemoteAddr(),
                request.getHeader("User-Agent")
            );

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