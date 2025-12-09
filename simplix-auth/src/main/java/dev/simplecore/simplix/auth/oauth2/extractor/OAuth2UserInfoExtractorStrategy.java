package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

/**
 * Strategy interface for extracting user information from OAuth2/OIDC providers.
 * <p>
 * Each provider implementation handles the specific response format and
 * normalizes it into a standard OAuth2UserInfo object.
 */
public interface OAuth2UserInfoExtractorStrategy {

    /**
     * Get the provider type this extractor handles.
     *
     * @return the OAuth2 provider type
     */
    OAuth2ProviderType getProviderType();

    /**
     * Extract user information from OAuth2 attributes.
     *
     * @param attributes the raw attributes from the OAuth2/OIDC response
     * @param oauth2User the OAuth2User principal (may be OidcUser for OIDC providers)
     * @return standardized OAuth2UserInfo
     */
    OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User);

    /**
     * Helper method to safely get a String from attributes map.
     */
    default String getString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Helper method to safely get a boolean from attributes map.
     */
    default boolean getBoolean(Map<String, Object> map, String key) {
        if (map == null) return false;
        Object value = map.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
    }

    /**
     * Helper method to safely get a nested Map from attributes.
     */
    @SuppressWarnings("unchecked")
    default Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null) return Map.of();
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }
}
