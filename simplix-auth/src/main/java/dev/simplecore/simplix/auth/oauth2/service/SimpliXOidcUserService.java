package dev.simplecore.simplix.auth.oauth2.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Custom OIDC user service for OIDC providers (Google, Kakao, Apple).
 * Delegates to Spring Security's default implementation and provides extension points.
 */
@Slf4j
public class SimpliXOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.trace("Loading OIDC user from provider: {}", registrationId);

        OidcUser oidcUser = delegate.loadUser(userRequest);

        log.trace("OIDC user loaded successfully: sub={}", oidcUser.getSubject());
        return oidcUser;
    }
}
