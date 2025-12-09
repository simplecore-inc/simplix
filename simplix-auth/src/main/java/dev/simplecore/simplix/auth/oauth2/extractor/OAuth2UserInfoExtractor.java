package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts standardized user information from OAuth2/OIDC authentication.
 * <p>
 * Delegates to provider-specific extractors using Strategy pattern.
 * This allows easy extension for new providers without modifying this class.
 * <p>
 * Supported providers:
 * <ul>
 *   <li>Google (OIDC)</li>
 *   <li>Kakao (OAuth2/OIDC)</li>
 *   <li>Naver (OAuth2)</li>
 *   <li>GitHub (OAuth2)</li>
 *   <li>Facebook (OAuth2)</li>
 *   <li>Apple (OIDC)</li>
 * </ul>
 */
@Slf4j
public class OAuth2UserInfoExtractor {

    private final Map<OAuth2ProviderType, OAuth2UserInfoExtractorStrategy> extractors;

    /**
     * Creates extractor with default provider implementations.
     */
    public OAuth2UserInfoExtractor() {
        this(List.of(
                new GoogleUserInfoExtractor(),
                new KakaoUserInfoExtractor(),
                new NaverUserInfoExtractor(),
                new GitHubUserInfoExtractor(),
                new FacebookUserInfoExtractor(),
                new AppleUserInfoExtractor()
        ));
    }

    /**
     * Creates extractor with custom provider implementations.
     * Useful for testing or adding custom providers.
     *
     * @param strategies list of extractor strategies
     */
    public OAuth2UserInfoExtractor(List<OAuth2UserInfoExtractorStrategy> strategies) {
        this.extractors = new EnumMap<>(OAuth2ProviderType.class);
        for (OAuth2UserInfoExtractorStrategy strategy : strategies) {
            this.extractors.put(strategy.getProviderType(), strategy);
        }
    }

    /**
     * Extract OAuth2UserInfo from an authentication object.
     *
     * @param authentication the OAuth2 authentication token
     * @return standardized user info
     * @throws IllegalArgumentException if authentication is not OAuth2 or provider is unsupported
     */
    public OAuth2UserInfo extract(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            throw new IllegalArgumentException(
                    "Authentication must be OAuth2AuthenticationToken, got: " +
                            authentication.getClass().getSimpleName());
        }

        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2ProviderType provider = OAuth2ProviderType.fromRegistrationId(registrationId);
        OAuth2User oauth2User = token.getPrincipal();
        Map<String, Object> attributes = oauth2User.getAttributes();

        log.debug("Extracting user info from provider: {}", provider);

        OAuth2UserInfoExtractorStrategy strategy = extractors.get(provider);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No extractor registered for provider: " + provider);
        }

        return strategy.extract(attributes, oauth2User);
    }

    /**
     * Register a custom extractor strategy.
     * Can be used to override built-in extractors or add new providers.
     *
     * @param strategy the extractor strategy to register
     */
    public void registerExtractor(OAuth2UserInfoExtractorStrategy strategy) {
        this.extractors.put(strategy.getProviderType(), strategy);
        log.info("Registered OAuth2 extractor for provider: {}", strategy.getProviderType());
    }

    /**
     * Check if an extractor is registered for a provider.
     *
     * @param provider the provider type
     * @return true if an extractor is registered
     */
    public boolean hasExtractor(OAuth2ProviderType provider) {
        return extractors.containsKey(provider);
    }
}
