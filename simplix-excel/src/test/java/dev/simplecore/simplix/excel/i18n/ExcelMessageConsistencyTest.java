package dev.simplecore.simplix.excel.i18n;

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
 * for the simplix-excel module message bundle.
 */
@DisplayName("Excel module message key consistency (EN vs KO)")
class ExcelMessageConsistencyTest {

    private static Properties excelEn;
    private static Properties excelKo;

    @BeforeAll
    static void loadProperties() throws IOException {
        excelEn = loadFromClasspath("messages/simplix_excel.properties");
        excelKo = loadFromClasspath("messages/simplix_excel_ko.properties");
    }

    @Test
    @DisplayName("KO should contain all keys defined in EN default")
    void koContainsAllEnKeys() {
        Set<String> enKeys = excelEn.stringPropertyNames();
        Set<String> koKeys = excelKo.stringPropertyNames();

        Set<String> missingInKo = new TreeSet<>(enKeys);
        missingInKo.removeAll(koKeys);

        assertThat(missingInKo)
                .as("Keys present in simplix_excel.properties but missing in simplix_excel_ko.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("KO should not contain orphan keys absent from EN default")
    void koHasNoOrphanKeys() {
        Set<String> enKeys = excelEn.stringPropertyNames();
        Set<String> koKeys = excelKo.stringPropertyNames();

        Set<String> orphansInKo = new TreeSet<>(koKeys);
        orphansInKo.removeAll(enKeys);

        assertThat(orphansInKo)
                .as("Orphan keys in simplix_excel_ko.properties not found in simplix_excel.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("EN and KO should have the same number of keys")
    void sameKeyCount() {
        assertThat(excelKo.size())
                .as("simplix_excel KO key count should match EN key count")
                .isEqualTo(excelEn.size());
    }

    @Test
    @DisplayName("All EN values should be non-blank")
    void allEnValuesNonBlank() {
        for (String key : excelEn.stringPropertyNames()) {
            String value = excelEn.getProperty(key);
            assertThat(value)
                    .as("EN value for key '%s' should not be blank", key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("All KO values should be non-blank")
    void allKoValuesNonBlank() {
        for (String key : excelKo.stringPropertyNames()) {
            String value = excelKo.getProperty(key);
            assertThat(value)
                    .as("KO value for key '%s' should not be blank", key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("KO translations should differ from EN values (actual translation check)")
    void koTranslationsDifferFromEn() {
        Set<String> enKeys = excelEn.stringPropertyNames();
        int identicalCount = 0;

        for (String key : enKeys) {
            String enValue = excelEn.getProperty(key);
            String koValue = excelKo.getProperty(key);
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
        try (InputStream is = ExcelMessageConsistencyTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                fail("Properties file not found on classpath: " + path);
            }
            props.load(is);
        }
        return props;
    }
}
