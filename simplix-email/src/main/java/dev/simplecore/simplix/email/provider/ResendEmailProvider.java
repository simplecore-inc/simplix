package dev.simplecore.simplix.email.provider;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.resend.services.emails.model.Tag;
import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resend email provider.
 * <p>
 * Uses Resend API for sending emails. Modern email API designed for developers
 * with excellent deliverability and developer experience.
 * <p>
 * Configuration requires Resend API key:
 * <pre>{@code
 * resend:
 *   api-key: re_xxxx
 *   from-address: noreply@example.com
 * }</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class ResendEmailProvider extends AbstractEmailProvider {

    private final Resend resend;
    private final String defaultFromAddress;
    private final String defaultFromName;

    @Override
    protected EmailResult doSend(EmailRequest request) throws Exception {
        CreateEmailOptions params = buildEmailOptions(request);

        try {
            CreateEmailResponse response = resend.emails().send(params);
            String messageId = response.getId();
            if (messageId == null || messageId.isEmpty()) {
                messageId = "resend-" + System.currentTimeMillis();
            }
            return EmailResult.success(messageId, getType());
        } catch (ResendException e) {
            return handleResendError(e);
        }
    }

    @Override
    protected EmailResult handleException(Exception e) {
        return EmailResult.failure("Resend send failed: " + e.getMessage(), getType());
    }

    @Override
    public MailProviderType getType() {
        return MailProviderType.RESEND;
    }

    @Override
    public boolean isAvailable() {
        return resend != null;
    }

    @Override
    public int getPriority() {
        return 55;
    }

    @Override
    public boolean supportsBulkSend() {
        return true;
    }

    private CreateEmailOptions buildEmailOptions(EmailRequest request) {
        var builder = CreateEmailOptions.builder();

        // From address
        String from;
        if (request.getFrom() != null) {
            from = formatEmailAddress(request.getFrom());
        } else {
            from = formatEmailAddress(defaultFromName, defaultFromAddress);
        }
        builder.from(from);

        // Subject
        builder.subject(request.getSubject());

        // Recipients
        List<String> toAddresses = request.getTo().stream()
                .map(EmailAddress::getAddress)
                .collect(Collectors.toList());
        builder.to(toAddresses);

        // CC
        if (request.getCc() != null && !request.getCc().isEmpty()) {
            List<String> ccAddresses = request.getCc().stream()
                    .map(EmailAddress::getAddress)
                    .collect(Collectors.toList());
            builder.cc(ccAddresses);
        }

        // BCC
        if (request.getBcc() != null && !request.getBcc().isEmpty()) {
            List<String> bccAddresses = request.getBcc().stream()
                    .map(EmailAddress::getAddress)
                    .collect(Collectors.toList());
            builder.bcc(bccAddresses);
        }

        // Reply-to
        if (request.getReplyTo() != null) {
            builder.replyTo(request.getReplyTo().getAddress());
        }

        // Content
        if (request.hasHtmlBody()) {
            builder.html(request.getHtmlBody());
        }
        if (request.hasTextBody()) {
            builder.text(request.getTextBody());
        }

        // Attachments
        if (request.hasAttachments()) {
            List<Attachment> attachments = new ArrayList<>();
            for (EmailAttachment attachment : request.getAttachments()) {
                var resendAttachment = Attachment.builder()
                        .fileName(attachment.getFilename())
                        .content(Base64.getEncoder().encodeToString(attachment.getContent()))
                        .build();
                attachments.add(resendAttachment);
            }
            builder.attachments(attachments);
        }

        // Tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            List<Tag> tags = request.getTags().stream()
                    .map(tag -> Tag.builder().name(tag).value(tag).build())
                    .collect(Collectors.toList());
            builder.tags(tags);
        }

        // Custom headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            builder.headers(request.getHeaders());
        }

        return builder.build();
    }

    private String formatEmailAddress(EmailAddress address) {
        return formatEmailAddress(address.getName(), address.getAddress());
    }

    private String formatEmailAddress(String name, String address) {
        if (name != null && !name.isBlank()) {
            return name + " <" + address + ">";
        }
        return address;
    }

    private EmailResult handleResendError(ResendException e) {
        String errorMessage = "Resend error: " + e.getMessage();

        // Check if retryable (rate limit or server error based on message)
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (message.contains("rate limit") || message.contains("429") ||
                message.contains("500") || message.contains("503")) {
            return EmailResult.retryableFailure(
                    errorMessage,
                    "RESEND_ERROR",
                    getType()
            );
        }

        return EmailResult.failure(errorMessage, getType());
    }
}
