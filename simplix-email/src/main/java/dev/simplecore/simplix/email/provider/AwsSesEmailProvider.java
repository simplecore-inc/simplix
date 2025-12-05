package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.RawMessage;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * AWS Simple Email Service (SES) v2 email provider.
 * <p>
 * Uses AWS SES v2 API for sending emails. Recommended for production
 * environments with high email volume.
 * <p>
 * Configuration requires AWS credentials and region:
 * <pre>{@code
 * aws:
 *   ses:
 *     region: us-east-1
 *     from-address: noreply@example.com
 * }</pre>
 * <p>
 * AWS credentials can be configured via:
 * <ul>
 *   <li>Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)</li>
 *   <li>AWS credentials file (~/.aws/credentials)</li>
 *   <li>IAM role (when running on AWS)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class AwsSesEmailProvider extends AbstractEmailProvider {

    private final SesV2Client sesClient;
    private final String defaultFromAddress;
    private final String configurationSetName;

    @Override
    protected EmailResult doSend(EmailRequest request) throws Exception {
        MimeMessage mimeMessage = createMimeMessage(request);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);

        SendEmailRequest.Builder sendRequestBuilder = SendEmailRequest.builder()
                .content(EmailContent.builder()
                        .raw(RawMessage.builder()
                                .data(SdkBytes.fromByteBuffer(ByteBuffer.wrap(outputStream.toByteArray())))
                                .build())
                        .build());

        if (configurationSetName != null && !configurationSetName.isBlank()) {
            sendRequestBuilder.configurationSetName(configurationSetName);
        }

        SendEmailResponse response = sesClient.sendEmail(sendRequestBuilder.build());
        return EmailResult.success(response.messageId(), getType());
    }

    @Override
    protected EmailResult handleException(Exception e) {
        if (e instanceof SesV2Exception sesException) {
            String errorCode = sesException.awsErrorDetails().errorCode();
            String errorMessage = sesException.awsErrorDetails().errorMessage();

            if ("Throttling".equals(errorCode) || "ServiceUnavailable".equals(errorCode)) {
                return EmailResult.retryableFailure(
                        "AWS SES temporary error: " + errorMessage,
                        errorCode,
                        getType()
                );
            }

            return EmailResult.failure("AWS SES error [" + errorCode + "]: " + errorMessage, getType());
        }
        return EmailResult.failure("AWS SES send failed: " + e.getMessage(), getType());
    }

    @Override
    public MailProviderType getType() {
        return MailProviderType.AWS_SES;
    }

    @Override
    public boolean isAvailable() {
        return sesClient != null;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean supportsBulkSend() {
        return true;
    }

    private MimeMessage createMimeMessage(EmailRequest request) throws Exception {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);

        String fromAddress = request.getFrom() != null ?
                request.getFrom().getAddress() : defaultFromAddress;
        String fromName = request.getFrom() != null ?
                request.getFrom().getName() : null;

        if (fromName != null) {
            message.setFrom(new InternetAddress(fromAddress, fromName));
        } else {
            message.setFrom(new InternetAddress(fromAddress));
        }

        for (EmailAddress to : request.getTo()) {
            if (to.getName() != null) {
                message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(to.getAddress(), to.getName()));
            } else {
                message.addRecipient(Message.RecipientType.TO,
                        new InternetAddress(to.getAddress()));
            }
        }

        if (request.getCc() != null) {
            for (EmailAddress cc : request.getCc()) {
                message.addRecipient(Message.RecipientType.CC,
                        new InternetAddress(cc.getAddress()));
            }
        }

        if (request.getBcc() != null) {
            for (EmailAddress bcc : request.getBcc()) {
                message.addRecipient(Message.RecipientType.BCC,
                        new InternetAddress(bcc.getAddress()));
            }
        }

        if (request.getReplyTo() != null) {
            message.setReplyTo(new InternetAddress[]{
                    new InternetAddress(request.getReplyTo().getAddress())
            });
        }

        message.setSubject(request.getSubject(), "UTF-8");

        if (request.hasAttachments()) {
            MimeMultipart multipart = new MimeMultipart("mixed");

            MimeBodyPart bodyPart = new MimeBodyPart();
            if (request.hasHtmlBody()) {
                if (request.hasTextBody()) {
                    MimeMultipart alternative = new MimeMultipart("alternative");
                    MimeBodyPart textPart = new MimeBodyPart();
                    textPart.setText(request.getTextBody(), "UTF-8");
                    alternative.addBodyPart(textPart);

                    MimeBodyPart htmlPart = new MimeBodyPart();
                    htmlPart.setContent(request.getHtmlBody(), "text/html; charset=UTF-8");
                    alternative.addBodyPart(htmlPart);

                    bodyPart.setContent(alternative);
                } else {
                    bodyPart.setContent(request.getHtmlBody(), "text/html; charset=UTF-8");
                }
            } else {
                bodyPart.setText(request.getTextBody(), "UTF-8");
            }
            multipart.addBodyPart(bodyPart);

            for (EmailAttachment attachment : request.getAttachments()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                ByteArrayDataSource dataSource = new ByteArrayDataSource(
                        attachment.getContent(),
                        attachment.getContentType()
                );
                attachmentPart.setDataHandler(new DataHandler(dataSource));
                attachmentPart.setFileName(attachment.getFilename());

                if (attachment.isInline() && attachment.getContentId() != null) {
                    attachmentPart.setContentID("<" + attachment.getContentId() + ">");
                    attachmentPart.setDisposition(MimeBodyPart.INLINE);
                }

                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);
        } else {
            if (request.hasHtmlBody()) {
                if (request.hasTextBody()) {
                    MimeMultipart alternative = new MimeMultipart("alternative");
                    MimeBodyPart textPart = new MimeBodyPart();
                    textPart.setText(request.getTextBody(), "UTF-8");
                    alternative.addBodyPart(textPart);

                    MimeBodyPart htmlPart = new MimeBodyPart();
                    htmlPart.setContent(request.getHtmlBody(), "text/html; charset=UTF-8");
                    alternative.addBodyPart(htmlPart);

                    message.setContent(alternative);
                } else {
                    message.setContent(request.getHtmlBody(), "text/html; charset=UTF-8");
                }
            } else {
                message.setText(request.getTextBody(), "UTF-8");
            }
        }

        if (request.getHeaders() != null) {
            for (var entry : request.getHeaders().entrySet()) {
                message.addHeader(entry.getKey(), entry.getValue());
            }
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            message.addHeader("X-SES-MESSAGE-TAGS",
                    request.getTags().stream()
                            .map(tag -> "tag=" + tag)
                            .collect(Collectors.joining(", ")));
        }

        return message;
    }
}
