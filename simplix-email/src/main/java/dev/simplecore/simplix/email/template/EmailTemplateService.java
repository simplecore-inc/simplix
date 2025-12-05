package dev.simplecore.simplix.email.template;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.model.EmailRequest;
import dev.simplecore.simplix.email.model.TemplateEmailRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service for processing email templates.
 * <p>
 * Coordinates between template resolvers and the template engine to
 * convert template-based email requests into ready-to-send email requests.
 */
@Slf4j
public class EmailTemplateService {

    private final List<EmailTemplateResolver> resolvers;
    private final EmailTemplateEngine templateEngine;

    public EmailTemplateService(List<EmailTemplateResolver> resolvers, EmailTemplateEngine templateEngine) {
        // Sort resolvers by priority (highest first)
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(EmailTemplateResolver::getPriority).reversed())
                .toList();
        this.templateEngine = templateEngine;
    }

    /**
     * Process a template email request into a standard email request.
     *
     * @param request template email request
     * @return processed email request ready for sending
     * @throws TemplateNotFoundException if template cannot be found
     */
    public EmailRequest processTemplate(TemplateEmailRequest request) {
        EmailTemplateResolver.ResolvedTemplate resolvedTemplate = resolveTemplate(
                request.getTemplateCode(),
                request.getLocale()
        );

        EmailTemplateEngine.ProcessedTemplate processedTemplate = templateEngine.process(
                resolvedTemplate,
                request.getVariables()
        );

        return EmailRequest.builder()
                .from(request.getFrom())
                .to(request.getTo())
                .cc(request.getCc())
                .bcc(request.getBcc())
                .replyTo(request.getReplyTo())
                .subject(request.getSubject() != null ? request.getSubject() : processedTemplate.subject())
                .htmlBody(processedTemplate.htmlBody())
                .textBody(processedTemplate.textBody())
                .attachments(request.getAttachments())
                .priority(request.getPriority())
                .tenantId(request.getTenantId())
                .correlationId(request.getCorrelationId())
                .tags(request.getTags())
                .build();
    }

    /**
     * Process a template with personalized variables for a single recipient.
     *
     * @param templateCode template identifier
     * @param to recipient address
     * @param variables template variables
     * @param locale locale for template selection
     * @return processed email request
     */
    public EmailRequest processTemplate(String templateCode, EmailAddress to,
                                        Map<String, Object> variables, Locale locale) {
        EmailTemplateResolver.ResolvedTemplate resolvedTemplate = resolveTemplate(templateCode, locale);
        EmailTemplateEngine.ProcessedTemplate processedTemplate = templateEngine.process(
                resolvedTemplate,
                variables
        );

        return EmailRequest.builder()
                .to(List.of(to))
                .subject(processedTemplate.subject())
                .htmlBody(processedTemplate.htmlBody())
                .textBody(processedTemplate.textBody())
                .build();
    }

    /**
     * Render a TEXT mode template string with variables.
     * <p>
     * Uses TEXT mode template engine for [(${...})] syntax.
     *
     * @param template template string with Thymeleaf TEXT expressions
     * @param variables template variables
     * @param locale locale for formatting
     * @return rendered string
     */
    public String render(String template, Map<String, Object> variables, Locale locale) {
        return templateEngine.processText(template, variables, locale);
    }

    /**
     * Check if a template exists.
     *
     * @param templateCode template identifier
     * @return true if template is available
     */
    public boolean templateExists(String templateCode) {
        return resolvers.stream().anyMatch(r -> r.exists(templateCode));
    }

    private EmailTemplateResolver.ResolvedTemplate resolveTemplate(String templateCode, Locale locale) {
        for (EmailTemplateResolver resolver : resolvers) {
            Optional<EmailTemplateResolver.ResolvedTemplate> resolved = resolver.resolve(templateCode, locale);
            if (resolved.isPresent()) {
                log.debug("Template '{}' resolved by {}", templateCode, resolver.getClass().getSimpleName());
                return resolved.get();
            }
        }

        throw new TemplateNotFoundException("Email template not found: " + templateCode);
    }

    /**
     * Exception thrown when a template cannot be found.
     */
    public static class TemplateNotFoundException extends RuntimeException {
        public TemplateNotFoundException(String message) {
            super(message);
        }
    }
}
