package dev.simplecore.simplix.email.template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Email template resolver that loads templates from database.
 * <p>
 * This resolver uses a function to fetch templates, allowing it to work
 * with any repository implementation without direct dependency.
 * <p>
 * Priority is higher than classpath resolver, so database templates
 * take precedence over file-based templates.
 */
@Slf4j
@RequiredArgsConstructor
public class DatabaseEmailTemplateResolver implements EmailTemplateResolver {

    /**
     * Function to fetch template from database.
     * Parameters: (templateCode, locale) -&gt; Optional template data
     */
    private final BiFunction<String, String, Optional<TemplateData>> templateFetcher;

    /**
     * Current tenant ID provider (can be null for system templates).
     */
    private final java.util.function.Supplier<String> tenantIdProvider;

    @Override
    public Optional<ResolvedTemplate> resolve(String templateCode, Locale locale) {
        String localeStr = locale.getLanguage();
        String tenantId = tenantIdProvider != null ? tenantIdProvider.get() : null;

        // Try tenant-specific template first
        Optional<TemplateData> template = templateFetcher.apply(templateCode, localeStr);

        if (template.isEmpty()) {
            // Try default locale
            template = templateFetcher.apply(templateCode, "en");
        }

        if (template.isEmpty()) {
            log.debug("Template not found in database: {}", templateCode);
            return Optional.empty();
        }

        TemplateData data = template.get();
        return Optional.of(new ResolvedTemplate(
                templateCode,
                data.subject(),
                data.bodyHtml(),
                data.bodyText(),
                locale
        ));
    }

    @Override
    public boolean exists(String templateCode) {
        return templateFetcher.apply(templateCode, "en").isPresent();
    }

    @Override
    public int getPriority() {
        return 100; // Higher priority than classpath resolver
    }

    /**
     * Template data record for database results.
     */
    public record TemplateData(
            String code,
            String subject,
            String bodyHtml,
            String bodyText
    ) {}
}
