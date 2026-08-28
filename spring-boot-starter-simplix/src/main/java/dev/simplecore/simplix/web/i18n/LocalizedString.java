package dev.simplecore.simplix.web.i18n;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Localized string containing translations for multiple locales
 *
 * This DTO dynamically supports any number of locales without hardcoding.
 * Locale codes are detected automatically from properties files.
 *
 * Example:
 * <pre>{@code
 * {
 *   "en": "User Account",
 *   "ko": "사용자 계정",
 *   "ja": "ユーザーアカウント"
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Localized string with translations for multiple locales")
public class LocalizedString {

    @Schema(
        description = "Map of locale code to translated string",
        example = "{\"en\": \"User Account\", \"ko\": \"사용자 계정\", \"ja\": \"ユーザーアカウント\"}"
    )
    private Map<String, String> translations = new HashMap<>();

    /**
     * Add translation for a specific locale
     *
     * @param locale Locale code (e.g., "en", "ko", "ja")
     * @param value Translated string
     */
    public void addTranslation(String locale, String value) {
        this.translations.put(locale, value);
    }

    /**
     * Get translation for a specific locale
     *
     * @param locale Locale code
     * @return Translated string, or null if not found
     */
    public String getTranslation(String locale) {
        return this.translations.get(locale);
    }

    /**
     * Create LocalizedString with single translation
     *
     * @param locale Locale code
     * @param value Translated string
     * @return LocalizedString instance
     */
    public static LocalizedString of(String locale, String value) {
        LocalizedString ls = new LocalizedString();
        ls.addTranslation(locale, value);
        return ls;
    }
}
