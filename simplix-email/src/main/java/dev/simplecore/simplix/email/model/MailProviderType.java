package dev.simplecore.simplix.email.model;

/**
 * Mail provider types supported by the mail module.
 * <p>
 * Each provider has different characteristics and use cases:
 * <ul>
 *   <li>CONSOLE - Development only, logs emails to console</li>
 *   <li>SMTP - Traditional SMTP server connection</li>
 *   <li>AWS_SES - Amazon Simple Email Service</li>
 *   <li>SENDGRID - SendGrid email delivery service</li>
 *   <li>RESEND - Resend email delivery service</li>
 * </ul>
 */
public enum MailProviderType {

    /**
     * Console provider for development and testing.
     * Does not send actual emails, only logs to console.
     */
    CONSOLE,

    /**
     * SMTP provider for traditional email servers.
     * Supports both plain SMTP and SMTP with TLS/SSL.
     */
    SMTP,

    /**
     * AWS Simple Email Service provider.
     * Recommended for production with high volume.
     */
    AWS_SES,

    /**
     * SendGrid email delivery service provider.
     * Good for transactional and marketing emails.
     */
    SENDGRID,

    /**
     * Resend email delivery service provider.
     * Modern email API for developers.
     */
    RESEND
}
