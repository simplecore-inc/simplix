package dev.simplecore.simplix.web.i18n;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localized messages for a single entity
 *
 * Contains entity label and field labels in multiple locales.
 *
 * Example:
 * <pre>{@code
 * {
 *   "_label": {
 *     "en": "User Account",
 *     "ko": "사용자 계정",
 *     "ja": "ユーザーアカウント"
 *   },
 *   "userId": {
 *     "en": "User ID",
 *     "ko": "사용자 ID",
 *     "ja": "ユーザーID"
 *   },
 *   "email": {
 *     "en": "Email",
 *     "ko": "이메일",
 *     "ja": "メール"
 *   }
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Localized messages for entity and its fields")
public class EntityMessages {

    @Schema(description = "Entity name label (key: _label)")
    private LocalizedString label;

    @Schema(description = "Field labels (field name → localized string)")
    private Map<String, LocalizedString> fields = new LinkedHashMap<>();

    /**
     * Add field label
     *
     * @param fieldName Field name
     * @param localizedString Localized label
     */
    public void addField(String fieldName, LocalizedString localizedString) {
        this.fields.put(fieldName, localizedString);
    }
}
