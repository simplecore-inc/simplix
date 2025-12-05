package dev.simplecore.simplix.email.model;

/**
 * Email delivery status enumeration.
 * <p>
 * Tracks the lifecycle of an email from creation to delivery or failure.
 */
public enum EmailStatus {

    /**
     * Email is queued and waiting to be sent.
     */
    PENDING,

    /**
     * Email is currently being processed by the provider.
     */
    SENDING,

    /**
     * Email was successfully sent to the provider.
     * Note: This does not guarantee delivery to recipient.
     */
    SENT,

    /**
     * Email was delivered to the recipient's mail server.
     */
    DELIVERED,

    /**
     * Email sending failed after all retry attempts.
     */
    FAILED,

    /**
     * Email bounced back (recipient address invalid or rejected).
     */
    BOUNCED,

    /**
     * Recipient marked the email as spam.
     */
    COMPLAINED,

    /**
     * Email was suppressed (recipient on suppression list).
     */
    SUPPRESSED
}
