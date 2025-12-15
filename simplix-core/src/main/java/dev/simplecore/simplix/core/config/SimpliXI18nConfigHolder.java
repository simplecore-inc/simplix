package dev.simplecore.simplix.core.config;

import java.util.Arrays;
import java.util.List;

/**
 * Static holder for I18n configuration.
 * <p>
 * This class provides static access to I18n configuration for Jackson serializers
 * that cannot be Spring-managed beans.
 * <p>
 * The configuration is initialized by {@code SimpliXI18nAutoConfiguration} during
 * application startup.
 */
public final class SimpliXI18nConfigHolder {

    private static String defaultLocale = "en";
    private static List<String> supportedLocales = Arrays.asList("en", "ko", "ja");

    private SimpliXI18nConfigHolder() {
        // Utility class
    }

    /**
     * Initialize the I18n configuration.
     * Called by SimpliXI18nAutoConfiguration during application startup.
     *
     * @param properties the I18n properties
     */
    public static void initialize(SimpliXI18nProperties properties) {
        if (properties != null) {
            defaultLocale = properties.getDefaultLocale();
            supportedLocales = properties.getSupportedLocales();
        }
    }

    /**
     * Get the default locale code.
     *
     * @return default locale code
     */
    public static String getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * Get the list of supported locales.
     *
     * @return list of supported locale codes
     */
    public static List<String> getSupportedLocales() {
        return supportedLocales;
    }
}