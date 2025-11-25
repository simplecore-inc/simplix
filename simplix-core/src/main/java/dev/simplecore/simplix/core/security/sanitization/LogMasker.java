package dev.simplecore.simplix.core.security.sanitization;

import java.util.regex.Pattern;

/**
 * Utility for masking sensitive information in logs and audit trails.
 * Uses pattern matching to detect sensitive data in free-form text,
 * then delegates to DataMaskingUtils for consistent masking algorithms.
 */
public final class LogMasker {

    // Korean Resident Registration Number pattern (주민등록번호)
    private static final Pattern RRN_PATTERN = Pattern.compile(
        "\\b(\\d{2})(\\d{2})(\\d{2})[-\\s]?(\\d{7})\\b"
    );

    // Credit Card Number patterns
    private static final Pattern CARD_PATTERN = Pattern.compile(
        "\\b(\\d{4})[-\\s]?(\\d{4})[-\\s]?(\\d{4})[-\\s]?(\\d{3,4})\\b"
    );

    // Phone Number patterns (Korean and International)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(01[0-9]|02|0[3-9][0-9]?)[-\\s]?(\\d{3,4})[-\\s]?(\\d{4})\\b|" +
        "\\+?(\\d{1,3})[-\\s]?(\\d{1,4})[-\\s]?(\\d{1,4})[-\\s]?(\\d{1,4})\\b"
    );

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b"
    );

    // Password pattern in JSON or query strings
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(?i)(password|passwd|pwd|pass|secret|token|api[_-]?key)([\"'\\s:=]+)([^\"'\\s,}]+)",
        Pattern.CASE_INSENSITIVE
    );

    // IP Address pattern
    private static final Pattern IP_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    private LogMasker() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Masks all sensitive data in the input string.
     * Scans the text for patterns matching sensitive data types.
     *
     * @param input The input string containing potentially sensitive data
     * @return String with all sensitive data masked
     */
    public static String maskSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String masked = input;

        // Mask Korean Resident Registration Number
        masked = maskRRN(masked);

        // Mask Credit Card Numbers
        masked = maskCreditCard(masked);

        // Mask Phone Numbers
        masked = maskPhoneNumber(masked);

        // Mask Emails (partial masking)
        masked = maskEmail(masked);

        // Mask Passwords
        masked = maskPassword(masked);

        return masked;
    }

    /**
     * Masks Korean Resident Registration Numbers found in text.
     * Uses pattern matching to find RRNs, delegates to DataMaskingUtils for masking.
     */
    public static String maskRRN(String input) {
        if (input == null) {
            return null;
        }

        return RRN_PATTERN.matcher(input).replaceAll(m -> {
            String matched = m.group(0);
            return DataMaskingUtils.maskRRN(matched);
        });
    }

    /**
     * Masks credit card numbers found in text.
     * Uses pattern matching to find card numbers, delegates to DataMaskingUtils for masking.
     */
    public static String maskCreditCard(String input) {
        if (input == null) {
            return null;
        }

        return CARD_PATTERN.matcher(input).replaceAll(m -> {
            String matched = m.group(0);
            return DataMaskingUtils.maskCreditCard(matched);
        });
    }

    /**
     * Masks phone numbers found in text.
     * Uses pattern matching to find phone numbers, delegates to DataMaskingUtils for masking.
     */
    public static String maskPhoneNumber(String input) {
        if (input == null) {
            return null;
        }

        return PHONE_PATTERN.matcher(input).replaceAll(m -> {
            String matched = m.group(0);
            return DataMaskingUtils.maskPhoneNumber(matched);
        });
    }

    /**
     * Masks email addresses found in text.
     * Uses pattern matching to find emails, delegates to DataMaskingUtils for masking.
     */
    public static String maskEmail(String input) {
        if (input == null) {
            return null;
        }

        return EMAIL_PATTERN.matcher(input).replaceAll(m -> {
            String matched = m.group(0);
            return DataMaskingUtils.maskEmail(matched);
        });
    }

    /**
     * Masks passwords and API keys found in text.
     * Looks for key-value patterns in JSON, query strings, etc.
     */
    public static String maskPassword(String input) {
        if (input == null) {
            return null;
        }

        return PASSWORD_PATTERN.matcher(input).replaceAll(m -> {
            String key = m.group(1);
            String separator = m.group(2);
            return key + separator + "********";
        });
    }

    /**
     * Masks IP addresses found in text (optional based on requirements).
     * Uses pattern matching to find IPs, delegates to DataMaskingUtils for masking.
     */
    public static String maskIPAddress(String input) {
        if (input == null) {
            return null;
        }

        return IP_PATTERN.matcher(input).replaceAll(m -> {
            String matched = m.group(0);
            return DataMaskingUtils.maskIpAddress(matched);
        });
    }

    /**
     * Checks if a string contains sensitive data.
     *
     * @param input String to check
     * @return true if sensitive patterns detected
     */
    public static boolean containsSensitiveData(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return RRN_PATTERN.matcher(input).find() ||
               CARD_PATTERN.matcher(input).find() ||
               PHONE_PATTERN.matcher(input).find() ||
               EMAIL_PATTERN.matcher(input).find() ||
               PASSWORD_PATTERN.matcher(input).find();
    }

    /**
     * Masks specific field value based on field name.
     * Used for structured data like JSON objects.
     *
     * @param fieldName Name of the field
     * @param value Value to potentially mask
     * @return Masked value if field name indicates sensitive data
     */
    public static String maskFieldValue(String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String lowerFieldName = fieldName.toLowerCase();

        // Check field name for sensitive data indicators
        if (lowerFieldName.contains("password") ||
            lowerFieldName.contains("secret") ||
            lowerFieldName.contains("token") ||
            lowerFieldName.contains("apikey") ||
            lowerFieldName.contains("api_key")) {
            return "********";
        }

        if (lowerFieldName.contains("email")) {
            return DataMaskingUtils.maskEmail(value);
        }

        if (lowerFieldName.contains("phone") ||
            lowerFieldName.contains("mobile") ||
            lowerFieldName.contains("tel")) {
            return DataMaskingUtils.maskPhoneNumber(value);
        }

        if (lowerFieldName.contains("rrn") ||
            lowerFieldName.contains("ssn") ||
            lowerFieldName.contains("resident")) {
            return DataMaskingUtils.maskRRN(value);
        }

        if (lowerFieldName.contains("card") ||
            lowerFieldName.contains("credit")) {
            return DataMaskingUtils.maskCreditCard(value);
        }

        // Default: mask if contains sensitive data patterns
        return maskSensitiveData(value);
    }
}
