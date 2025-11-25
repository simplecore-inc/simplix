package dev.simplecore.simplix.core.security.sanitization;

/**
 * Centralized utility for masking sensitive data.
 * Provides consistent masking algorithms across the entire application.
 * <p>
 * Used by:
 * - MaskingConverter (JPA attribute masking)
 * - LogMasker (log sanitization)
 * - UniversalMaskingListener (entity listener masking)
 */
public final class DataMaskingUtils {

    private static final String DEFAULT_MASK = "****";
    private static final String MASK_CHAR = "*";

    private DataMaskingUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Masks an email address, showing only the first 2 characters and domain.
     * <p>
     * Examples:
     * - user@example.com -> us***@example.com
     * - a@test.com -> **@test.com
     *
     * @param email Email address to mask
     * @return Masked email or original if invalid format
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email; // Not a valid email format
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 2) {
            return "**" + domain;
        }

        return localPart.substring(0, 2) + "***" + domain;
    }

    /**
     * Masks a phone number, showing only the area code or country code.
     * <p>
     * Examples:
     * - 010-1234-5678 -> 010-****-****
     * - +82-10-1234-5678 -> +82-***-****
     * - 02-123-4567 -> 02-****-****
     *
     * @param phoneNumber Phone number to mask
     * @return Masked phone number
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        // Remove all non-digits for processing
        String digits = phoneNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 10) {
            return phoneNumber; // Too short to be a valid phone number
        }

        // Korean mobile numbers (010, 011, etc.)
        if (digits.startsWith("01") || digits.startsWith("02")) {
            String areaCode = digits.substring(0, digits.startsWith("02") ? 2 : 3);
            return areaCode + "-****-****";
        }

        // International format
        if (phoneNumber.startsWith("+")) {
            int firstSpace = phoneNumber.indexOf(' ');
            if (firstSpace > 0) {
                return phoneNumber.substring(0, firstSpace) + " ***-****";
            }
        }

        // Default: mask all but first 3 digits
        return digits.substring(0, 3) + "-****-****";
    }

    /**
     * Masks a credit card number, showing only the last 4 digits.
     * <p>
     * Examples:
     * - 1234-5678-9012-3456 -> ****-****-****-3456
     * - 1234567890123456 -> ************3456
     *
     * @param cardNumber Credit card number to mask
     * @return Masked card number
     */
    public static String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isEmpty()) {
            return cardNumber;
        }

        String digits = cardNumber.replaceAll("[^0-9]", "");

        if (digits.length() < 12) {
            return DEFAULT_MASK; // Too short to be a valid card number
        }

        // Keep last 4 digits only
        String last4 = digits.substring(digits.length() - 4);

        // Maintain original format if present
        if (cardNumber.contains("-")) {
            return "****-****-****-" + last4;
        } else if (cardNumber.contains(" ")) {
            return "**** **** **** " + last4;
        } else {
            return MASK_CHAR.repeat(digits.length() - 4) + last4;
        }
    }

    /**
     * Masks a Korean Resident Registration Number (주민등록번호).
     * Shows only birth date (YYMMDD), masks the rest.
     * <p>
     * Examples:
     * - 901231-1234567 -> 901231-*******
     * - 901231 1234567 -> 901231-*******
     *
     * @param rrn Resident registration number
     * @return Masked RRN
     */
    public static String maskRRN(String rrn) {
        if (rrn == null || rrn.isEmpty()) {
            return rrn;
        }

        // Remove all non-digits
        String digits = rrn.replaceAll("[^0-9]", "");

        if (digits.length() != 13) {
            return rrn; // Invalid format
        }

        // Show birth date (first 6 digits), mask the rest
        return digits.substring(0, 6) + "-*******";
    }

    /**
     * Masks an IP address at subnet level for GDPR compliance.
     * Delegates to IpAddressMaskingUtils for consistent masking.
     * <p>
     * Examples:
     * - IPv4: 192.168.1.123 -> 192.168.1.0
     * - IPv6: 2001:db8::7334 -> 2001:db8::0
     *
     * @param ipAddress IP address to mask (IPv4 or IPv6)
     * @return Masked IP address
     */
    public static String maskIpAddress(String ipAddress) {
        return IpAddressMaskingUtils.maskSubnetLevel(ipAddress);
    }

    /**
     * Masks a payment token (Stripe, PayPal, etc.).
     * Keeps prefix for identification, masks middle, shows last 4 characters.
     * <p>
     * Examples:
     * - pm_1234567890abcdef -> pm_****cdef
     * - tok_1234567890abcdef -> tok_****cdef
     * - PAYID-ABCDEF123456 -> PAYID-****3456
     *
     * @param token Payment token to mask
     * @return Masked token
     */
    public static String maskPaymentToken(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }

        if (token.length() <= 10) {
            return token; // Too short to be a real token
        }

        // Find underscore or dash position
        int separatorIndex = Math.max(token.indexOf('_'), token.indexOf('-'));

        if (separatorIndex > 0 && separatorIndex < 10) {
            // Format: prefix_token
            String prefix = token.substring(0, separatorIndex + 1);
            String tokenPart = token.substring(separatorIndex + 1);

            if (tokenPart.length() > 8) {
                return prefix + "****" + tokenPart.substring(tokenPart.length() - 4);
            }
        }

        // Default: mask middle portion
        if (token.length() > 20) {
            return token.substring(0, 6) + "****" + token.substring(token.length() - 4);
        }

        return token; // Keep short tokens as-is
    }

    /**
     * Generic masking with configurable keep first/last characters.
     *
     * @param value Value to mask
     * @param keepFirst Number of characters to keep at the beginning
     * @param keepLast Number of characters to keep at the end
     * @return Masked value
     */
    public static String maskGeneric(String value, int keepFirst, int keepLast) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (value.length() <= keepFirst + keepLast) {
            return value; // Too short to mask meaningfully
        }

        String prefix = value.substring(0, keepFirst);
        String suffix = value.substring(value.length() - keepLast);
        int maskLength = value.length() - keepFirst - keepLast;

        return prefix + MASK_CHAR.repeat(Math.min(maskLength, 10)) + suffix;
    }

    /**
     * Fully masks a value with asterisks.
     *
     * @param value Value to mask
     * @param maxLength Maximum length of mask (to prevent excessive asterisks)
     * @return Fully masked value
     */
    public static String maskFull(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int length = Math.min(value.length(), maxLength);
        return MASK_CHAR.repeat(length);
    }

    /**
     * Checks if a value appears to be already masked.
     *
     * @param value Value to check
     * @return true if value contains masking patterns
     */
    public static boolean isMasked(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Check for common masking patterns
        return value.contains("****") ||
               value.contains("***") ||
               value.matches(".*\\*{3,}.*");
    }
}