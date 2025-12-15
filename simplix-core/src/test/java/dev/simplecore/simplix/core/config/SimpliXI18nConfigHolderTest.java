package dev.simplecore.simplix.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SimpliXI18nConfigHolder}.
 */
class SimpliXI18nConfigHolderTest {

    @BeforeEach
    void setUp() {
        // Reset to default values before each test
        SimpliXI18nProperties defaultProps = new SimpliXI18nProperties();
        SimpliXI18nConfigHolder.initialize(defaultProps);
    }

    @Test
    @DisplayName("should have default values when not initialized")
    void shouldHaveDefaultValues() {
        // given - default initialization in setUp

        // then
        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo("en");
        assertThat(SimpliXI18nConfigHolder.getSupportedLocales())
            .containsExactly("en", "ko", "ja");
    }

    @Test
    @DisplayName("should update configuration when initialized with custom properties")
    void shouldUpdateConfigurationWhenInitialized() {
        // given
        SimpliXI18nProperties properties = new SimpliXI18nProperties();
        properties.setDefaultLocale("ko");
        properties.setSupportedLocales(Arrays.asList("ko", "en", "zh", "ja"));

        // when
        SimpliXI18nConfigHolder.initialize(properties);

        // then
        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo("ko");
        assertThat(SimpliXI18nConfigHolder.getSupportedLocales())
            .containsExactly("ko", "en", "zh", "ja");
    }

    @Test
    @DisplayName("should not change configuration when initialized with null")
    void shouldNotChangeWhenInitializedWithNull() {
        // given - set custom values first
        SimpliXI18nProperties customProps = new SimpliXI18nProperties();
        customProps.setDefaultLocale("fr");
        customProps.setSupportedLocales(Arrays.asList("fr", "de"));
        SimpliXI18nConfigHolder.initialize(customProps);

        String previousDefaultLocale = SimpliXI18nConfigHolder.getDefaultLocale();
        List<String> previousSupportedLocales = SimpliXI18nConfigHolder.getSupportedLocales();

        // when
        SimpliXI18nConfigHolder.initialize(null);

        // then - values should remain unchanged
        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo(previousDefaultLocale);
        assertThat(SimpliXI18nConfigHolder.getSupportedLocales()).isEqualTo(previousSupportedLocales);
    }

    @Test
    @DisplayName("should allow re-initialization with different values")
    void shouldAllowReInitialization() {
        // given
        SimpliXI18nProperties props1 = new SimpliXI18nProperties();
        props1.setDefaultLocale("en");
        props1.setSupportedLocales(Arrays.asList("en", "ko"));
        SimpliXI18nConfigHolder.initialize(props1);

        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo("en");

        // when
        SimpliXI18nProperties props2 = new SimpliXI18nProperties();
        props2.setDefaultLocale("ja");
        props2.setSupportedLocales(Arrays.asList("ja", "zh"));
        SimpliXI18nConfigHolder.initialize(props2);

        // then
        assertThat(SimpliXI18nConfigHolder.getDefaultLocale()).isEqualTo("ja");
        assertThat(SimpliXI18nConfigHolder.getSupportedLocales())
            .containsExactly("ja", "zh");
    }
}
