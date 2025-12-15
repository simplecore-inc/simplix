package dev.simplecore.simplix.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for SimpliX I18n translation.
 * <p>
 * Allows configuration via application.yml/properties:
 * <pre>{@code
 * simplix:
 *   i18n:
 *     default-locale: en
 *     supported-locales:
 *       - en
 *       - ko
 *       - ja
 * }</pre>
 * <p>
 * The supported-locales list is used for fallback when neither current locale
 * nor default locale is found in the translation Map. The serializer will try
 * each locale in order and return the first available translation.
 */
@Data
@ConfigurationProperties(prefix = "simplix.i18n")
public class SimpliXI18nProperties {

    /**
     * Default locale code to use when the current locale is not found in the translation Map.
     */
    private String defaultLocale = "en";

    /**
     * List of supported locales for the application.
     * Used for fallback when neither current locale nor default locale is found.
     * The serializer will try each locale in order and return the first available translation.
     */
    private List<String> supportedLocales = Arrays.asList("en", "ko", "ja");
}