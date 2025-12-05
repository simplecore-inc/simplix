package dev.simplecore.simplix.email.provider;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailAttachment;
import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.EmailResult;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Console email provider for development and testing.
 * <p>
 * This provider does not send actual emails. Instead, it logs the email
 * content to the console in a formatted box for easy visibility during
 * development.
 * <p>
 * Use this provider in local development environments to verify email
 * content without sending real emails.
 */
@Slf4j
public class ConsoleEmailProvider extends AbstractEmailProvider {

    private static final String BOX_LINE = "═".repeat(80);

    private final boolean enabled;

    public ConsoleEmailProvider() {
        this(true);
    }

    public ConsoleEmailProvider(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected EmailResult doSend(EmailRequest request) {
        String messageId = "console-" + UUID.randomUUID();
        printEmailBox(request, messageId);
        return EmailResult.success(messageId, getType());
    }

    @Override
    public MailProviderType getType() {
        return MailProviderType.CONSOLE;
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return -100; // Lowest priority, only use if no other provider is available
    }

    private void printEmailBox(EmailRequest request, String messageId) {
        StringBuilder box = new StringBuilder();
        box.append("\n");
        box.append("╔").append(BOX_LINE).append("╗\n");
        box.append(formatLine("EMAIL (Console Provider - Not Actually Sent)"));
        box.append(formatLine(""));
        box.append(formatLine("Message ID: " + messageId));
        box.append("╠").append(BOX_LINE).append("╣\n");

        // From
        if (request.getFrom() != null) {
            box.append(formatLine("From: " + request.getFrom().toFormattedString()));
        }

        // To
        String toAddresses = request.getTo().stream()
                .map(EmailAddress::toFormattedString)
                .collect(Collectors.joining(", "));
        box.append(formatLine("To: " + truncate(toAddresses, 70)));

        // CC
        if (request.getCc() != null && !request.getCc().isEmpty()) {
            String ccAddresses = request.getCc().stream()
                    .map(EmailAddress::toFormattedString)
                    .collect(Collectors.joining(", "));
            box.append(formatLine("Cc: " + truncate(ccAddresses, 70)));
        }

        // BCC
        if (request.getBcc() != null && !request.getBcc().isEmpty()) {
            box.append(formatLine("Bcc: [" + request.getBcc().size() + " recipients]"));
        }

        // Subject
        box.append(formatLine("Subject: " + truncate(request.getSubject(), 65)));
        box.append(formatLine("Priority: " + request.getPriority()));

        // Attachments
        if (request.hasAttachments()) {
            box.append("╠").append(BOX_LINE).append("╣\n");
            box.append(formatLine("Attachments (" + request.getAttachments().size() + "):"));
            for (EmailAttachment attachment : request.getAttachments()) {
                String attachInfo = String.format("  - %s (%s, %d bytes)",
                        attachment.getFilename(),
                        attachment.getContentType(),
                        attachment.getSize());
                box.append(formatLine(truncate(attachInfo, 75)));
            }
        }

        // Body preview
        box.append("╠").append(BOX_LINE).append("╣\n");
        if (request.hasHtmlBody()) {
            box.append(formatLine("HTML Body Preview:"));
            String htmlPreview = stripHtml(request.getHtmlBody());
            for (String line : splitIntoLines(htmlPreview, 76)) {
                box.append(formatLine("  " + line));
            }
        } else if (request.hasTextBody()) {
            box.append(formatLine("Text Body Preview:"));
            for (String line : splitIntoLines(request.getTextBody(), 76)) {
                box.append(formatLine("  " + line));
            }
        }

        box.append("╚").append(BOX_LINE).append("╝\n");

        log.info(box.toString());
    }

    private String formatLine(String text) {
        int contentWidth = 80;
        String truncated = truncate(text, contentWidth);
        int padding = contentWidth - truncated.length();
        return "║ " + truncated + " ".repeat(Math.max(0, padding - 1)) + "║\n";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<p>", "\n")
                .replaceAll("</p>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String[] splitIntoLines(String text, int maxLineLength) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        String truncated = text.length() > 500 ? text.substring(0, 500) + "..." : text;
        return truncated.split("(?<=\\G.{" + maxLineLength + "})");
    }
}
