package dev.simplecore.simplix.core.security.validation;

import dev.simplecore.simplix.core.security.sanitization.HtmlSanitizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

/**
 * Validator for SafeHtml annotation.
 * Validates HTML content to prevent XSS attacks.
 */
@Component
public class SafeHtmlValidator implements ConstraintValidator<SafeHtml, String> {
    
    private boolean allowBasicFormatting;
    private boolean allowLinks;
    private int maxLength;
    private String[] allowedTags;
    
    @Override
    public void initialize(SafeHtml constraintAnnotation) {
        this.allowBasicFormatting = constraintAnnotation.allowBasicFormatting();
        this.allowLinks = constraintAnnotation.allowLinks();
        this.maxLength = constraintAnnotation.maxLength();
        this.allowedTags = constraintAnnotation.allowedTags();
    }
    
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        
        // Sanitize the HTML content
        String sanitized = HtmlSanitizer.sanitize(
            value,
            allowBasicFormatting,
            allowLinks,
            allowedTags
        );
        
        // Check if content was modified (indicates potentially dangerous content)
        if (!value.equals(sanitized)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "HTML content was modified during sanitization. Original content may contain dangerous elements."
            ).addConstraintViolation();
            return false;
        }
        
        // Check length constraint if specified
        if (maxLength > 0 && sanitized.length() > maxLength) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                String.format("Content length %d exceeds maximum allowed length %d", 
                    sanitized.length(), maxLength)
            ).addConstraintViolation();
            return false;
        }
        
        // Additional security checks - delegate to HtmlSanitizer for consistent detection
        if (HtmlSanitizer.containsDangerousContent(value)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                "Content contains suspicious patterns that may indicate XSS attempt"
            ).addConstraintViolation();
            return false;
        }

        return true;
    }
}