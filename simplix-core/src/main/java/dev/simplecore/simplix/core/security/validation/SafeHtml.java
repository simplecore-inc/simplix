package dev.simplecore.simplix.core.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validation annotation to ensure HTML content is safe from XSS attacks.
 * Uses OWASP Java HTML Sanitizer to validate and sanitize HTML content.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SafeHtmlValidator.class)
@Documented
public @interface SafeHtml {
    
    /**
     * Error message when validation fails
     */
    String message() default "HTML content contains potentially dangerous elements";
    
    /**
     * Validation groups
     */
    Class<?>[] groups() default {};
    
    /**
     * Payload for clients
     */
    Class<? extends Payload>[] payload() default {};
    
    /**
     * Whether to allow basic HTML formatting tags
     * If true, allows tags like b, i, u, em, strong, p, br
     * If false, strips all HTML tags
     */
    boolean allowBasicFormatting() default false;
    
    /**
     * Whether to allow links (a href)
     * Links are sanitized to prevent javascript: and data: protocols
     */
    boolean allowLinks() default false;
    
    /**
     * Maximum length of the content after sanitization
     * -1 means no limit
     */
    int maxLength() default -1;
    
    /**
     * Custom whitelist of allowed tags
     * Empty means use default policy
     */
    String[] allowedTags() default {};
}