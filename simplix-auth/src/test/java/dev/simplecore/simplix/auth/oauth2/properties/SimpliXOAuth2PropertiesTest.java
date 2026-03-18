package dev.simplecore.simplix.auth.oauth2.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXOAuth2Properties")
class SimpliXOAuth2PropertiesTest {

    private SimpliXOAuth2Properties properties;

    @BeforeEach
    void setUp() {
        properties = new SimpliXOAuth2Properties();
    }

    @Nested
    @DisplayName("default values")
    class DefaultValues {

        @Test
        @DisplayName("should have correct default enabled state")
        void shouldHaveDefaultEnabled() {
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should have correct default URLs")
        void shouldHaveDefaultUrls() {
            assertThat(properties.getSuccessUrl()).isEqualTo("/");
            assertThat(properties.getFailureUrl()).isEqualTo("/login?error=social");
            assertThat(properties.getLinkSuccessUrl()).isEqualTo("/settings/social?linked=true");
            assertThat(properties.getLinkFailureUrl()).isEqualTo("/settings/social?error=link_failed");
            assertThat(properties.getLinkBaseUrl()).isEqualTo("/oauth2/link");
            assertThat(properties.getAuthorizationBaseUrl()).isEqualTo("/oauth2/authorize");
            assertThat(properties.getCallbackBaseUrl()).isEqualTo("/oauth2/callback");
            assertThat(properties.getLoginBaseUrl()).isEqualTo("/oauth2/login");
            assertThat(properties.getRegisterBaseUrl()).isEqualTo("/oauth2/register");
        }

        @Test
        @DisplayName("should have correct default email conflict policy")
        void shouldHaveDefaultEmailConflictPolicy() {
            assertThat(properties.getEmailConflictPolicy())
                    .isEqualTo(SimpliXOAuth2Properties.EmailConflictPolicy.REJECT);
        }

        @Test
        @DisplayName("should have correct default token delivery method")
        void shouldHaveDefaultTokenDeliveryMethod() {
            assertThat(properties.getTokenDeliveryMethod())
                    .isEqualTo(SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE);
        }

        @Test
        @DisplayName("should have correct default pending registration TTL")
        void shouldHaveDefaultPendingRegistrationTtl() {
            assertThat(properties.getPendingRegistrationTtlSeconds()).isEqualTo(600L);
        }
    }

    @Nested
    @DisplayName("getPostMessageOrigin")
    class GetPostMessageOrigin {

        @Test
        @DisplayName("should return * when no origins configured")
        void shouldReturnWildcardWhenNoOrigins() {
            assertThat(properties.getPostMessageOrigin()).isEqualTo("*");
        }

        @Test
        @DisplayName("should return first origin when configured")
        void shouldReturnFirstOrigin() {
            properties.setAllowedOrigins(List.of("https://example.com", "https://other.com"));

            assertThat(properties.getPostMessageOrigin()).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should return * when origins list is empty")
        void shouldReturnWildcardWhenEmpty() {
            properties.setAllowedOrigins(List.of());

            assertThat(properties.getPostMessageOrigin()).isEqualTo("*");
        }
    }

    @Nested
    @DisplayName("CookieSettings defaults")
    class CookieSettingsDefaults {

        @Test
        @DisplayName("should have correct cookie defaults")
        void shouldHaveCorrectCookieDefaults() {
            SimpliXOAuth2Properties.CookieSettings cookie = properties.getCookie();

            assertThat(cookie.getAccessTokenName()).isEqualTo("access_token");
            assertThat(cookie.getRefreshTokenName()).isEqualTo("refresh_token");
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
        }
    }

    @Nested
    @DisplayName("enums")
    class Enums {

        @Test
        @DisplayName("should have all EmailConflictPolicy values")
        void shouldHaveAllEmailConflictPolicies() {
            assertThat(SimpliXOAuth2Properties.EmailConflictPolicy.values())
                    .containsExactlyInAnyOrder(
                            SimpliXOAuth2Properties.EmailConflictPolicy.REJECT,
                            SimpliXOAuth2Properties.EmailConflictPolicy.AUTO_LINK
                    );
        }

        @Test
        @DisplayName("should have all TokenDeliveryMethod values")
        void shouldHaveAllTokenDeliveryMethods() {
            assertThat(SimpliXOAuth2Properties.TokenDeliveryMethod.values())
                    .containsExactlyInAnyOrder(
                            SimpliXOAuth2Properties.TokenDeliveryMethod.REDIRECT,
                            SimpliXOAuth2Properties.TokenDeliveryMethod.COOKIE,
                            SimpliXOAuth2Properties.TokenDeliveryMethod.POST_MESSAGE
                    );
        }
    }
}
