package dev.simplecore.simplix.email.template;

import java.util.Locale;
import java.util.Optional;

/**
 * Interface for resolving email templates.
 * <p>
 * Implementations can resolve templates from different sources:
 * <ul>
 *   <li>Database (via EmailTemplate entity)</li>
 *   <li>Filesystem (classpath resources)</li>
 *   <li>Remote storage (S3, etc.)</li>
 * </ul>
 */
public interface EmailTemplateResolver {

    /**
     * Resolve template by code and locale.
     *
     * @param templateCode unique template identifier
     * @param locale desired locale (falls back to default if not found)
     * @return resolved template data or empty if not found
     */
    Optional<ResolvedTemplate> resolve(String templateCode, Locale locale);

    /**
     * Check if a template exists.
     *
     * @param templateCode template identifier
     * @return true if template exists
     */
    boolean exists(String templateCode);

    /**
     * Get resolver priority (higher = checked first).
     *
     * @return priority value
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Resolved template data holder.
     */
    record ResolvedTemplate(
            String code,
            String subject,
            String htmlBody,
            String textBody,
            Locale locale
    ) {
        public boolean hasHtmlBody() {
            return htmlBody != null && !htmlBody.isBlank();
        }

        public boolean hasTextBody() {
            return textBody != null && !textBody.isBlank();
        }
    }
}
