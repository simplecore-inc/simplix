package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for internationalization titles
 * Supports multiple language-value pairs for field names
 * 
 * Usage example:
 * @I18nTitle({"ko=사용자", "en=User", "ja=ユーザー"})
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface I18nTitle {
    
    /**
     * Array of language-value pairs in format "language=value"
     * Example: {"ko=사용자", "en=User", "ja=ユーザー"}
     */
    String[] value();
} 