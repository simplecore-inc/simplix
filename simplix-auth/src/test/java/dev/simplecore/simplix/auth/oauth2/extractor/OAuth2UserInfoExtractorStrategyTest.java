package dev.simplecore.simplix.auth.oauth2.extractor;

import dev.simplecore.simplix.auth.oauth2.OAuth2ProviderType;
import dev.simplecore.simplix.auth.oauth2.OAuth2UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2UserInfoExtractorStrategy default methods")
class OAuth2UserInfoExtractorStrategyTest {

    private final OAuth2UserInfoExtractorStrategy strategy = new OAuth2UserInfoExtractorStrategy() {
        @Override
        public OAuth2ProviderType getProviderType() {
            return OAuth2ProviderType.GOOGLE;
        }

        @Override
        public OAuth2UserInfo extract(Map<String, Object> attributes, OAuth2User oauth2User) {
            return null;
        }
    };

    @Test
    @DisplayName("getString should return value as string")
    void getStringShouldReturnValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");

        assertThat(strategy.getString(map, "key")).isEqualTo("value");
    }

    @Test
    @DisplayName("getString should return null for missing key")
    void getStringShouldReturnNullForMissingKey() {
        Map<String, Object> map = new HashMap<>();

        assertThat(strategy.getString(map, "missing")).isNull();
    }

    @Test
    @DisplayName("getString should return null for null map")
    void getStringShouldReturnNullForNullMap() {
        assertThat(strategy.getString(null, "key")).isNull();
    }

    @Test
    @DisplayName("getString should convert non-string values to string")
    void getStringShouldConvertNonString() {
        Map<String, Object> map = new HashMap<>();
        map.put("number", 42);

        assertThat(strategy.getString(map, "number")).isEqualTo("42");
    }

    @Test
    @DisplayName("getBoolean should return boolean value")
    void getBooleanShouldReturnBooleanValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("flag", true);

        assertThat(strategy.getBoolean(map, "flag")).isTrue();
    }

    @Test
    @DisplayName("getBoolean should parse string boolean")
    void getBooleanShouldParseString() {
        Map<String, Object> map = new HashMap<>();
        map.put("flag", "true");

        assertThat(strategy.getBoolean(map, "flag")).isTrue();
    }

    @Test
    @DisplayName("getBoolean should return false for null map")
    void getBooleanShouldReturnFalseForNullMap() {
        assertThat(strategy.getBoolean(null, "key")).isFalse();
    }

    @Test
    @DisplayName("getBoolean should return false for missing key")
    void getBooleanShouldReturnFalseForMissingKey() {
        Map<String, Object> map = new HashMap<>();
        assertThat(strategy.getBoolean(map, "missing")).isFalse();
    }

    @Test
    @DisplayName("getMap should return nested map")
    void getMapShouldReturnNestedMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", "value");

        Map<String, Object> map = new HashMap<>();
        map.put("nested", nested);

        assertThat(strategy.getMap(map, "nested")).isEqualTo(nested);
    }

    @Test
    @DisplayName("getMap should return empty map for null map")
    void getMapShouldReturnEmptyForNullMap() {
        assertThat(strategy.getMap(null, "key")).isEmpty();
    }

    @Test
    @DisplayName("getMap should return empty map for missing key")
    void getMapShouldReturnEmptyForMissingKey() {
        Map<String, Object> map = new HashMap<>();
        assertThat(strategy.getMap(map, "missing")).isEmpty();
    }

    @Test
    @DisplayName("getMap should return empty map for non-map value")
    void getMapShouldReturnEmptyForNonMapValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "not a map");

        assertThat(strategy.getMap(map, "key")).isEmpty();
    }
}
