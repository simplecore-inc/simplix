package dev.simplecore.simplix.web.i18n;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localized messages for a single enum
 *
 * Contains enum value labels in multiple locales.
 *
 * Example:
 * <pre>{@code
 * {
 *   "EMPLOYEE": {
 *     "en": "Employee",
 *     "ko": "직원",
 *     "ja": "社員"
 *   },
 *   "GUEST": {
 *     "en": "Guest",
 *     "ko": "게스트",
 *     "ja": "ゲスト"
 *   }
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Localized messages for enum values")
public class EnumMessages {

    @Schema(description = "Enum value labels (value name → localized string)")
    private Map<String, LocalizedString> values = new LinkedHashMap<>();

    /**
     * Add enum value label
     *
     * @param valueName Enum value name
     * @param localizedString Localized label
     */
    public void addValue(String valueName, LocalizedString localizedString) {
        this.values.put(valueName, localizedString);
    }
}
