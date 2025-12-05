package dev.simplecore.simplix.email.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of an email send operation.
 * <p>
 * Contains status, provider information, and any error details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResult {

    /**
     * Whether the email was sent successfully.
     */
    private boolean success;

    /**
     * Current status of the email.
     */
    private EmailStatus status;

    /**
     * Message ID assigned by the provider.
     */
    private String messageId;

    /**
     * Provider type that handled the send.
     */
    private MailProviderType providerType;

    /**
     * Error message if sending failed.
     */
    private String errorMessage;

    /**
     * Error code from the provider.
     */
    private String errorCode;

    /**
     * Timestamp when the send was attempted.
     */
    private Instant timestamp;

    /**
     * Number of retry attempts made.
     */
    @Builder.Default
    private int retryCount = 0;

    /**
     * Whether the error is retryable.
     */
    @Builder.Default
    private boolean retryable = false;

    /**
     * Recipient addresses that were processed.
     */
    @Builder.Default
    private List<String> recipients = new ArrayList<>();

    /**
     * Create a successful result.
     *
     * @param messageId provider message ID
     * @param providerType provider type
     * @return successful EmailResult
     */
    public static EmailResult success(String messageId, MailProviderType providerType) {
        return EmailResult.builder()
                .success(true)
                .status(EmailStatus.SENT)
                .messageId(messageId)
                .providerType(providerType)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failed result.
     *
     * @param errorMessage error description
     * @param providerType provider type
     * @return failed EmailResult
     */
    public static EmailResult failure(String errorMessage, MailProviderType providerType) {
        return EmailResult.builder()
                .success(false)
                .status(EmailStatus.FAILED)
                .errorMessage(errorMessage)
                .providerType(providerType)
                .timestamp(Instant.now())
                .retryable(false)
                .build();
    }

    /**
     * Create a retryable failure result.
     *
     * @param errorMessage error description
     * @param errorCode provider error code
     * @param providerType provider type
     * @return retryable failed EmailResult
     */
    public static EmailResult retryableFailure(String errorMessage, String errorCode, MailProviderType providerType) {
        return EmailResult.builder()
                .success(false)
                .status(EmailStatus.PENDING)
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .providerType(providerType)
                .timestamp(Instant.now())
                .retryable(true)
                .build();
    }
}
