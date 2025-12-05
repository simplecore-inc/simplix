package dev.simplecore.simplix.email.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Email template resolver that loads templates from classpath resources.
 * <p>
 * Templates are expected in the following structure:
 * <pre>{@code
 * templates/email/
 *   {templateCode}/
 *     en/                   - English (default fallback)
 *       subject.txt
 *       body.html
 *       body.txt
 *     ko/                   - Korean
 *       subject.txt
 *       body.html
 *       body.txt
 *     ja/                   - Japanese
 *       subject.txt
 *       body.html
 *       body.txt
 * }</pre>
 */
@Slf4j
public class ClasspathEmailTemplateResolver implements EmailTemplateResolver {

    private static final String DEFAULT_BASE_PATH = "templates/email";
    private static final String DEFAULT_LOCALE = "en";

    private final String basePath;

    public ClasspathEmailTemplateResolver() {
        this(DEFAULT_BASE_PATH);
    }

    public ClasspathEmailTemplateResolver(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public Optional<ResolvedTemplate> resolve(String templateCode, Locale locale) {
        String templatePath = basePath + "/" + templateCode;

        // Try to load subject
        String subject = loadTemplateFile(templatePath, "subject", "txt", locale);
        if (subject == null) {
            log.debug("Template not found: {} (no subject file)", templateCode);
            return Optional.empty();
        }

        // Load HTML body
        String htmlBody = loadTemplateFile(templatePath, "body", "html", locale);

        // Load text body (optional)
        String textBody = loadTemplateFile(templatePath, "body", "txt", locale);

        if (htmlBody == null && textBody == null) {
            log.debug("Template not found: {} (no body files)", templateCode);
            return Optional.empty();
        }

        return Optional.of(new ResolvedTemplate(
                templateCode,
                subject.trim(),
                htmlBody,
                textBody,
                locale
        ));
    }

    @Override
    public boolean exists(String templateCode) {
        // Check if template exists in default locale folder
        String subjectPath = basePath + "/" + templateCode + "/" + DEFAULT_LOCALE + "/subject.txt";
        Resource resource = new ClassPathResource(subjectPath);
        return resource.exists();
    }

    @Override
    public int getPriority() {
        return 10; // Lower priority than database resolver
    }

    private String loadTemplateFile(String templatePath, String filename, String extension, Locale locale) {
        // Try locale-specific folder first
        String localeFolder = locale.getLanguage();
        String localeFilePath = templatePath + "/" + localeFolder + "/" + filename + "." + extension;

        Resource localeResource = new ClassPathResource(localeFilePath);
        if (localeResource.exists()) {
            return readResource(localeResource);
        }

        // Fall back to default locale folder (en)
        if (!DEFAULT_LOCALE.equals(localeFolder)) {
            String defaultFilePath = templatePath + "/" + DEFAULT_LOCALE + "/" + filename + "." + extension;
            Resource defaultResource = new ClassPathResource(defaultFilePath);
            if (defaultResource.exists()) {
                return readResource(defaultResource);
            }
        }

        return null;
    }

    private String readResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read template resource: {}", resource.getDescription(), e);
            return null;
        }
    }
}
