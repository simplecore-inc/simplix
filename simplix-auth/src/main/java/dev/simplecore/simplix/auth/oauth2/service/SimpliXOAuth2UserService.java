package dev.simplecore.simplix.auth.oauth2.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Custom OAuth2 user service for non-OIDC providers (GitHub, Naver, Facebook).
 * Delegates to Spring Security's default implementation and provides extension points.
 */
@Slf4j
public class SimpliXOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.debug("Loading OAuth2 user from provider: {}", registrationId);

        OAuth2User oauth2User = delegate.loadUser(userRequest);

        log.debug("OAuth2 user loaded successfully: {}", oauth2User.getName());
        return oauth2User;
    }
}
