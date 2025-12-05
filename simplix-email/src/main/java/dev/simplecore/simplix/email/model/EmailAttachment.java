package dev.simplecore.simplix.email.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an email attachment.
 * <p>
 * Supports both inline attachments (for embedded images) and
 * regular attachments (for downloadable files).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachment {

    /**
     * File name to display in the email.
     */
    @NotBlank(message = "Attachment filename is required")
    private String filename;

    /**
     * MIME type of the attachment (e.g., "application/pdf", "image/png").
     */
    @NotBlank(message = "Content type is required")
    private String contentType;

    /**
     * Raw content bytes of the attachment.
     */
    @NotNull(message = "Attachment content is required")
    private byte[] content;

    /**
     * Content ID for inline attachments (used for embedded images).
     * If set, the attachment will be inline with this CID.
     * Reference in HTML: &lt;img src="cid:contentId"&gt;
     */
    private String contentId;

    /**
     * Whether this attachment should be inline (embedded) or regular attachment.
     */
    @Builder.Default
    private boolean inline = false;

    /**
     * Create a regular attachment.
     *
     * @param filename file name
     * @param contentType MIME type
     * @param content file content
     * @return EmailAttachment instance
     */
    public static EmailAttachment of(String filename, String contentType, byte[] content) {
        return EmailAttachment.builder()
                .filename(filename)
                .contentType(contentType)
                .content(content)
                .inline(false)
                .build();
    }

    /**
     * Create an inline attachment for embedded images.
     *
     * @param contentId content ID for CID reference
     * @param filename file name
     * @param contentType MIME type
     * @param content image content
     * @return EmailAttachment instance
     */
    public static EmailAttachment inline(String contentId, String filename, String contentType, byte[] content) {
        return EmailAttachment.builder()
                .contentId(contentId)
                .filename(filename)
                .contentType(contentType)
                .content(content)
                .inline(true)
                .build();
    }

    /**
     * Get content size in bytes.
     *
     * @return content size
     */
    public int getSize() {
        return content != null ? content.length : 0;
    }
}
