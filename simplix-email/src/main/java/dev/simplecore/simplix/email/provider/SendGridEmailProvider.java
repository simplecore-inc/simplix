package dev.simplecore.simplix.email.provider;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;

/**
 * SendGrid email provider.
 * <p>
 * Uses SendGrid Web API v3 for sending emails. Good for both transactional
 * and marketing emails with built-in analytics.
 * <p>
 * Configuration requires SendGrid API key:
 * <pre>{@code
 * sendgrid:
 *   api-key: SG.xxxx
 *   from-address: noreply@example.com
 * }</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class SendGridEmailProvider extends AbstractEmailProvider {

    private final SendGrid sendGrid;
    private final String defaultFromAddress;
    private final String defaultFromName;

    @Override
    protected EmailResult doSend(EmailRequest request) throws Exception {
        Mail mail = buildMail(request);

        Request sendRequest = new Request();
        sendRequest.setMethod(Method.POST);
        sendRequest.setEndpoint("mail/send");
        sendRequest.setBody(mail.build());

        Response response = sendGrid.api(sendRequest);

        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            String messageId = response.getHeaders().get("X-Message-Id");
            if (messageId == null) {
                messageId = "sendgrid-" + System.currentTimeMillis();
            }
            return EmailResult.success(messageId, getType());
        } else {
            return handleSendGridError(response);
        }
    }

    @Override
    protected EmailResult handleException(Exception e) {
        return EmailResult.failure("SendGrid send failed: " + e.getMessage(), getType());
    }

    @Override
    public MailProviderType getType() {
        return MailProviderType.SENDGRID;
    }

    @Override
    public boolean isAvailable() {
        return sendGrid != null;
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean supportsBulkSend() {
        return true;
    }

    private Mail buildMail(EmailRequest request) {
        Mail mail = new Mail();

        // From address
        if (request.getFrom() != null) {
            mail.setFrom(new Email(request.getFrom().getAddress(), request.getFrom().getName()));
        } else {
            mail.setFrom(new Email(defaultFromAddress, defaultFromName));
        }

        // Subject
        mail.setSubject(request.getSubject());

        // Personalization (recipients)
        Personalization personalization = new Personalization();

        for (EmailAddress to : request.getTo()) {
            personalization.addTo(new Email(to.getAddress(), to.getName()));
        }

        if (request.getCc() != null) {
            for (EmailAddress cc : request.getCc()) {
                personalization.addCc(new Email(cc.getAddress(), cc.getName()));
            }
        }

        if (request.getBcc() != null) {
            for (EmailAddress bcc : request.getBcc()) {
                personalization.addBcc(new Email(bcc.getAddress()));
            }
        }

        mail.addPersonalization(personalization);

        // Reply-to
        if (request.getReplyTo() != null) {
            mail.setReplyTo(new Email(request.getReplyTo().getAddress(), request.getReplyTo().getName()));
        }

        // Content
        if (request.hasTextBody()) {
            mail.addContent(new Content("text/plain", request.getTextBody()));
        }
        if (request.hasHtmlBody()) {
            mail.addContent(new Content("text/html", request.getHtmlBody()));
        }

        // Attachments
        if (request.hasAttachments()) {
            for (EmailAttachment attachment : request.getAttachments()) {
                Attachments sgAttachment = new Attachments();
                sgAttachment.setFilename(attachment.getFilename());
                sgAttachment.setType(attachment.getContentType());
                sgAttachment.setContent(Base64.getEncoder().encodeToString(attachment.getContent()));

                if (attachment.isInline() && attachment.getContentId() != null) {
                    sgAttachment.setDisposition("inline");
                    sgAttachment.setContentId(attachment.getContentId());
                } else {
                    sgAttachment.setDisposition("attachment");
                }

                mail.addAttachments(sgAttachment);
            }
        }

        // Categories (tags)
        if (request.getTags() != null) {
            for (String tag : request.getTags()) {
                mail.addCategory(tag);
            }
        }

        // Custom headers
        if (request.getHeaders() != null) {
            for (var entry : request.getHeaders().entrySet()) {
                mail.addHeader(entry.getKey(), entry.getValue());
            }
        }

        return mail;
    }

    private EmailResult handleSendGridError(Response response) {
        String errorMessage = "SendGrid error: HTTP " + response.getStatusCode();
        if (response.getBody() != null && !response.getBody().isEmpty()) {
            errorMessage += " - " + response.getBody();
        }

        // Check if retryable (rate limit or server error)
        if (response.getStatusCode() == 429 || response.getStatusCode() >= 500) {
            return EmailResult.retryableFailure(
                    errorMessage,
                    String.valueOf(response.getStatusCode()),
                    getType()
            );
        }

        return EmailResult.failure(errorMessage, getType());
    }
}
