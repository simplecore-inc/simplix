package dev.simplecore.simplix.core.entity.listener;

import java.lang.annotation.*;

/**
 * Marks a field that contains sensitive data requiring masking.
 * The masking strategy can be customized per field.
 * <p>
 * Example usage:
 * <pre>
 * @MaskSensitive(type = MaskType.PAYMENT_TOKEN)
 * private String paymentMethodId;
 *
 * @MaskSensitive(type = MaskType.PARTIAL, keepFirst = 4, keepLast = 4)
 * private String accountNumber;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaskSensitive {

    /**
     * Type of masking to apply
     */
    MaskType type() default MaskType.FULL;

    /**
     * Number of characters to keep visible at the beginning
     * Only used with MaskType.PARTIAL
     */
    int keepFirst() default 3;

    /**
     * Number of characters to keep visible at the end
     * Only used with MaskType.PARTIAL
     */
    int keepLast() default 4;

    /**
     * Minimum length threshold for masking
     * Fields shorter than this won't be masked
     */
    int minLength() default 0;

    /**
     * Custom mask character (default: *)
     */
    String maskChar() default "*";

    /**
     * Whether to mask this field (can be used to conditionally disable masking)
     */
    boolean enabled() default true;

    /**
     * Masking strategy types
     */
    enum MaskType {
        /**
         * Completely mask the field
         */
        FULL,

        /**
         * Keep first and last N characters
         */
        PARTIAL,

        /**
         * Mask email (keep first char and domain)
         * user@example.com -> u***@example.com
         */
        EMAIL,

        /**
         * Mask phone number (keep area code)
         * 010-1234-5678 -> 010-****-****
         */
        PHONE,

        /**
         * Mask credit card (keep last 4 digits)
         * 1234-5678-9012-3456 -> ****-****-****-3456
         */
        CREDIT_CARD,

        /**
         * Mask payment token (intelligent masking based on format)
         * pm_1234567890abcdef -> pm_****cdef
         */
        PAYMENT_TOKEN,

        /**
         * Mask IP address (keep first two octets)
         * 192.168.1.100 -> 192.168.*.*
         */
        IP_ADDRESS,

        /**
         * Apply JSON masking using LogMaskingService
         */
        JSON,

        /**
         * No masking - for conditional logic
         */
        NONE
    }
}