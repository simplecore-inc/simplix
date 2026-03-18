package dev.simplecore.simplix.i18n;

import dev.simplecore.simplix.core.exception.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests that every non-deprecated ErrorCode enum value has a corresponding
 * message key in the simplix_core properties files.
 *
 * Key derivation rule:
 *   ErrorCode name (e.g. GEN_NOT_FOUND) maps to "error." + name.toLowerCase().replace("_", ".")
 *   => "error.gen.not.found"
 */
@DisplayName("ErrorCode message coverage")
class ErrorCodeMessageCoverageTest {

    private static Properties coreEn;
    private static Properties coreKo;
    private static List<ErrorCode> activeErrorCodes;

    @BeforeAll
    static void setUp() throws IOException {
        coreEn = loadFromClasspath("messages/simplix_core.properties");
        coreKo = loadFromClasspath("messages/simplix_core_ko.properties");
        activeErrorCodes = resolveActiveErrorCodes();
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    @DisplayName("All active ErrorCode values should have a message key in EN default")
    void allActiveErrorCodesHaveEnMessage() {
        Set<String> enKeys = coreEn.stringPropertyNames();
        List<String> missingKeys = new ArrayList<>();

        for (ErrorCode errorCode : activeErrorCodes) {
            String expectedKey = deriveMessageKey(errorCode);
            if (!enKeys.contains(expectedKey)) {
                missingKeys.add(errorCode.name() + " -> " + expectedKey);
            }
        }

        assertThat(missingKeys)
                .as("ErrorCode values missing message keys in simplix_core.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("All active ErrorCode values should have a message key in KO")
    void allActiveErrorCodesHaveKoMessage() {
        Set<String> koKeys = coreKo.stringPropertyNames();
        List<String> missingKeys = new ArrayList<>();

        for (ErrorCode errorCode : activeErrorCodes) {
            String expectedKey = deriveMessageKey(errorCode);
            if (!koKeys.contains(expectedKey)) {
                missingKeys.add(errorCode.name() + " -> " + expectedKey);
            }
        }

        assertThat(missingKeys)
                .as("ErrorCode values missing message keys in simplix_core_ko.properties")
                .isEmpty();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("activeErrorCodeProvider")
    @DisplayName("Each active ErrorCode should have a non-blank EN message")
    void eachErrorCodeHasNonBlankEnMessage(ErrorCode errorCode) {
        String expectedKey = deriveMessageKey(errorCode);
        String value = coreEn.getProperty(expectedKey);

        assertThat(value)
                .as("EN message for ErrorCode %s (key: %s) should exist and be non-blank",
                        errorCode.name(), expectedKey)
                .isNotNull()
                .isNotBlank();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("activeErrorCodeProvider")
    @DisplayName("Each active ErrorCode should have a non-blank KO message")
    void eachErrorCodeHasNonBlankKoMessage(ErrorCode errorCode) {
        String expectedKey = deriveMessageKey(errorCode);
        String value = coreKo.getProperty(expectedKey);

        assertThat(value)
                .as("KO message for ErrorCode %s (key: %s) should exist and be non-blank",
                        errorCode.name(), expectedKey)
                .isNotNull()
                .isNotBlank();
    }

    @Test
    @DisplayName("Deprecated ErrorCode values are skipped from coverage check")
    void deprecatedCodesAreSkipped() {
        List<ErrorCode> deprecatedCodes = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (isDeprecated(code)) {
                deprecatedCodes.add(code);
            }
        }

        // Verify we actually detected deprecated codes (sanity check)
        assertThat(deprecatedCodes)
                .as("Should detect at least one @Deprecated ErrorCode")
                .isNotEmpty();

        // Verify none of the deprecated codes are in the active list
        assertThat(activeErrorCodes)
                .as("Active ErrorCode list should not contain any @Deprecated values")
                .doesNotContainAnyElementsOf(deprecatedCodes);
    }

    // =========================================================================
    // Parameterized test source
    // =========================================================================

    static Stream<ErrorCode> activeErrorCodeProvider() {
        return resolveActiveErrorCodes().stream();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Derive the expected message key from an ErrorCode.
     * Example: GEN_NOT_FOUND -> "error.gen.not.found"
     */
    private static String deriveMessageKey(ErrorCode errorCode) {
        return "error." + errorCode.getCode().toLowerCase().replace("_", ".");
    }

    /**
     * Resolve the list of ErrorCode values that are NOT annotated with @Deprecated.
     */
    private static List<ErrorCode> resolveActiveErrorCodes() {
        List<ErrorCode> active = new ArrayList<>();
        for (ErrorCode code : ErrorCode.values()) {
            if (!isDeprecated(code)) {
                active.add(code);
            }
        }
        return active;
    }

    /**
     * Check if an enum constant is annotated with @Deprecated.
     */
    private static boolean isDeprecated(ErrorCode code) {
        try {
            Field field = ErrorCode.class.getField(code.name());
            return field.isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static Properties loadFromClasspath(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream is = ErrorCodeMessageCoverageTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                fail("Properties file not found on classpath: " + path);
            }
            props.load(is);
        }
        return props;
    }
}
