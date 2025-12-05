package dev.simplecore.simplix.email.template;

import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Email template engine using Thymeleaf for variable substitution.
 * <p>
 * Processes email templates with provided variables using Thymeleaf's
 * powerful templating features including:
 * <ul>
 *   <li>Variable expressions: ${variable} for HTML, [(${variable})] for TEXT</li>
 *   <li>Conditional rendering: th:if, th:unless</li>
 *   <li>Iteration: th:each</li>
 *   <li>Text formatting: #dates, #numbers, etc.</li>
 * </ul>
 * <p>
 * Uses separate template engines for TEXT and HTML modes, as StringTemplateResolver
 * requires TemplateMode to be set directly on the resolver, not per-call.
 */
@Slf4j
public class EmailTemplateEngine {

    private final TemplateEngine textTemplateEngine;
    private final TemplateEngine htmlTemplateEngine;

    public EmailTemplateEngine(TemplateEngine textTemplateEngine, TemplateEngine htmlTemplateEngine) {
        this.textTemplateEngine = textTemplateEngine;
        this.htmlTemplateEngine = htmlTemplateEngine;
    }

    /**
     * Process a TEXT mode template string with variables.
     * <p>
     * Use this for subject.txt and body.txt files with [(${...})] syntax.
     *
     * @param template template content with Thymeleaf TEXT expressions
     * @param variables variable map for substitution
     * @param locale locale for formatting
     * @return processed template string
     */
    public String processText(String template, Map<String, Object> variables, Locale locale) {
        if (template == null || template.isBlank()) {
            return template;
        }

        Context context = new Context(locale);
        if (variables != null) {
            context.setVariables(variables);
        }

        try {
            return textTemplateEngine.process(template, context);
        } catch (Exception e) {
            log.error("Failed to process TEXT template: {}", e.getMessage(), e);
            throw new EmailTemplateException("Template processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process an HTML mode template string with variables.
     * <p>
     * Use this for body.html files with ${...} and th:* syntax.
     *
     * @param template template content with Thymeleaf HTML expressions
     * @param variables variable map for substitution
     * @param locale locale for formatting
     * @return processed template string
     */
    public String processHtml(String template, Map<String, Object> variables, Locale locale) {
        if (template == null || template.isBlank()) {
            return template;
        }

        Context context = new Context(locale);
        if (variables != null) {
            context.setVariables(variables);
        }

        try {
            return htmlTemplateEngine.process(template, context);
        } catch (Exception e) {
            log.error("Failed to process HTML template: {}", e.getMessage(), e);
            throw new EmailTemplateException("Template processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process a resolved template with variables.
     * <p>
     * Uses TEXT mode for subject and text body, HTML mode for HTML body.
     *
     * @param resolvedTemplate resolved template data
     * @param variables variable map for substitution
     * @return processed template with subject, HTML body, and text body
     */
    public ProcessedTemplate process(EmailTemplateResolver.ResolvedTemplate resolvedTemplate,
                                     Map<String, Object> variables) {
        Locale locale = resolvedTemplate.locale();

        // Subject uses TEXT mode for [(${...})] syntax
        String subject = processText(resolvedTemplate.subject(), variables, locale);

        // HTML body uses HTML mode
        String htmlBody = resolvedTemplate.hasHtmlBody() ?
                processHtml(resolvedTemplate.htmlBody(), variables, locale) : null;

        // Text body uses TEXT mode
        String textBody = resolvedTemplate.hasTextBody() ?
                processText(resolvedTemplate.textBody(), variables, locale) : null;

        return new ProcessedTemplate(subject, htmlBody, textBody);
    }

    /**
     * Processed template result.
     */
    public record ProcessedTemplate(
            String subject,
            String htmlBody,
            String textBody
    ) {
        public boolean hasHtmlBody() {
            return htmlBody != null && !htmlBody.isBlank();
        }

        public boolean hasTextBody() {
            return textBody != null && !textBody.isBlank();
        }
    }

    /**
     * Exception thrown when template processing fails.
     */
    public static class EmailTemplateException extends RuntimeException {
        public EmailTemplateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
