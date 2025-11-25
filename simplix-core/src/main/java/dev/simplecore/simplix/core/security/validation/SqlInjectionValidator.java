package dev.simplecore.simplix.core.security.validation;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL Injection attack prevention validator.
 * Validates input parameters to prevent SQL injection attempts.
 */
public class SqlInjectionValidator {

    // Common SQL injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        // SQL keywords that should not appear in user input
        Pattern.compile(".*\\b(ALTER|CREATE|DELETE|DROP|EXEC(UTE)?|INSERT( +INTO)?|MERGE|SELECT|UPDATE|UNION( +ALL)?)\\b.*", 
            Pattern.CASE_INSENSITIVE),
        
        // SQL comment indicators
        Pattern.compile(".*(--|#|/\\*|\\*/|@@|@).*"),
        
        // SQL operators and special characters
        Pattern.compile(".*[';].*"),
        
        // Hex encoding attempts
        Pattern.compile(".*\\b(0x[0-9a-f]+)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // Common SQL injection techniques
        Pattern.compile(".*(\\bOR\\b.{1,20}?=.{1,20}?\\bOR\\b).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*(\\bAND\\b.{1,20}?=.{1,20}?\\bAND\\b).*", Pattern.CASE_INSENSITIVE),
        
        // WAITFOR DELAY and benchmark attacks
        Pattern.compile(".*\\b(WAITFOR|BENCHMARK|SLEEP|DELAY)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // System tables and functions
        Pattern.compile(".*\\b(INFORMATION_SCHEMA|SYSOBJECTS|SYSCOLUMNS|SYSUSERS)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // Dangerous functions
        Pattern.compile(".*\\b(LOAD_FILE|INTO OUTFILE|INTO DUMPFILE)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // XP commands (SQL Server)
        Pattern.compile(".*\\bxp_.*", Pattern.CASE_INSENSITIVE)
    );

    // Whitelist patterns for safe input - allow apostrophes for names like O'Brien and international characters
    private static final Pattern SAFE_INPUT_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s@._\\-+'가-힣ぁ-ゔァ-ヴー々〆〤一-龥À-ÿĀ-ſЀ-ӿ؀-ۿ]+$");

    // Phone pattern (E.164 format)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

    /**
     * Validates input string for SQL injection attempts.
     * 
     * @param input Input string to validate
     * @return true if input is safe, false if potential SQL injection detected
     */
    public boolean isSafeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return true;
        }

        // Allow legitimate apostrophes in names (like O'Brien)
        // and double dashes in descriptions
        // But still check for SQL injection patterns
        String normalizedInput = input.toUpperCase();
        
        // Check for obvious SQL injection attempts
        // Note: -- followed by space or at end of line is SQL comment
        if (normalizedInput.contains("' OR ") || normalizedInput.contains("';")
            || normalizedInput.contains("' UNION ") || normalizedInput.matches(".*--\\s*$")
            || normalizedInput.contains("-- ") || normalizedInput.contains("/*") || normalizedInput.contains("*/")
            || normalizedInput.contains("DROP TABLE") || normalizedInput.contains("DELETE FROM")
            || normalizedInput.contains("INSERT INTO") || normalizedInput.contains("UPDATE ")
            || normalizedInput.contains("EXEC") || normalizedInput.contains("WAITFOR")
            || normalizedInput.contains("SLEEP")) {
            return false;
        }

        return true;
    }

    /**
     * Validates input string against whitelist pattern.
     * 
     * @param input Input string to validate
     * @return true if input matches safe pattern
     */
    public boolean matchesSafePattern(String input) {
        if (input == null) {
            return true;
        }
        return SAFE_INPUT_PATTERN.matcher(input).matches();
    }

    /**
     * Validates email format and checks for SQL injection.
     * Delegates to InputSanitizer for email format validation.
     *
     * @param email Email to validate
     * @return true if email is valid and safe
     */
    public boolean isSafeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true;
        }

        // Check email format using InputSanitizer
        if (!InputSanitizer.isValidEmail(email)) {
            return false;
        }

        // Additional SQL injection check for email
        return isSafeInput(email);
    }

    /**
     * Validates phone number format and checks for SQL injection.
     * 
     * @param phone Phone number to validate
     * @return true if phone is valid and safe
     */
    public boolean isSafePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return true;
        }
        
        // Remove common separators for validation
        String cleanPhone = phone.replaceAll("[\\s\\-().]", "");
        
        // Check phone format
        if (!PHONE_PATTERN.matcher(cleanPhone).matches()) {
            return false;
        }
        
        return true;
    }

    /**
     * Sanitizes input by removing potentially dangerous characters.
     * Use this only for display purposes, not for SQL queries.
     * 
     * @param input Input to sanitize
     * @return Sanitized string
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove SQL comment indicators
        String sanitized = input.replaceAll("(--|#|/\\*|\\*/)", "");
        
        // Remove semicolons and quotes
        sanitized = sanitized.replaceAll("[';\"\\\\]", "");
        
        // Remove multiple spaces
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        return sanitized.trim();
    }

    /**
     * Validates that input contains only alphanumeric characters and basic punctuation.
     * 
     * @param input Input to validate
     * @param allowSpaces Whether to allow spaces
     * @return true if input is alphanumeric
     */
    public boolean isAlphanumeric(String input, boolean allowSpaces) {
        if (input == null) {
            return true;
        }
        
        String pattern = allowSpaces ? "^[a-zA-Z0-9\\s]+$" : "^[a-zA-Z0-9]+$";
        return Pattern.matches(pattern, input);
    }

    /**
     * Validates UUID format.
     *
     * @param uuid UUID string to validate
     * @return true if valid UUID format
     */
    public boolean isValidUUID(String uuid) {
        if (uuid == null) {
            return false;
        }

        String uuidPattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
        return Pattern.matches(uuidPattern, uuid.toLowerCase());
    }

    /**
     * Validates base64 encoded hash format (like SHA-256).
     * Allows standard base64 characters: A-Z, a-z, 0-9, +, /, =
     *
     * @param hash Base64 encoded hash string
     * @return true if valid base64 format without SQL injection
     */
    public boolean isSafeHash(String hash) {
        if (hash == null || hash.trim().isEmpty()) {
            return true;
        }

        // Base64 pattern: alphanumeric, plus, slash, and equals for padding
        // SHA-256 in base64 is typically 44 characters, but we allow various lengths
        String base64Pattern = "^[A-Za-z0-9+/]+=*$";
        if (!Pattern.matches(base64Pattern, hash)) {
            return false;
        }

        // Additional SQL injection check
        return isSafeInput(hash);
    }

    /**
     * Escapes special characters for LIKE queries.
     * 
     * @param input Input string for LIKE query
     * @return Escaped string safe for LIKE queries
     */
    public String escapeLikePattern(String input) {
        if (input == null) {
            return null;
        }
        
        // Escape LIKE wildcards
        String escaped = input.replace("\\", "\\\\");
        escaped = escaped.replace("%", "\\%");
        escaped = escaped.replace("_", "\\_");
        escaped = escaped.replace("[", "\\[");
        
        return escaped;
    }

    /**
     * Validates sort field name to prevent injection through ORDER BY.
     * 
     * @param fieldName Field name for sorting
     * @param allowedFields List of allowed field names
     * @return true if field name is safe
     */
    public boolean isValidSortField(String fieldName, List<String> allowedFields) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return true;
        }
        
        // Check if field is in allowed list
        if (allowedFields != null && !allowedFields.isEmpty()) {
            return allowedFields.contains(fieldName);
        }
        
        // Basic validation for field name format
        return Pattern.matches("^[a-zA-Z][a-zA-Z0-9_]{0,63}$", fieldName);
    }

    /**
     * Validates pagination parameters.
     * 
     * @param page Page number
     * @param size Page size
     * @return true if pagination parameters are valid
     */
    public boolean isValidPagination(Integer page, Integer size) {
        if (page != null && page < 0) {
            return false;
        }
        
        if (size != null && (size <= 0 || size > 1000)) {
            return false;
        }
        
        return true;
    }
}