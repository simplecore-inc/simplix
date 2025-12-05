package dev.simplecore.simplix.email.service;

import dev.simplecore.simplix.email.model.BulkEmailRequest;
import dev.simplecore.simplix.email.model.BulkEmailResult;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import dev.simplecore.simplix.email.model.TemplateEmailRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Email service interface for sending emails.
 * <p>
 * Provides high-level email sending operations including:
 * <ul>
 *   <li>Direct email sending with raw content</li>
 *   <li>Template-based email sending</li>
 *   <li>Bulk email sending</li>
 *   <li>Asynchronous email sending</li>
 * </ul>
 */
public interface EmailService {

    /**
     * Send an email synchronously.
     *
     * @param request email request with all details
     * @return send result with status and message ID
     */
    EmailResult send(EmailRequest request);

    /**
     * Send an email using a template.
     *
     * @param request template email request
     * @return send result
     */
    EmailResult sendTemplate(TemplateEmailRequest request);

    /**
     * Send a simple template email.
     *
     * @param templateCode template identifier
     * @param to recipient email address
     * @param variables template variables
     * @return send result
     */
    EmailResult sendTemplate(String templateCode, String to, Map<String, Object> variables);

    /**
     * Send bulk emails to multiple recipients.
     *
     * @param request bulk email request
     * @return bulk result with individual recipient statuses
     */
    BulkEmailResult sendBulk(BulkEmailRequest request);

    /**
     * Send an email asynchronously.
     *
     * @param request email request
     * @return future that completes with send result
     */
    CompletableFuture<EmailResult> sendAsync(EmailRequest request);

    /**
     * Send a template email asynchronously.
     *
     * @param request template email request
     * @return future that completes with send result
     */
    CompletableFuture<EmailResult> sendTemplateAsync(TemplateEmailRequest request);

    /**
     * Send bulk emails asynchronously.
     *
     * @param request bulk email request
     * @return future that completes with bulk result
     */
    CompletableFuture<BulkEmailResult> sendBulkAsync(BulkEmailRequest request);

    /**
     * Check if email service is available.
     *
     * @return true if at least one provider is available
     */
    boolean isAvailable();
}
