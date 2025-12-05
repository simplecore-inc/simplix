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
import java.util.Locale;
import java.util.Map;

/**
 * Request for sending templated emails.
 * <p>
 * Uses Thymeleaf templates stored in the database or filesystem.
 * Template variables are merged at send time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateEmailRequest {

    /**
     * Template code identifying which template to use.
     */
    @NotBlank(message = "Template code is required")
    private String templateCode;

    /**
     * Sender address. If not provided, default sender will be used.
     */
    @Valid
    private EmailAddress from;

    /**
     * Primary recipients.
     */
    @NotEmpty(message = "At least one recipient is required")
    @Valid
    private List<EmailAddress> to;

    /**
     * Carbon copy recipients.
     */
    @Valid
    @Builder.Default
    private List<EmailAddress> cc = new ArrayList<>();

    /**
     * Blind carbon copy recipients.
     */
    @Valid
    @Builder.Default
    private List<EmailAddress> bcc = new ArrayList<>();

    /**
     * Reply-to address.
     */
    @Valid
    private EmailAddress replyTo;

    /**
     * Subject line override. If not provided, template's default subject will be used.
     */
    private String subject;

    /**
     * Template variables for merging.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Locale for template selection (for i18n support).
     */
    @Builder.Default
    private Locale locale = Locale.getDefault();

    /**
     * Attachments to include.
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
     * Tenant ID for multi-tenancy.
     */
    private String tenantId;

    /**
     * Correlation ID for tracking.
     */
    private String correlationId;

    /**
     * Tags for analytics.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Create a simple template email request.
     *
     * @param templateCode template identifier
     * @param to recipient email
     * @param variables template variables
     * @return TemplateEmailRequest instance
     */
    public static TemplateEmailRequest of(String templateCode, String to, Map<String, Object> variables) {
        return TemplateEmailRequest.builder()
                .templateCode(templateCode)
                .to(List.of(EmailAddress.of(to)))
                .variables(variables != null ? variables : new HashMap<>())
                .build();
    }
}
