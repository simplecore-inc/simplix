package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * SMTP email provider using Spring JavaMailSender.
 * <p>
 * This provider sends emails through a configured SMTP server.
 * Supports HTML/text content, attachments, and inline images.
 * <p>
 * Configuration is done through Spring Boot's mail properties:
 * <pre>{@code
 * spring:
 *   mail:
 *     host: smtp.example.com
 *     port: 587
 *     username: user@example.com
 *     password: secret
 *     properties:
 *       mail.smtp.auth: true
 *       mail.smtp.starttls.enable: true
 * }</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpEmailProvider extends AbstractEmailProvider {

    private final JavaMailSender mailSender;
    private final String defaultFromAddress;
    private final String defaultFromName;

    @Override
    protected EmailResult doSend(EmailRequest request) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = createMessageHelper(message, request);

        setAddresses(helper, request);
        setContent(helper, request);
        setAttachments(helper, request);

        mailSender.send(message);

        String messageId = message.getMessageID();
        if (messageId == null) {
            messageId = "smtp-" + UUID.randomUUID();
        }

        return EmailResult.success(messageId, getType());
    }

    @Override
    protected EmailResult handleException(Exception e) {
        if (e instanceof MailAuthenticationException) {
            return EmailResult.failure("SMTP authentication failed: " + e.getMessage(), getType());
        }
        if (e instanceof MailSendException) {
            String message = e.getMessage();
            if (message != null && (message.contains("Connection") || message.contains("timeout"))) {
                return EmailResult.retryableFailure(
                        "SMTP connection error: " + message,
                        "CONNECTION_ERROR",
                        getType()
                );
            }
        }
        return EmailResult.failure("SMTP send failed: " + e.getMessage(), getType());
    }

    @Override
    public MailProviderType getType() {
        return MailProviderType.SMTP;
    }

    @Override
    public boolean isAvailable() {
        return mailSender != null;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    private MimeMessageHelper createMessageHelper(MimeMessage message, EmailRequest request)
            throws MessagingException {
        boolean multipart = request.hasAttachments() ||
                (request.hasHtmlBody() && request.hasTextBody());
        return new MimeMessageHelper(message, multipart, "UTF-8");
    }

    private void setAddresses(MimeMessageHelper helper, EmailRequest request)
            throws MessagingException, UnsupportedEncodingException {
        // From address
        if (request.getFrom() != null) {
            if (request.getFrom().getName() != null) {
                helper.setFrom(request.getFrom().getAddress(), request.getFrom().getName());
            } else {
                helper.setFrom(request.getFrom().getAddress());
            }
        } else if (defaultFromAddress != null) {
            if (defaultFromName != null) {
                helper.setFrom(defaultFromAddress, defaultFromName);
            } else {
                helper.setFrom(defaultFromAddress);
            }
        }

        // To addresses
        for (EmailAddress to : request.getTo()) {
            if (to.getName() != null) {
                helper.addTo(new InternetAddress(to.getAddress(), to.getName()));
            } else {
                helper.addTo(to.getAddress());
            }
        }

        // CC addresses
        if (request.getCc() != null) {
            for (EmailAddress cc : request.getCc()) {
                if (cc.getName() != null) {
                    helper.addCc(new InternetAddress(cc.getAddress(), cc.getName()));
                } else {
                    helper.addCc(cc.getAddress());
                }
            }
        }

        // BCC addresses
        if (request.getBcc() != null) {
            for (EmailAddress bcc : request.getBcc()) {
                helper.addBcc(bcc.getAddress());
            }
        }

        // Reply-to
        if (request.getReplyTo() != null) {
            if (request.getReplyTo().getName() != null) {
                helper.setReplyTo(request.getReplyTo().getAddress(), request.getReplyTo().getName());
            } else {
                helper.setReplyTo(request.getReplyTo().getAddress());
            }
        }
    }

    private void setContent(MimeMessageHelper helper, EmailRequest request) throws MessagingException {
        helper.setSubject(request.getSubject());

        if (request.hasHtmlBody() && request.hasTextBody()) {
            helper.setText(request.getTextBody(), request.getHtmlBody());
        } else if (request.hasHtmlBody()) {
            helper.setText(request.getHtmlBody(), true);
        } else {
            helper.setText(request.getTextBody(), false);
        }

        // Set priority header
        switch (request.getPriority()) {
            case CRITICAL, HIGH -> helper.setPriority(1);
            case LOW -> helper.setPriority(5);
            default -> helper.setPriority(3);
        }
    }

    private void setAttachments(MimeMessageHelper helper, EmailRequest request) throws MessagingException {
        if (!request.hasAttachments()) {
            return;
        }

        for (EmailAttachment attachment : request.getAttachments()) {
            ByteArrayResource resource = new ByteArrayResource(attachment.getContent());

            if (attachment.isInline() && attachment.getContentId() != null) {
                helper.addInline(attachment.getContentId(), resource, attachment.getContentType());
            } else {
                helper.addAttachment(attachment.getFilename(), resource, attachment.getContentType());
            }
        }
    }
}
