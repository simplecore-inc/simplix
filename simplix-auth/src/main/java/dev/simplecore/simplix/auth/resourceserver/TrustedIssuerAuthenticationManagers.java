package dev.simplecore.simplix.auth.resourceserver;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties.TrustedIssuer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds one authentication manager per trusted issuer and a resolver that picks the manager by
 * the token's {@code iss} claim. A token from an issuer that is not configured is refused before
 * its signature is looked at.
 */
public final class TrustedIssuerAuthenticationManagers {

    private TrustedIssuerAuthenticationManagers() {
    }

    /**
     * @param issuers   the configured trusted issuers; at least one
     * @param converter maps a verified token to the application's authentication
     * @return a resolver over exactly the configured issuers
     */
    public static JwtIssuerAuthenticationManagerResolver resolver(List<TrustedIssuer> issuers,
                                                                  SimpliXJwtAuthenticationConverter converter) {
        if (issuers == null || issuers.isEmpty()) {
            throw new IllegalStateException(
                    "simplix.auth.resource-server.issuers must name at least one trusted issuer");
        }
        Map<String, AuthenticationManager> managers = new LinkedHashMap<>();
        for (TrustedIssuer issuer : issuers) {
            if (!StringUtils.hasText(issuer.getName()) || !StringUtils.hasText(issuer.getIssuerUri())) {
                throw new IllegalStateException("Every trusted issuer needs a name and an issuer-uri");
            }
            if (managers.containsKey(issuer.getIssuerUri())) {
                throw new IllegalStateException("Trusted issuer listed twice: " + issuer.getIssuerUri());
            }
            JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder(issuer));
            provider.setJwtAuthenticationConverter(jwt -> converter.convert(jwt, issuer));
            managers.put(issuer.getIssuerUri(), provider::authenticate);
        }
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    /**
     * The decoder for one issuer. Keys are fetched on the first token rather than at startup, so
     * the application starts while the issuer is still coming up.
     */
    static JwtDecoder decoder(TrustedIssuer issuer) {
        OAuth2TokenValidator<Jwt> validator = validator(issuer);
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = StringUtils.hasText(issuer.getJwkSetUri())
                    ? NimbusJwtDecoder.withJwkSetUri(issuer.getJwkSetUri()).build()
                    : NimbusJwtDecoder.withIssuerLocation(issuer.getIssuerUri()).build();
            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    static OAuth2TokenValidator<Jwt> validator(TrustedIssuer issuer) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuer.getIssuerUri()));
        if (issuer.getAudiences() != null && !issuer.getAudiences().isEmpty()) {
            validators.add(new AudienceValidator(issuer.getAudiences()));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
