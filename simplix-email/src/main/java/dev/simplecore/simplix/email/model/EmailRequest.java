package dev.simplecore.simplix.email.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email request model containing all information needed to send an email.
 * <p>
 * Supports single recipient, multiple recipients (CC, BCC), attachments,
 * and both HTML and plain text content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    /**
     * Sender address. If not provided, default sender from configuration will be used.
     */
    @Valid
    private EmailAddress from;

    /**
     * Primary recipients (To).
     */
    @NotEmpty(message = "At least one recipient is required")
    @Valid
    private List<EmailAddress> to;

    /**
     * Carbon copy recipients (CC).
     */
    @Valid
    @Builder.Default
    private List<EmailAddress> cc = new ArrayList<>();

    /**
     * Blind carbon copy recipients (BCC).
     */
    @Valid
    @Builder.Default
    private List<EmailAddress> bcc = new ArrayList<>();

    /**
     * Reply-to address. If not provided, from address will be used.
     */
    @Valid
    private EmailAddress replyTo;

    /**
     * Email subject line.
     */
    @NotBlank(message = "Subject is required")
    private String subject;

    /**
     * HTML content of the email.
     * At least one of htmlBody or textBody must be provided.
     */
    private String htmlBody;

    /**
     * Plain text content of the email.
     * At least one of htmlBody or textBody must be provided.
     */
    private String textBody;

    /**
     * File attachments.
     */
    @Valid
    @Builder.Default
    private List<EmailAttachment> attachments = new ArrayList<>();

    /**
     * Email priority.
     */
    @NotNull
    @Builder.Default
    private MailPriority priority = MailPriority.NORMAL;

    /**
     * Custom headers to include in the email.
     */
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Tenant ID for multi-tenancy support.
     */
    private String tenantId;

    /**
     * Correlation ID for tracking across systems.
     */
    private String correlationId;

    /**
     * Tags for categorization and analytics.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Create a simple email request.
     *
     * @param to recipient address
     * @param subject email subject
     * @param htmlBody HTML content
     * @return EmailRequest instance
     */
    public static EmailRequest simple(String to, String subject, String htmlBody) {
        return EmailRequest.builder()
                .to(List.of(EmailAddress.of(to)))
                .subject(subject)
                .htmlBody(htmlBody)
                .build();
    }

    /**
     * Create a simple email with plain text.
     *
     * @param to recipient address
     * @param subject email subject
     * @param textBody plain text content
     * @return EmailRequest instance
     */
    public static EmailRequest plainText(String to, String subject, String textBody) {
        return EmailRequest.builder()
                .to(List.of(EmailAddress.of(to)))
                .subject(subject)
                .textBody(textBody)
                .build();
    }

    /**
     * Check if this request has HTML content.
     *
     * @return true if HTML body is present
     */
    public boolean hasHtmlBody() {
        return htmlBody != null && !htmlBody.isBlank();
    }

    /**
     * Check if this request has plain text content.
     *
     * @return true if text body is present
     */
    public boolean hasTextBody() {
        return textBody != null && !textBody.isBlank();
    }

    /**
     * Check if this request has attachments.
     *
     * @return true if attachments exist
     */
    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    /**
     * Get total recipients count (To + CC + BCC).
     *
     * @return total recipient count
     */
    public int getTotalRecipientCount() {
        int count = to != null ? to.size() : 0;
        count += cc != null ? cc.size() : 0;
        count += bcc != null ? bcc.size() : 0;
        return count;
    }
}
