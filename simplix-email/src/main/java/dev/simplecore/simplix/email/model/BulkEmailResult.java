package dev.simplecore.simplix.email.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a bulk email send operation.
 * <p>
 * Contains aggregate statistics and individual results for each recipient.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEmailResult {

    /**
     * Batch identifier for this bulk send.
     */
    private String batchId;

    /**
     * Total number of recipients.
     */
    private int totalCount;

    /**
     * Number of successfully sent emails.
     */
    private int successCount;

    /**
     * Number of failed emails.
     */
    private int failureCount;

    /**
     * Number of emails still pending.
     */
    private int pendingCount;

    /**
     * Individual results for each recipient.
     */
    @Builder.Default
    private List<RecipientResult> results = new ArrayList<>();

    /**
     * Timestamp when bulk send started.
     */
    private Instant startTime;

    /**
     * Timestamp when bulk send completed.
     */
    private Instant endTime;

    /**
     * Provider type used for sending.
     */
    private MailProviderType providerType;

    /**
     * Result for a single recipient in bulk send.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecipientResult {

        /**
         * Recipient email address.
         */
        private String email;

        /**
         * Whether this recipient's email was sent successfully.
         */
        private boolean success;

        /**
         * Message ID from provider.
         */
        private String messageId;

        /**
         * Error message if failed.
         */
        private String errorMessage;

        /**
         * Status of this recipient's email.
         */
        private EmailStatus status;

        /**
         * Create a successful recipient result.
         *
         * @param email recipient email
         * @param messageId provider message ID
         * @return successful RecipientResult
         */
        public static RecipientResult success(String email, String messageId) {
            return RecipientResult.builder()
                    .email(email)
                    .success(true)
                    .messageId(messageId)
                    .status(EmailStatus.SENT)
                    .build();
        }

        /**
         * Create a failed recipient result.
         *
         * @param email recipient email
         * @param errorMessage error description
         * @return failed RecipientResult
         */
        public static RecipientResult failure(String email, String errorMessage) {
            return RecipientResult.builder()
                    .email(email)
                    .success(false)
                    .errorMessage(errorMessage)
                    .status(EmailStatus.FAILED)
                    .build();
        }
    }

    /**
     * Check if all emails were sent successfully.
     *
     * @return true if no failures
     */
    public boolean isAllSuccess() {
        return failureCount == 0 && pendingCount == 0;
    }

    /**
     * Check if any emails were sent successfully.
     *
     * @return true if at least one success
     */
    public boolean hasAnySuccess() {
        return successCount > 0;
    }

    /**
     * Get success rate as percentage.
     *
     * @return success rate (0-100)
     */
    public double getSuccessRate() {
        if (totalCount == 0) {
            return 0.0;
        }
        return (double) successCount / totalCount * 100;
    }
}
