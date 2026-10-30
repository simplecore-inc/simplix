package dev.simplecore.simplix.auth.resourceserver;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties.TrustedIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedIssuerAuthenticationManagersTest {

    private static final String USERS = "https://id.example.test/realms/users";
    private static final String STAFF = "https://id.example.test/realms/staff";
    private static final String API = "example-api";

    private static HttpServer jwks;
    private static RSAKey key;
    private static String jwkSetUri;

    @BeforeAll
    static void serveKeys() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("k1").generate();
        byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwks.start();
        jwkSetUri = "http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs";
    }

    @AfterAll
    static void stop() {
        jwks.stop(0);
    }

    private static TrustedIssuer issuer(String name, String uri) {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setName(name);
        issuer.setIssuerUri(uri);
        issuer.setJwkSetUri(jwkSetUri);
        issuer.setAudiences(List.of(API));
        return issuer;
    }

    private static String token(String iss, String aud, Instant expires) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(iss)
                .subject("subject-1")
                .audience(aud)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(Date.from(expires))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Authentication authenticate(JwtIssuerAuthenticationManagerResolver resolver, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        AuthenticationManager manager = resolver.resolve(request);
        return manager.authenticate(new BearerTokenAuthenticationToken(token));
    }

    private final AtomicReference<String> convertedFor = new AtomicReference<>();

    private JwtIssuerAuthenticationManagerResolver resolver() {
        SimpliXJwtAuthenticationConverter converter = (jwt, issuer) -> {
            convertedFor.set(issuer.getName());
            AbstractAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
            return authentication;
        };
        return TrustedIssuerAuthenticationManagers.resolver(
                List.of(issuer("users", USERS), issuer("staff", STAFF)), converter);
    }

    @Test
    void acceptsATokenFromEachTrustedIssuerAndTellsTheConverterWhichSignedIt() throws Exception {
        JwtIssuerAuthenticationManagerResolver resolver = resolver();

        Authentication users = authenticate(resolver, token(USERS, API, Instant.now().plusSeconds(300)));
        assertThat(users.isAuthenticated()).isTrue();
        assertThat(convertedFor.get()).isEqualTo("users");

        authenticate(resolver, token(STAFF, API, Instant.now().plusSeconds(300)));
        assertThat(convertedFor.get()).isEqualTo("staff");
    }

    @Test
    void refusesATokenFromAnIssuerThatIsNotConfigured() throws Exception {
        String foreign = token("https://id.example.test/realms/other", API, Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> authenticate(resolver(), foreign)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refusesATokenIssuedForAnotherAudience() throws Exception {
        String other = token(USERS, "another-client", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> authenticate(resolver(), other))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("not issued for this resource server");
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        String expired = token(USERS, API, Instant.now().minusSeconds(600));
        assertThatThrownBy(() -> authenticate(resolver(), expired)).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void refusesToStartWithoutATrustedIssuer() {
        assertThatThrownBy(() -> TrustedIssuerAuthenticationManagers.resolver(List.of(), (jwt, issuer) -> null))
                .isInstanceOf(IllegalStateException.class);
    }
}
