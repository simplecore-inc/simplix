package dev.simplecore.simplix.auth.resourceserver;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/**
 * Accepts a token only when its {@code aud} claim names at least one of the accepted audiences.
 * <p>
 * Without this check an issuer that signs tokens for several clients would have every one of
 * them accepted here, including tokens minted for an unrelated application.
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token was not issued for this resource server", null);

    private final List<String> accepted;

    /**
     * @param accepted the audiences of which a token must name at least one; must not be empty
     */
    public AudienceValidator(Collection<String> accepted) {
        if (accepted == null || accepted.isEmpty()) {
            throw new IllegalArgumentException("At least one accepted audience is required");
        }
        this.accepted = List.copyOf(accepted);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.stream().anyMatch(accepted::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
