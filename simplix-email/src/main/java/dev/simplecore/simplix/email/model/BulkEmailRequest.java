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
 * Request for sending bulk emails to multiple recipients.
 * <p>
 * Each recipient can have personalized variables for template merging.
 * Rate limiting is applied automatically based on configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEmailRequest {

    /**
     * Template code for the email content.
     */
    @NotBlank(message = "Template code is required")
    private String templateCode;

    /**
     * Sender address.
     */
    @Valid
    private EmailAddress from;

    /**
     * List of recipients with their individual variables.
     */
    @NotEmpty(message = "At least one recipient is required")
    @Valid
    private List<BulkRecipient> recipients;

    /**
     * Subject line override. If not provided, template's default will be used.
     */
    private String subject;

    /**
     * Common variables shared across all recipients.
     * Individual recipient variables will override these.
     */
    @Builder.Default
    private Map<String, Object> commonVariables = new HashMap<>();

    /**
     * Locale for template selection.
     */
    @Builder.Default
    private Locale locale = Locale.getDefault();

    /**
     * Email priority.
     */
    @NotNull
    @Builder.Default
    private MailPriority priority = MailPriority.LOW;

    /**
     * Tenant ID for multi-tenancy.
     */
    private String tenantId;

    /**
     * Campaign or batch identifier for tracking.
     */
    private String batchId;

    /**
     * Tags for analytics.
     */
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Whether to continue sending if some emails fail.
     */
    @Builder.Default
    private boolean continueOnError = true;

    /**
     * Represents a single recipient in a bulk email request.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkRecipient {

        /**
         * Recipient email address.
         */
        @NotNull
        @Valid
        private EmailAddress address;

        /**
         * Personalized variables for this recipient.
         * These override common variables.
         */
        @Builder.Default
        private Map<String, Object> variables = new HashMap<>();

        /**
         * Create a recipient with just an email address.
         *
         * @param email recipient email
         * @return BulkRecipient instance
         */
        public static BulkRecipient of(String email) {
            return BulkRecipient.builder()
                    .address(EmailAddress.of(email))
                    .build();
        }

        /**
         * Create a recipient with email and variables.
         *
         * @param email recipient email
         * @param variables personalized variables
         * @return BulkRecipient instance
         */
        public static BulkRecipient of(String email, Map<String, Object> variables) {
            return BulkRecipient.builder()
                    .address(EmailAddress.of(email))
                    .variables(variables != null ? variables : new HashMap<>())
                    .build();
        }
    }

    /**
     * Get total recipient count.
     *
     * @return number of recipients
     */
    public int getRecipientCount() {
        return recipients != null ? recipients.size() : 0;
    }
}
