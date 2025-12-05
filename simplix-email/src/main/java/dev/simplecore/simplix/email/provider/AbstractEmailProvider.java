package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for email providers.
 * <p>
 * Provides common functionality for all email provider implementations.
 */
@Slf4j
public abstract class AbstractEmailProvider implements EmailProvider {

    /**
     * Send email with pre-send validation and logging.
     *
     * @param request email request
     * @return send result
     */
    @Override
    public EmailResult send(EmailRequest request) {
        if (!isAvailable()) {
            return EmailResult.failure("Provider not available: " + getType(), getType());
        }

        try {
            validateRequest(request);
            logSendAttempt(request);
            EmailResult result = doSend(request);
            logSendResult(request, result);
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid email request: {}", e.getMessage());
            return EmailResult.failure("Validation failed: " + e.getMessage(), getType());
        } catch (Exception e) {
            log.error("Failed to send email via {}: {}", getType(), e.getMessage(), e);
            return handleException(e);
        }
    }

    /**
     * Perform the actual email send operation.
     * Subclasses must implement this method.
     *
     * @param request validated email request
     * @return send result
     * @throws Exception if send fails
     */
    protected abstract EmailResult doSend(EmailRequest request) throws Exception;

    /**
     * Handle exceptions during send.
     * Subclasses can override to provide provider-specific error handling.
     *
     * @param e exception that occurred
     * @return appropriate error result
     */
    protected EmailResult handleException(Exception e) {
        return EmailResult.failure(e.getMessage(), getType());
    }

    /**
     * Validate email request before sending.
     *
     * @param request email request to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateRequest(EmailRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Email request cannot be null");
        }
        if (request.getTo() == null || request.getTo().isEmpty()) {
            throw new IllegalArgumentException("At least one recipient is required");
        }
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (!request.hasHtmlBody() && !request.hasTextBody()) {
            throw new IllegalArgumentException("Email body (HTML or text) is required");
        }
    }

    /**
     * Log send attempt for audit purposes.
     *
     * @param request email request
     */
    protected void logSendAttempt(EmailRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("Sending email via {}: to={}, subject={}",
                    getType(),
                    maskRecipients(request),
                    request.getSubject());
        }
    }

    /**
     * Log send result for audit purposes.
     *
     * @param request original request
     * @param result send result
     */
    protected void logSendResult(EmailRequest request, EmailResult result) {
        if (result.isSuccess()) {
            log.info("Email sent successfully via {}: messageId={}, to={}",
                    getType(),
                    result.getMessageId(),
                    maskRecipients(request));
        } else {
            log.warn("Email send failed via {}: to={}, error={}",
                    getType(),
                    maskRecipients(request),
                    result.getErrorMessage());
        }
    }

    /**
     * Mask recipient addresses for logging (privacy protection).
     *
     * @param request email request
     * @return masked recipient list string
     */
    protected String maskRecipients(EmailRequest request) {
        if (request.getTo() == null || request.getTo().isEmpty()) {
            return "[]";
        }
        return request.getTo().stream()
                .map(EmailAddress::toMaskedString)
                .toList()
                .toString();
    }
}
