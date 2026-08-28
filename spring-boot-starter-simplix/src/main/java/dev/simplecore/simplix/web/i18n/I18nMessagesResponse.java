package dev.simplecore.simplix.web.i18n;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete i18n messages response for frontend consumption
 *
 * This response contains all entity and enum messages with multi-locale support.
 * Frontend can use this to generate TypeScript type definitions for i18n.
 *
 * Example response:
 * <pre>{@code
 * {
 *   "version": "1.0.0",
 *   "supportedLocales": ["en", "ko", "ja"],
 *   "entities": {
 *     "UserAccount": {
 *       "_label": {"en": "User Account", "ko": "사용자 계정", "ja": "ユーザーアカウント"},
 *       "email": {"en": "Email", "ko": "이메일", "ja": "メール"}
 *     }
 *   },
 *   "enums": {
 *     "UserType": {
 *       "EMPLOYEE": {"en": "Employee", "ko": "직원", "ja": "社員"}
 *     }
 *   }
 * }
 * }</pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complete i18n messages for entities and enums")
public class I18nMessagesResponse {

    @Schema(description = "API version", example = "1.0.0")
    private String version;

    @Schema(description = "List of supported locale codes (auto-detected from properties files)", example = "[\"en\", \"ko\", \"ja\"]")
    private List<String> supportedLocales;

    @Schema(description = "Entity messages (entity name → messages)")
    private Map<String, EntityMessages> entities = new LinkedHashMap<>();

    @Schema(description = "Enum messages (enum name → messages)")
    private Map<String, EnumMessages> enums = new LinkedHashMap<>();

    /**
     * Add entity messages
     *
     * @param entityName Entity class simple name
     * @param messages Entity messages
     */
    public void addEntity(String entityName, EntityMessages messages) {
        this.entities.put(entityName, messages);
    }

    /**
     * Add enum messages
     *
     * @param enumName Enum class simple name
     * @param messages Enum messages
     */
    public void addEnum(String enumName, EnumMessages messages) {
        this.enums.put(enumName, messages);
    }
}
