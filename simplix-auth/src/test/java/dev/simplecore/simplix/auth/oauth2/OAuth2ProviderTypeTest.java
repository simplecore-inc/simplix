package dev.simplecore.simplix.auth.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OAuth2ProviderType")
class OAuth2ProviderTypeTest {

    @Test
    @DisplayName("should have all expected providers")
    void shouldHaveAllExpectedProviders() {
        assertThat(OAuth2ProviderType.values()).hasSize(6);
    }

    @ParameterizedTest
    @CsvSource({
            "google, GOOGLE, Google, true",
            "kakao, KAKAO, Kakao, true",
            "naver, NAVER, Naver, false",
            "github, GITHUB, GitHub, false",
            "facebook, FACEBOOK, Facebook, false",
            "apple, APPLE, Apple, true"
    })
    @DisplayName("should have correct properties for each provider")
    void shouldHaveCorrectProperties(String registrationId, String name,
                                      String displayName, boolean supportsOidc) {
        OAuth2ProviderType provider = OAuth2ProviderType.valueOf(name);

        assertThat(provider.getRegistrationId()).isEqualTo(registrationId);
        assertThat(provider.getDisplayName()).isEqualTo(displayName);
        assertThat(provider.isSupportsOidc()).isEqualTo(supportsOidc);
    }

    @Nested
    @DisplayName("fromRegistrationId")
    class FromRegistrationId {

        @Test
        @DisplayName("should find provider by registration ID")
        void shouldFindByRegistrationId() {
            assertThat(OAuth2ProviderType.fromRegistrationId("google"))
                    .isEqualTo(OAuth2ProviderType.GOOGLE);
            assertThat(OAuth2ProviderType.fromRegistrationId("kakao"))
                    .isEqualTo(OAuth2ProviderType.KAKAO);
        }

        @Test
        @DisplayName("should be case insensitive")
        void shouldBeCaseInsensitive() {
            assertThat(OAuth2ProviderType.fromRegistrationId("GOOGLE"))
                    .isEqualTo(OAuth2ProviderType.GOOGLE);
            assertThat(OAuth2ProviderType.fromRegistrationId("GitHub"))
                    .isEqualTo(OAuth2ProviderType.GITHUB);
        }

        @Test
        @DisplayName("should throw for unknown registration ID")
        void shouldThrowForUnknown() {
            assertThatThrownBy(() -> OAuth2ProviderType.fromRegistrationId("unknown"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown OAuth2 provider");
        }
    }

    @Nested
    @DisplayName("isSupported")
    class IsSupported {

        @ParameterizedTest
        @ValueSource(strings = {"google", "kakao", "naver", "github", "facebook", "apple"})
        @DisplayName("should return true for supported providers")
        void shouldReturnTrueForSupported(String registrationId) {
            assertThat(OAuth2ProviderType.isSupported(registrationId)).isTrue();
        }

        @Test
        @DisplayName("should return false for unsupported provider")
        void shouldReturnFalseForUnsupported() {
            assertThat(OAuth2ProviderType.isSupported("twitter")).isFalse();
            assertThat(OAuth2ProviderType.isSupported("linkedin")).isFalse();
        }
    }
}
