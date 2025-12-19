package dev.simplecore.simplix.core.jackson.annotation;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.simplecore.simplix.core.jackson.SimpliXI18nTransSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic i18n translation during JSON serialization.
 * Extracts the appropriate translation from a source i18n Map field based on the current locale.
 * <p>
 * Supports two modes:
 * <ol>
 *   <li><b>Simple mode</b>: Translates the annotated String field from its corresponding i18n Map</li>
 *   <li><b>Nested mode</b>: Translates a nested object's field using dot notation paths</li>
 * </ol>
 * <p>
 * Usage example (Simple mode):
 * <pre>{@code
 * public class UserRoleInfo {
 *     @I18nTrans(source = "roleNameI18n")
 *     private String roleName;
 *
 *     @JsonIgnore
 *     private Map<String, String> roleNameI18n;  // {"en": "Admin", "ko": "관리자", "ja": "管理者"}
 * }
 * }</pre>
 * <p>
 * Usage example (Nested mode):
 * <pre>{@code
 * public class TagEntryDTO {
 *     @I18nTrans(source = "tagGroup.nameI18n", target = "tagGroup.name")
 *     private CmsTagGroup tagGroup;  // tagGroup.name will be translated based on tagGroup.nameI18n
 * }
 * }</pre>
 * <p>
 * The serializer will:
 * <ul>
 *   <li>Get current locale from {@link org.springframework.context.i18n.LocaleContextHolder}</li>
 *   <li>Extract translation from the source Map field (supports dot notation for nested objects)</li>
 *   <li>Set the translated value to the target field (or annotated field if target is empty)</li>
 *   <li>Fallback to defaultLocale if current locale is not found</li>
 *   <li>Return original value if no translation is available</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SimpliXI18nTransSerializer.class)
public @interface I18nTrans {

    /**
     * Name of the source field containing the i18n translation Map.
     * The source field should be of type {@code Map<String, String>} where keys are locale codes.
     * <p>
     * Supports dot notation for nested objects (e.g., "tagGroup.nameI18n").
     *
     * @return source field name (e.g., "roleNameI18n" or "tagGroup.nameI18n")
     */
    String source();

    /**
     * Target field path to set the translated value.
     * <p>
     * If empty (default), the annotated field itself receives the translated value (simple mode).
     * If specified, supports dot notation for nested objects (e.g., "tagGroup.name").
     * <p>
     * When target is specified, the annotated field should be an object type, and the serializer
     * will modify the nested object's target field before serialization.
     *
     * @return target field path (default: "" for simple mode)
     */
    String target() default "";

    /**
     * Default locale code to use when the current locale is not found in the translation Map.
     *
     * @return default locale code (default: "en")
     */
    String defaultLocale() default "en";
}
