package dev.simplecore.simplix.auth.i18n;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests to verify message key consistency between EN and KO locales
 * for the simplix-auth module message bundle.
 */
@DisplayName("Auth module message key consistency (EN vs KO)")
class AuthMessageConsistencyTest {

    private static Properties authEn;
    private static Properties authKo;

    @BeforeAll
    static void loadProperties() throws IOException {
        authEn = loadFromClasspath("messages/simplix_auth.properties");
        authKo = loadFromClasspath("messages/simplix_auth_ko.properties");
    }

    @Test
    @DisplayName("KO should contain all keys defined in EN default")
    void koContainsAllEnKeys() {
        Set<String> enKeys = authEn.stringPropertyNames();
        Set<String> koKeys = authKo.stringPropertyNames();

        Set<String> missingInKo = new TreeSet<>(enKeys);
        missingInKo.removeAll(koKeys);

        assertThat(missingInKo)
                .as("Keys present in simplix_auth.properties but missing in simplix_auth_ko.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("KO should not contain orphan keys absent from EN default")
    void koHasNoOrphanKeys() {
        Set<String> enKeys = authEn.stringPropertyNames();
        Set<String> koKeys = authKo.stringPropertyNames();

        Set<String> orphansInKo = new TreeSet<>(koKeys);
        orphansInKo.removeAll(enKeys);

        assertThat(orphansInKo)
                .as("Orphan keys in simplix_auth_ko.properties not found in simplix_auth.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("EN and KO should have the same number of keys")
    void sameKeyCount() {
        assertThat(authKo.size())
                .as("simplix_auth KO key count should match EN key count")
                .isEqualTo(authEn.size());
    }

    @Test
    @DisplayName("All EN values should be non-blank")
    void allEnValuesNonBlank() {
        for (String key : authEn.stringPropertyNames()) {
            String value = authEn.getProperty(key);
            assertThat(value)
                    .as("EN value for key '%s' should not be blank", key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("All KO values should be non-blank")
    void allKoValuesNonBlank() {
        for (String key : authKo.stringPropertyNames()) {
            String value = authKo.getProperty(key);
            assertThat(value)
                    .as("KO value for key '%s' should not be blank", key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("KO translations should differ from EN values (actual translation check)")
    void koTranslationsDifferFromEn() {
        Set<String> enKeys = authEn.stringPropertyNames();
        int identicalCount = 0;

        for (String key : enKeys) {
            String enValue = authEn.getProperty(key);
            String koValue = authKo.getProperty(key);
            if (enValue != null && enValue.equals(koValue)) {
                identicalCount++;
            }
        }

        // Allow some keys to be identical (e.g., technical terms, format strings)
        // but most should be translated
        assertThat(identicalCount)
                .as("Most KO values should differ from EN (identical count should be low)")
                .isLessThan(enKeys.size() / 2);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static Properties loadFromClasspath(String path) throws IOException {
        Properties props = new Properties();
        try (InputStream is = AuthMessageConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                fail("Properties file not found on classpath: " + path);
            }
            props.load(is);
        }
        return props;
    }
}
