package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Extracts user information from any provider that speaks OIDC as the specification writes it.
 *
 * <p><b>The provider-specific extractors beside this one exist because consumer providers answer
 * in shapes of their own</b> — Kakao nests the account under {@code kakao_account}, Naver under
 * {@code response}, GitHub answers no {@code sub} at all. A provider that publishes a discovery
 * document and issues an ID token answers with the claims OpenID Connect Core §5.1 names, so
 * there is nothing left for an extractor to know: the same code reads Keycloak, Entra ID, Okta
 * and an organization's own server.
 *
 * <p><b>Every claim is carried through in {@code attributes}.</b> An enterprise directory sends
 * group membership, department, employee number — claims no framework can enumerate — and the
 * application maps them onto its own roles. Dropping them here would leave that application
 * asking the provider a second time for what it was already sent.
 *
 * @see OAuth2ProviderType#OIDC
 */
public class StandardOidcUserInfoExtractor implements OAuth2UserInfoExtractorStrategy {

    @Override
    public OAuth2ProviderType getProviderType() {
        return OAuth2ProviderType.OIDC;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the principal carries no ID token, which means the
     *         registration is not OIDC and needs an extractor of its own
     */
    @Override
    public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
        if (!(oauth2User instanceof OidcUser oidcUser)) {
            // Deliberately a refusal rather than a best effort at the attributes. A plain OAuth2
            // provider answers its userinfo endpoint in a shape only that provider knows, so
            // guessing here would put somebody else's account behind whichever field happened to
            // parse. Registering a strategy is the way through.
            throw new IllegalArgumentException(
                    "Registration '" + registrationIdOf(oauth2User) + "' returned no ID token, so it "
                            + "is not an OIDC provider. Register an OAuth2UserInfoExtractorStrategy "
                            + "for it via OAuth2UserInfoExtractor.registerExtractor(...).");
        }

        return OAuth2UserInfo.builder()
                .provider(OAuth2ProviderType.OIDC)
                .providerId(oidcUser.getSubject())
                .email(oidcUser.getEmail())
                .emailVerified(Boolean.TRUE.equals(oidcUser.getEmailVerified()))
                .name(oidcUser.getFullName())
                .firstName(oidcUser.getGivenName())
                .lastName(oidcUser.getFamilyName())
                .profileImageUrl(oidcUser.getPicture())
                .locale(oidcUser.getLocale())
                .attributes(attributes)
                .build();
    }

    /**
     * @param oauth2User the principal
     * @return something to name it by in the refusal above, without assuming any claim is present
     */
    private static String registrationIdOf(OAuth2User oauth2User) {
        return oauth2User == null ? "unknown" : oauth2User.getName();
    }
}
