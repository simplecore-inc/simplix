package dev.simplecore.simplix.auth.resourceserver;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties.TrustedIssuer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified bearer token into the authentication the application works with.
 * <p>
 * The token's signature, issuer, lifetime and audience have already been checked when this runs,
 * and {@code issuer} names which trusted issuer signed it. An application registers its own bean
 * to resolve the token's subject to a local account, so that method security and auditing see the
 * application's principal rather than the raw token.
 */
@FunctionalInterface
public interface SimpliXJwtAuthenticationConverter {

    /**
     * @param jwt    the verified token
     * @param issuer the trusted issuer that signed it
     * @return the authentication to place in the security context
     */
    AbstractAuthenticationToken convert(Jwt jwt, TrustedIssuer issuer);
}
