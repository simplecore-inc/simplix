package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;

/**
 * Email provider interface for sending emails.
 * <p>
 * Implementations handle the actual email delivery through different
 * services (SMTP, AWS SES, SendGrid, etc.).
 * <p>
 * Each provider implementation should:
 * <ul>
 *   <li>Handle connection and authentication</li>
 *   <li>Convert EmailRequest to provider-specific format</li>
 *   <li>Send the email and return appropriate result</li>
 *   <li>Handle provider-specific errors gracefully</li>
 * </ul>
 */
public interface EmailProvider {

    /**
     * Send an email through this provider.
     *
     * @param request email request containing all necessary information
     * @return result containing success status, message ID, or error details
     */
    EmailResult send(EmailRequest request);

    /**
     * Get the provider type identifier.
     *
     * @return provider type enum value
     */
    MailProviderType getType();

    /**
     * Check if this provider is available and properly configured.
     *
     * @return true if provider can accept send requests
     */
    boolean isAvailable();

    /**
     * Get the priority order for this provider (higher = preferred).
     * Used when multiple providers are available.
     *
     * @return priority value (default 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this provider supports bulk sending natively.
     *
     * @return true if bulk send is optimized
     */
    default boolean supportsBulkSend() {
        return false;
    }
}
