package dev.simplecore.simplix.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the MessageSource configuration loads all expected message bundles,
 * resolves messages across locales, and handles fallback correctly.
 * Mimics the behavior of SimpliXMessageSourceAutoConfiguration.
 */
@DisplayName("MessageSource configuration and bundle loading")
class MessageSourceConfigurationTest {

    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        // Mimic what SimpliXMessageSourceAutoConfiguration.createLibraryMessageSource() does
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/simplix_core", "messages/simplix_validation");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // =========================================================================
    // Core error messages resolve across locales
    // =========================================================================

    @Nested
    @DisplayName("Core error message resolution (error.gen.not.found)")
    class CoreErrorMessages {

        @Test
        @DisplayName("EN locale: resolves to English message")
        void resolveInEn() {
            String message = messageSource.getMessage("error.gen.not.found", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("KO locale: resolves to Korean message")
        void resolveInKo() {
            String message = messageSource.getMessage("error.gen.not.found", null, Locale.KOREAN);
            assertThat(message)
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("Resource not found");
        }

        @Test
        @DisplayName("JA locale: resolves to Japanese message")
        void resolveInJa() {
            String message = messageSource.getMessage("error.gen.not.found", null, Locale.JAPANESE);
            assertThat(message)
                    .isNotNull()
                    .isNotBlank()
                    // JA file has the key, so it should not equal EN
                    .isNotEqualTo("Resource not found");
        }

        @Test
        @DisplayName("EN, KO, JA messages are all different from each other")
        void allLocalesDiffer() {
            String en = messageSource.getMessage("error.gen.not.found", null, Locale.ENGLISH);
            String ko = messageSource.getMessage("error.gen.not.found", null, Locale.KOREAN);
            String ja = messageSource.getMessage("error.gen.not.found", null, Locale.JAPANESE);

            assertThat(en).isNotEqualTo(ko);
            assertThat(en).isNotEqualTo(ja);
            assertThat(ko).isNotEqualTo(ja);
        }
    }

    // =========================================================================
    // Validation messages resolve across locales
    // =========================================================================

    @Nested
    @DisplayName("Validation message resolution (validation.notnull)")
    class ValidationMessages {

        @Test
        @DisplayName("EN locale: resolves to English message")
        void resolveInEn() {
            String message = messageSource.getMessage("validation.notnull", null, Locale.ENGLISH);
            assertThat(message).isEqualTo("This field is required");
        }

        @Test
        @DisplayName("KO locale: resolves to Korean message")
        void resolveInKo() {
            String message = messageSource.getMessage("validation.notnull", null, Locale.KOREAN);
            assertThat(message)
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("This field is required");
        }

        @Test
        @DisplayName("JA locale: resolves to Japanese message")
        void resolveInJa() {
            String message = messageSource.getMessage("validation.notnull", null, Locale.JAPANESE);
            assertThat(message)
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("This field is required");
        }

        @Test
        @DisplayName("EN, KO, JA messages are all different from each other")
        void allLocalesDiffer() {
            String en = messageSource.getMessage("validation.notnull", null, Locale.ENGLISH);
            String ko = messageSource.getMessage("validation.notnull", null, Locale.KOREAN);
            String ja = messageSource.getMessage("validation.notnull", null, Locale.JAPANESE);

            assertThat(en).isNotEqualTo(ko);
            assertThat(en).isNotEqualTo(ja);
            assertThat(ko).isNotEqualTo(ja);
        }
    }

    // =========================================================================
    // Locale fallback behavior
    // =========================================================================

    @Nested
    @DisplayName("Locale fallback behavior")
    class FallbackBehavior {

        @Test
        @DisplayName("Unsupported locale falls back to EN default (core messages)")
        void unsupportedLocaleFallsBackToEn() {
            // French is not supported; should fall back to default (EN)
            String message = messageSource.getMessage("error.gen.not.found", null, Locale.FRENCH);
            assertThat(message)
                    .as("French locale should fall back to EN default")
                    .isEqualTo("Resource not found");
        }

        @Test
        @DisplayName("Unsupported locale falls back to EN default (validation messages)")
        void validationFallsBackToEn() {
            String message = messageSource.getMessage("validation.notnull", null, Locale.FRENCH);
            assertThat(message)
                    .as("French locale should fall back to EN default for validation")
                    .isEqualTo("This field is required");
        }

        @Test
        @DisplayName("JA locale resolves Japanese message from simplix_core_ja")
        void jaResolvesJapaneseMessage() {
            // error.authenticationFailed exists in EN, KO, and JA
            String message = messageSource.getMessage(
                    "error.authenticationFailed", null, Locale.JAPANESE);

            // Should get the JA translation
            assertThat(message)
                    .as("JA locale should resolve Japanese message")
                    .isNotBlank()
                    .isNotEqualTo("Authentication required");
        }
    }

    // =========================================================================
    // Unknown key handling
    // =========================================================================

    @Nested
    @DisplayName("Unknown key handling")
    class UnknownKeys {

        @Test
        @DisplayName("Unknown key throws NoSuchMessageException when no default provided")
        void throwsForUnknownKey() {
            assertThatThrownBy(() ->
                    messageSource.getMessage("this.key.absolutely.does.not.exist", null, Locale.ENGLISH))
                    .isInstanceOf(NoSuchMessageException.class);
        }

        @Test
        @DisplayName("Unknown key returns default message when default is provided")
        void returnsDefaultForUnknownKey() {
            String message = messageSource.getMessage(
                    "this.key.absolutely.does.not.exist", null, "My fallback", Locale.ENGLISH);
            assertThat(message).isEqualTo("My fallback");
        }

        @Test
        @DisplayName("Unknown key returns default regardless of locale")
        void returnsDefaultRegardlessOfLocale() {
            String enDefault = messageSource.getMessage(
                    "this.does.not.exist", null, "fallback", Locale.ENGLISH);
            String koDefault = messageSource.getMessage(
                    "this.does.not.exist", null, "fallback", Locale.KOREAN);
            String jaDefault = messageSource.getMessage(
                    "this.does.not.exist", null, "fallback", Locale.JAPANESE);

            assertThat(enDefault).isEqualTo("fallback");
            assertThat(koDefault).isEqualTo("fallback");
            assertThat(jaDefault).isEqualTo("fallback");
        }
    }

    // =========================================================================
    // Cross-bundle resolution
    // =========================================================================

    @Nested
    @DisplayName("Cross-bundle resolution")
    class CrossBundle {

        @Test
        @DisplayName("Both core and validation messages are accessible from the same MessageSource")
        void bothBundlesAccessible() {
            // Core message
            String coreMsg = messageSource.getMessage("error.gen.bad.request", null, Locale.ENGLISH);
            assertThat(coreMsg).isEqualTo("Bad request");

            // Validation message
            String validMsg = messageSource.getMessage("validation.email", null, Locale.ENGLISH);
            assertThat(validMsg).isEqualTo("Invalid email format");
        }

        @Test
        @DisplayName("Multiple core error keys resolve correctly in all supported locales")
        void multipleCoreKeysResolve() {
            String[] keys = {
                    "error.gen.internal.server.error",
                    "error.gen.bad.request",
                    "error.gen.not.found",
                    "error.auth.authentication.required",
                    "error.authz.insufficient.permissions",
                    "error.val.validation.failed"
            };

            for (String key : keys) {
                String en = messageSource.getMessage(key, null, Locale.ENGLISH);
                String ko = messageSource.getMessage(key, null, Locale.KOREAN);

                assertThat(en)
                        .as("EN message for '%s'", key)
                        .isNotNull()
                        .isNotBlank();
                assertThat(ko)
                        .as("KO message for '%s'", key)
                        .isNotNull()
                        .isNotBlank();
                assertThat(en)
                        .as("EN and KO should differ for '%s'", key)
                        .isNotEqualTo(ko);
            }
        }
    }

    // =========================================================================
    // Parameter substitution
    // =========================================================================

    @Nested
    @DisplayName("Parameter substitution")
    class ParameterSubstitution {

        @Test
        @DisplayName("EN: error.notFound.detail substitutes {0} parameter correctly")
        void enParameterSubstitution() {
            String message = messageSource.getMessage(
                    "error.notFound.detail", new Object[]{"/api/users/42"}, Locale.ENGLISH);

            assertThat(message).contains("/api/users/42");
        }

        @Test
        @DisplayName("KO: error.notFound.detail substitutes {0} parameter correctly")
        void koParameterSubstitution() {
            String message = messageSource.getMessage(
                    "error.notFound.detail", new Object[]{"/api/users/42"}, Locale.KOREAN);

            assertThat(message).contains("/api/users/42");
            // KO message template should differ from EN
            assertThat(message).isNotEqualTo(
                    messageSource.getMessage("error.notFound.detail", new Object[]{"/api/users/42"}, Locale.ENGLISH));
        }
    }
}
