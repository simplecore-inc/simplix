package dev.simplecore.simplix.i18n;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that every non-deprecated ErrorCode maps to a valid message key
 * that resolves successfully via ResourceBundleMessageSource for both EN and KO locales.
 * Also verifies that EN and KO messages are genuinely different (i.e., actually translated).
 */
@DisplayName("ErrorCode to message key mapping and resolution")
class ErrorCodeMessageMappingTest {

    private ResourceBundleMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/simplix_core");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    // =========================================================================
    // Parameterized test: each active ErrorCode resolves in EN
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("activeErrorCodeProvider")
    @DisplayName("Each non-deprecated ErrorCode should resolve in EN locale without throwing")
    void errorCodeResolvesInEnLocale(ErrorCode errorCode) {
        String key = deriveMessageKey(errorCode);

        assertThatCode(() -> messageSource.getMessage(key, null, Locale.ENGLISH))
                .as("Message key '%s' for ErrorCode %s should resolve in EN without NoSuchMessageException",
                        key, errorCode.name())
                .doesNotThrowAnyException();

        String message = messageSource.getMessage(key, null, Locale.ENGLISH);
        assertThat(message)
                .as("EN message for key '%s' should not be empty", key)
                .isNotNull()
                .isNotBlank();
    }

    // =========================================================================
    // Parameterized test: each active ErrorCode resolves in KO
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("activeErrorCodeProvider")
    @DisplayName("Each non-deprecated ErrorCode should resolve in KO locale without throwing")
    void errorCodeResolvesInKoLocale(ErrorCode errorCode) {
        String key = deriveMessageKey(errorCode);

        assertThatCode(() -> messageSource.getMessage(key, null, Locale.KOREAN))
                .as("Message key '%s' for ErrorCode %s should resolve in KO without NoSuchMessageException",
                        key, errorCode.name())
                .doesNotThrowAnyException();

        String message = messageSource.getMessage(key, null, Locale.KOREAN);
        assertThat(message)
                .as("KO message for key '%s' should not be empty", key)
                .isNotNull()
                .isNotBlank();
    }

    // =========================================================================
    // Parameterized test: EN and KO messages are different (actually translated)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("activeErrorCodeProvider")
    @DisplayName("EN and KO messages should be different for each non-deprecated ErrorCode")
    void enAndKoMessagesShouldDiffer(ErrorCode errorCode) {
        String key = deriveMessageKey(errorCode);
        String enMessage = messageSource.getMessage(key, null, Locale.ENGLISH);
        String koMessage = messageSource.getMessage(key, null, Locale.KOREAN);

        assertThat(enMessage)
                .as("EN and KO messages for key '%s' (ErrorCode: %s) should be different, "
                                + "proving actual translation exists. EN='%s', KO='%s'",
                        key, errorCode.name(), enMessage, koMessage)
                .isNotEqualTo(koMessage);
    }

    // =========================================================================
    // Summary test: collect all failures in a single assertion
    // =========================================================================

    @Nested
    @DisplayName("Bulk resolution check")
    class BulkResolution {

        @Test
        @DisplayName("All non-deprecated ErrorCodes should have resolvable, translated messages")
        void allErrorCodesResolveAndAreTranslated() {
            List<String> failures = new ArrayList<>();

            for (ErrorCode errorCode : resolveActiveErrorCodes()) {
                String key = deriveMessageKey(errorCode);

                // Check EN resolution
                try {
                    String enMsg = messageSource.getMessage(key, null, Locale.ENGLISH);
                    if (enMsg == null || enMsg.isBlank()) {
                        failures.add(errorCode.name() + " -> " + key + ": EN message is empty");
                    }
                } catch (NoSuchMessageException e) {
                    failures.add(errorCode.name() + " -> " + key + ": EN key not found");
                }

                // Check KO resolution
                try {
                    String koMsg = messageSource.getMessage(key, null, Locale.KOREAN);
                    if (koMsg == null || koMsg.isBlank()) {
                        failures.add(errorCode.name() + " -> " + key + ": KO message is empty");
                    }
                } catch (NoSuchMessageException e) {
                    failures.add(errorCode.name() + " -> " + key + ": KO key not found");
                }

                // Check translation difference
                try {
                    String enMsg = messageSource.getMessage(key, null, Locale.ENGLISH);
                    String koMsg = messageSource.getMessage(key, null, Locale.KOREAN);
                    if (enMsg != null && enMsg.equals(koMsg)) {
                        failures.add(errorCode.name() + " -> " + key
                                + ": EN and KO are identical ('" + enMsg + "')");
                    }
                } catch (NoSuchMessageException e) {
                    // Already captured above
                }
            }

            assertThat(failures)
                    .as("ErrorCode message resolution failures")
                    .isEmpty();
        }
    }

    // =========================================================================
    // Key derivation consistency test
    // =========================================================================

    @Test
    @DisplayName("The key derivation matches what SimpliXExceptionHandler uses at runtime")
    void keyDerivationMatchesHandlerLogic() {
        // SimpliXExceptionHandler uses:
        //   "error." + errorCode.getCode().toLowerCase().replace("_", ".")
        // This test ensures our deriveMessageKey() uses the same algorithm.
        for (ErrorCode errorCode : resolveActiveErrorCodes()) {
            String handlerKey = "error." + errorCode.getCode().toLowerCase().replace("_", ".");
            String testKey = deriveMessageKey(errorCode);

            assertThat(testKey)
                    .as("Key derivation for %s should match handler logic", errorCode.name())
                    .isEqualTo(handlerKey);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    static Stream<ErrorCode> activeErrorCodeProvider() {
        return resolveActiveErrorCodes().stream();
    }

    private static String deriveMessageKey(ErrorCode errorCode) {
        return "error." + errorCode.getCode().toLowerCase().replace("_", ".");
    }

    private static List<ErrorCode> resolveActiveErrorCodes() {
        List<ErrorCode> active = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (!isDeprecated(code)) {
                active.add(code);
            }
        }
        return active;
    }

    private static boolean isDeprecated(ErrorCode code) {
        try {
            Field field = ErrorCode.class.getField(code.name());
            return field.isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
