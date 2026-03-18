package dev.simplecore.simplix.auth.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that all message keys used by SimpliXAuthTokenController,
 * SimpliXAuthenticationEntryPoint, and SimpliXAccessDeniedHandler actually
 * resolve from the simplix_auth message bundle in both EN and KO locales.
 */
@DisplayName("Auth module message usage verification")
class AuthMessageUsageTest {

    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/simplix_auth");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // =========================================================================
    // Token lifecycle message keys (used in SimpliXAuthTokenController)
    // =========================================================================

    @Nested
    @DisplayName("Token lifecycle messages (SimpliXAuthTokenController)")
    class TokenLifecycleMessages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "auth.basic.header.missing",
                "auth.credentials.invalid",
                "token.refresh.header.missing",
                "token.refresh.header.missing.detail",
                "token.refresh.invalid",
                "token.refresh.invalid.detail",
                "token.revoke.success",
                "token.revoke.missing",
                "token.revoke.missing.detail"
        })
        @DisplayName("Token controller message keys resolve in both locales")
        void tokenControllerKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }

        @Test
        @DisplayName("auth.basic.header.missing EN message matches expected text")
        void authBasicHeaderMissingEn() {
            String message = messageSource.getMessage("auth.basic.header.missing", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Login information is required");
        }

        @Test
        @DisplayName("auth.credentials.invalid EN message matches expected text")
        void authCredentialsInvalidEn() {
            String message = messageSource.getMessage("auth.credentials.invalid", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Invalid username or password");
        }

        @Test
        @DisplayName("token.revoke.success EN message matches expected text")
        void tokenRevokeSuccessEn() {
            String message = messageSource.getMessage("token.revoke.success", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Successfully logged out");
        }
    }

    // =========================================================================
    // Token validation message keys (used in token processing)
    // =========================================================================

    @Nested
    @DisplayName("Token validation messages")
    class TokenValidationMessages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "token.expired",
                "token.expired.detail",
                "token.ip.mismatch",
                "token.ip.mismatch.detail",
                "token.useragent.mismatch",
                "token.useragent.mismatch.detail",
                "token.validation.failed",
                "token.revoked",
                "token.revoked.detail"
        })
        @DisplayName("Token validation message keys resolve in both locales")
        void tokenValidationKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }

        @Test
        @DisplayName("token.expired EN message matches expected text")
        void tokenExpiredEn() {
            String message = messageSource.getMessage("token.expired", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Authentication has expired");
        }

        @Test
        @DisplayName("token.ip.mismatch EN message matches expected text")
        void tokenIpMismatchEn() {
            String message = messageSource.getMessage("token.ip.mismatch", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Your connection environment has changed");
        }

        @Test
        @DisplayName("token.useragent.mismatch EN message matches expected text")
        void tokenUserAgentMismatchEn() {
            String message = messageSource.getMessage("token.useragent.mismatch", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Your connection environment has changed");
        }
    }

    // =========================================================================
    // User authentication message keys
    // =========================================================================

    @Nested
    @DisplayName("User authentication messages")
    class UserAuthMessages {

        @Test
        @DisplayName("user.auth.not.found resolves in EN")
        void userAuthNotFoundEn() {
            String message = messageSource.getMessage("user.auth.not.found", null, Locale.ENGLISH);
            assertThat(message)
                    .isNotNull()
                    .isNotBlank()
                    .isEqualTo("No authenticated user found");
        }

        @Test
        @DisplayName("user.auth.not.found resolves in KO and differs from EN")
        void userAuthNotFoundKo() {
            String en = messageSource.getMessage("user.auth.not.found", null, Locale.ENGLISH);
            String ko = messageSource.getMessage("user.auth.not.found", null, Locale.KOREAN);
            assertThat(ko)
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo(en);
        }
    }

    // =========================================================================
    // JWE key configuration message keys
    // =========================================================================

    @Nested
    @DisplayName("JWE key configuration messages")
    class JweKeyMessages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "jwe.key.load.failed",
                "jwe.key.not.configured"
        })
        @DisplayName("JWE key message keys resolve in both locales")
        void jweKeyKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }

        @Test
        @DisplayName("jwe.key.load.failed supports parameter substitution")
        void jweKeyLoadFailedParameterSubstitution() {
            String message = messageSource.getMessage(
                    "jwe.key.load.failed", new Object[]{"classpath:keys/jwe-key.json"}, Locale.ENGLISH);
            assertThat(message).contains("classpath:keys/jwe-key.json");
        }

        @Test
        @DisplayName("jwe.key.not.configured EN message mentions configuration")
        void jweKeyNotConfiguredEn() {
            String message = messageSource.getMessage("jwe.key.not.configured", null, Locale.ENGLISH);
            assertThat(message)
                    .isNotBlank()
                    .contains("simplix.auth.jwt.secret-key");
        }
    }

    // =========================================================================
    // OpenAPI security description message keys
    // =========================================================================

    @Nested
    @DisplayName("OpenAPI security description messages")
    class OpenApiMessages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "openapi.security.bearer.description",
                "openapi.security.basic.description"
        })
        @DisplayName("OpenAPI message keys resolve in both locales")
        void openApiKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }

        @Test
        @DisplayName("openapi.security.bearer.description EN message contains Bearer")
        void bearerDescriptionContainsBearer() {
            String message = messageSource.getMessage(
                    "openapi.security.bearer.description", null, Locale.ENGLISH);
            assertThat(message).containsIgnoringCase("Bearer");
        }

        @Test
        @DisplayName("openapi.security.basic.description EN message contains Basic")
        void basicDescriptionContainsBasic() {
            String message = messageSource.getMessage(
                    "openapi.security.basic.description", null, Locale.ENGLISH);
            assertThat(message).containsIgnoringCase("Basic");
        }
    }

    // =========================================================================
    // OAuth2 error message keys
    // =========================================================================

    @Nested
    @DisplayName("OAuth2 error messages")
    class OAuth2Messages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "oauth2.error.email_already_exists",
                "oauth2.error.social_already_linked",
                "oauth2.error.provider_already_linked",
                "oauth2.error.last_login_method",
                "oauth2.error.provider_error",
                "oauth2.error.linking_failed",
                "oauth2.error.user_not_found"
        })
        @DisplayName("OAuth2 error message keys resolve in both locales")
        void oauth2ErrorKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }

        @Test
        @DisplayName("All OAuth2 error messages are non-empty in EN")
        void allOAuth2EnMessagesNonEmpty() {
            String[] keys = {
                    "oauth2.error.email_already_exists",
                    "oauth2.error.social_already_linked",
                    "oauth2.error.provider_already_linked",
                    "oauth2.error.last_login_method",
                    "oauth2.error.provider_error",
                    "oauth2.error.linking_failed",
                    "oauth2.error.user_not_found"
            };

            for (String key : keys) {
                String message = messageSource.getMessage(key, null, Locale.ENGLISH);
                assertThat(message)
                        .as("EN message for '%s' should be non-empty", key)
                        .isNotNull()
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("All OAuth2 error messages are translated differently in KO")
        void allOAuth2KoMessagesTranslated() {
            String[] keys = {
                    "oauth2.error.email_already_exists",
                    "oauth2.error.social_already_linked",
                    "oauth2.error.provider_already_linked",
                    "oauth2.error.last_login_method",
                    "oauth2.error.provider_error",
                    "oauth2.error.linking_failed",
                    "oauth2.error.user_not_found"
            };

            for (String key : keys) {
                String en = messageSource.getMessage(key, null, Locale.ENGLISH);
                String ko = messageSource.getMessage(key, null, Locale.KOREAN);
                assertThat(en)
                        .as("EN and KO should differ for '%s'", key)
                        .isNotEqualTo(ko);
            }
        }
    }

    // =========================================================================
    // Login page message keys
    // =========================================================================

    @Nested
    @DisplayName("Login page messages")
    class LoginMessages {

        @ParameterizedTest(name = "Key ''{0}'' resolves in EN and KO")
        @ValueSource(strings = {
                "login.title",
                "login.header",
                "login.error",
                "login.logout",
                "login.session.expired",
                "login.username",
                "login.password",
                "login.remember-me",
                "login.button.submit"
        })
        @DisplayName("Login page message keys resolve in both locales")
        void loginKeysResolve(String key) {
            assertKeyResolvesInBothLocales(key);
        }
    }

    // =========================================================================
    // Bulk verification: all keys used in auth source code
    // =========================================================================

    @Nested
    @DisplayName("Comprehensive key resolution check")
    class ComprehensiveCheck {

        /**
         * All message keys referenced in SimpliXAuthTokenController,
         * SimpliXAuthenticationEntryPoint, and SimpliXAccessDeniedHandler source code.
         */
        private static final String[] ALL_REFERENCED_KEYS = {
                // SimpliXAuthTokenController
                "auth.basic.header.missing",
                "auth.credentials.invalid",
                "token.refresh.header.missing",
                "token.refresh.header.missing.detail",
                "token.refresh.invalid",
                "token.refresh.invalid.detail",
                "token.revoke.success",
                "token.revoke.missing",
                "token.revoke.missing.detail",
                // Token validation
                "token.expired",
                "token.expired.detail",
                "token.ip.mismatch",
                "token.ip.mismatch.detail",
                "token.useragent.mismatch",
                "token.useragent.mismatch.detail",
                "token.validation.failed",
                "token.revoked",
                "token.revoked.detail",
                // User
                "user.auth.not.found",
                // JWE
                "jwe.key.load.failed",
                "jwe.key.not.configured",
                // OpenAPI
                "openapi.security.bearer.description",
                "openapi.security.basic.description",
                // OAuth2
                "oauth2.error.email_already_exists",
                "oauth2.error.social_already_linked",
                "oauth2.error.provider_already_linked",
                "oauth2.error.last_login_method",
                "oauth2.error.provider_error",
                "oauth2.error.linking_failed",
                "oauth2.error.user_not_found"
        };

        @Test
        @DisplayName("All referenced keys resolve without error in EN")
        void allReferencedKeysResolveInEn() {
            List<String> failures = new ArrayList<>();

            for (String key : ALL_REFERENCED_KEYS) {
                try {
                    String msg = messageSource.getMessage(key, null, Locale.ENGLISH);
                    if (msg == null || msg.isBlank()) {
                        failures.add(key + ": EN message is empty");
                    }
                } catch (NoSuchMessageException e) {
                    failures.add(key + ": EN key not found");
                }
            }

            assertThat(failures)
                    .as("All auth message keys should resolve in EN")
                    .isEmpty();
        }

        @Test
        @DisplayName("All referenced keys resolve without error in KO")
        void allReferencedKeysResolveInKo() {
            List<String> failures = new ArrayList<>();

            for (String key : ALL_REFERENCED_KEYS) {
                try {
                    String msg = messageSource.getMessage(key, null, Locale.KOREAN);
                    if (msg == null || msg.isBlank()) {
                        failures.add(key + ": KO message is empty");
                    }
                } catch (NoSuchMessageException e) {
                    failures.add(key + ": KO key not found");
                }
            }

            assertThat(failures)
                    .as("All auth message keys should resolve in KO")
                    .isEmpty();
        }

        @Test
        @DisplayName("All referenced keys have different EN and KO messages (actually translated)")
        void allReferencedKeysHaveTranslations() {
            List<String> untranslated = new ArrayList<>();

            for (String key : ALL_REFERENCED_KEYS) {
                try {
                    String en = messageSource.getMessage(key, null, Locale.ENGLISH);
                    String ko = messageSource.getMessage(key, null, Locale.KOREAN);
                    if (en != null && en.equals(ko)) {
                        untranslated.add(key + ": EN='" + en + "' equals KO");
                    }
                } catch (NoSuchMessageException e) {
                    // Already caught in resolution tests above
                }
            }

            assertThat(untranslated)
                    .as("All auth message keys should have different EN and KO translations")
                    .isEmpty();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Asserts that the given key resolves without error in both EN and KO locales,
     * produces non-empty messages, and the two translations differ.
     */
    private void assertKeyResolvesInBothLocales(String key) {
        // EN resolution
        assertThatCode(() -> messageSource.getMessage(key, null, Locale.ENGLISH))
                .as("Key '%s' should resolve in EN without NoSuchMessageException", key)
                .doesNotThrowAnyException();

        String enMessage = messageSource.getMessage(key, null, Locale.ENGLISH);
        assertThat(enMessage)
                .as("EN message for '%s' should not be empty", key)
                .isNotNull()
                .isNotBlank();

        // KO resolution
        assertThatCode(() -> messageSource.getMessage(key, null, Locale.KOREAN))
                .as("Key '%s' should resolve in KO without NoSuchMessageException", key)
                .doesNotThrowAnyException();

        String koMessage = messageSource.getMessage(key, null, Locale.KOREAN);
        assertThat(koMessage)
                .as("KO message for '%s' should not be empty", key)
                .isNotNull()
                .isNotBlank();

        // Translation check
        assertThat(enMessage)
                .as("EN and KO messages should differ for key '%s' (EN='%s', KO='%s')",
                        key, enMessage, koMessage)
                .isNotEqualTo(koMessage);
    }
}
