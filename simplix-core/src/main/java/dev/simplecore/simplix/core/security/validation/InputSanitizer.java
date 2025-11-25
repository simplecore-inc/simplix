package dev.simplecore.simplix.core.security.validation;

import dev.simplecore.simplix.core.security.sanitization.HtmlSanitizer;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing various types of user input.
 * Provides comprehensive input validation and sanitization methods.
 */
@Slf4j
public class InputSanitizer {

    // Reusable validator instance (thread-safe)
    private static final SqlInjectionValidator SQL_VALIDATOR = new SqlInjectionValidator();

    // Patterns for validation
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern ALPHANUMERIC_WITH_SPACE_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=.]+$");
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]+$");

    // Dangerous characters for different contexts
    private static final String SQL_DANGEROUS_CHARS = "'\"\\;--/**/";
    private static final String HTML_DANGEROUS_CHARS = "<>&\"'/";
    private static final String LDAP_DANGEROUS_CHARS = "\\*()|&";
    private static final String OS_COMMAND_DANGEROUS_CHARS = "&|;`$(){}[]<>\\";

    /**
     * Sanitize input for use in SQL contexts (as additional protection layer).
     * First validates with SqlInjectionValidator, then applies sanitization.
     *
     * <p><b>⚠ IMPORTANT:</b> Always use parameterized queries as primary defense.
     * This is an additional layer, not a replacement for proper SQL practices.
     *
     * @param input Input to sanitize
     * @return Sanitized input, or empty string if validation fails
     */
    public static String sanitizeForSql(String input) {
        if (input == null) {
            return null;
        }

        // First validate with SqlInjectionValidator (reuse static instance)
        if (!SQL_VALIDATOR.isSafeInput(input)) {
            log.warn("✖ Input failed SQL injection validation, returning empty string");
            return ""; // Return empty string for unsafe input
        }

        // Apply sanitization
        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (SQL_DANGEROUS_CHARS.indexOf(c) == -1) {
                sanitized.append(c);
            }
        }

        // Remove SQL keywords
        String result = sanitized.toString();
        result = removeSqlKeywords(result);

        return result.trim();
    }

    /**
     * Sanitize input for display in HTML context.
     * Delegates to HtmlSanitizer for consistent HTML escaping.
     */
    public static String sanitizeForHtml(String input) {
        return HtmlSanitizer.escapeHtml(input);
    }

    /**
     * Sanitize input for use in JavaScript context
     */
    public static String sanitizeForJavaScript(String input) {
        if (input == null) {
            return null;
        }

        // Apply JavaScript sanitization
        return input.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("/", "\\/")
                   .replace("<", "\\x3C")
                   .replace(">", "\\x3E");
    }

    /**
     * Sanitize input for use in LDAP queries
     */
    public static String sanitizeForLdap(String input) {
        if (input == null) {
            return null;
        }

        // Apply LDAP sanitization
        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (LDAP_DANGEROUS_CHARS.indexOf(c) == -1) {
                sanitized.append(c);
            }
        }

        return sanitized.toString();
    }

    /**
     * Sanitize input for use in OS commands
     */
    public static String sanitizeForOsCommand(String input) {
        if (input == null) {
            return null;
        }

        // Apply OS command sanitization
        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (OS_COMMAND_DANGEROUS_CHARS.indexOf(c) == -1) {
                sanitized.append(c);
            }
        }

        return sanitized.toString();
    }

    /**
     * Sanitize file name
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }

        // Remove path traversal attempts
        fileName = fileName.replaceAll("\\.\\./", "")
                          .replaceAll("\\.\\.", "")
                          .replaceAll("/", "")
                          .replaceAll("\\\\", "");

        // Keep only safe characters
        StringBuilder sanitized = new StringBuilder();
        for (char c : fileName.toCharArray()) {
            if (FILENAME_PATTERN.matcher(String.valueOf(c)).matches() || c == '.' || c == '-' || c == '_') {
                sanitized.append(c);
            }
        }

        return sanitized.toString();
    }

    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }

        // Validate email format
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate URL format
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // Validate URL format
        return URL_PATTERN.matcher(url).matches();
    }

    /**
     * Check if input contains only alphanumeric characters
     */
    public static boolean isAlphanumeric(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Validate alphanumeric pattern
        return ALPHANUMERIC_PATTERN.matcher(input).matches();
    }

    /**
     * Check if input contains only alphanumeric characters and spaces
     */
    public static boolean isAlphanumericWithSpace(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        // Validate alphanumeric pattern
        return ALPHANUMERIC_WITH_SPACE_PATTERN.matcher(input).matches();
    }

    /**
     * Validate UUID format
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        // Validate UUID format
        String uuidPattern = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
        return Pattern.compile(uuidPattern).matcher(uuid).matches();
    }

    /**
     * Escape LIKE pattern for safe SQL queries
     */
    public static String escapeLikePattern(String pattern) {
        if (pattern == null) {
            return null;
        }

        // Escape special characters for LIKE patterns
        return pattern.replace("\\", "\\\\")
                     .replace("%", "\\%")
                     .replace("_", "\\_");
    }

    /**
     * Remove common SQL keywords from input
     */
    private static String removeSqlKeywords(String input) {
        if (input == null) {
            return null;
        }

        String[] sqlKeywords = {
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "CREATE",
            "ALTER", "TRUNCATE", "EXEC", "EXECUTE", "UNION", "FROM",
            "WHERE", "AND", "OR", "LIKE", "JOIN", "SCRIPT", "DECLARE"
        };

        String result = input.toUpperCase();
        for (String keyword : sqlKeywords) {
            result = result.replaceAll("\\b" + keyword + "\\b", "");
        }

        return result.trim();
    }

}