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
 *
 * <p>Usage example:
 * <pre>
 * public class UserRoleInfo {
 *     &#64;I18nTrans(source = "roleNameI18n")
 *     private String roleName;
 *
 *     &#64;JsonIgnore
 *     private Map&lt;String, String&gt; roleNameI18n;  // {"en": "Admin", "ko": "관리자", "ja": "管理者"}
 * }
 * </pre>
 *
 * <p>The serializer will:
 * <ul>
 *   <li>Get current locale from {@link org.springframework.context.i18n.LocaleContextHolder}</li>
 *   <li>Extract translation from the source Map field</li>
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
     *
     * @return source field name (e.g., "roleNameI18n")
     */
    String source();

    /**
     * Default locale code to use when the current locale is not found in the translation Map.
     *
     * @return default locale code (default: "en")
     */
    String defaultLocale() default "en";
}
