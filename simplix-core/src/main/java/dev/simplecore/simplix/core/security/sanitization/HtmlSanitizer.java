package dev.simplecore.simplix.core.security.sanitization;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for sanitizing HTML content to prevent XSS attacks.
 * Uses OWASP Java HTML Sanitizer library.
 */
public final class HtmlSanitizer {

    // Pre-built policies for common use cases
    private static final PolicyFactory STRICT_POLICY = new HtmlPolicyBuilder()
        .toFactory();

    private static final PolicyFactory BASIC_FORMATTING_POLICY = new HtmlPolicyBuilder()
        .allowElements("b", "i", "u", "em", "strong", "p", "br", "span", "div")
        .allowElements("h1", "h2", "h3", "h4", "h5", "h6")
        .allowElements("ul", "ol", "li")
        .allowElements("blockquote", "pre", "code")
        .toFactory();

    private static final PolicyFactory LINKS_POLICY = new HtmlPolicyBuilder()
        .allowElements("a")
        .allowAttributes("href", "title").onElements("a")
        .allowStandardUrlProtocols()
        .requireRelNofollowOnLinks()
        .toFactory();

    private static final PolicyFactory TABLES_POLICY = new HtmlPolicyBuilder()
        .allowElements("table", "thead", "tbody", "tfoot", "tr", "td", "th")
        .allowAttributes("colspan", "rowspan").onElements("td", "th")
        .toFactory();

    private static final PolicyFactory IMAGES_POLICY = new HtmlPolicyBuilder()
        .allowElements("img")
        .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
        .allowStandardUrlProtocols()
        .toFactory();

    // Pattern for detecting encoded scripts
    private static final Pattern ENCODED_SCRIPT_PATTERN = Pattern.compile(
        "(%3C|&lt;|\\\\x3c|\\\\u003c)script",
        Pattern.CASE_INSENSITIVE
    );

    private HtmlSanitizer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Sanitize HTML content with default strict policy (strips all HTML)
     */
    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return STRICT_POLICY.sanitize(html);
    }

    /**
     * Sanitize HTML with specified options
     */
    public static String sanitize(String html, boolean allowBasicFormatting, boolean allowLinks, String[] customTags) {
        if (html == null) {
            return null;
        }

        // Check for encoded malicious content first
        if (containsEncodedMaliciousContent(html)) {
            // If encoded malicious content detected, strip all HTML
            return STRICT_POLICY.sanitize(html);
        }

        PolicyFactory policy = buildPolicy(allowBasicFormatting, allowLinks, customTags);
        return policy.sanitize(html);
    }

    /**
     * Sanitize with predefined common policies
     */
    public static String sanitizeWithPolicy(String html, SanitizationPolicy policy) {
        if (html == null) {
            return null;
        }

		return switch (policy) {
			case STRICT -> STRICT_POLICY.sanitize(html);
			case BASIC_FORMATTING -> BASIC_FORMATTING_POLICY.sanitize(html);
			case FORMATTING_WITH_LINKS -> BASIC_FORMATTING_POLICY.and(LINKS_POLICY).sanitize(html);
			case RICH_TEXT -> BASIC_FORMATTING_POLICY
				.and(LINKS_POLICY)
				.and(TABLES_POLICY)
				.and(IMAGES_POLICY)
				.sanitize(html);
			case PLAIN_TEXT ->
				// Remove all HTML tags but preserve text content
				Sanitizers.FORMATTING.and(Sanitizers.BLOCKS).sanitize(html)
					.replaceAll("<[^>]+>", "");
			default -> STRICT_POLICY.sanitize(html);
		};
    }

    /**
     * Build custom policy based on options
     */
    private static PolicyFactory buildPolicy(boolean allowBasicFormatting, boolean allowLinks, String[] customTags) {
        HtmlPolicyBuilder builder = new HtmlPolicyBuilder();

        if (allowBasicFormatting) {
            builder.allowElements("b", "i", "u", "em", "strong", "p", "br", "span")
                   .allowElements("h1", "h2", "h3", "h4", "h5", "h6")
                   .allowElements("ul", "ol", "li");
        }

        if (allowLinks) {
            builder.allowElements("a")
                   .allowAttributes("href", "title").onElements("a")
                   .allowStandardUrlProtocols()
                   .requireRelNofollowOnLinks();
        }

        if (customTags != null && customTags.length > 0) {
            // Validate custom tags to prevent dangerous elements
            Set<String> safeTags = filterSafeTags(customTags);
            if (!safeTags.isEmpty()) {
                builder.allowElements(safeTags.toArray(new String[0]));
            }
        }

        return builder.toFactory();
    }

    /**
     * Filter out potentially dangerous tags from custom tag list
     */
    private static Set<String> filterSafeTags(String[] tags) {
        Set<String> dangerousTags = new HashSet<>(Arrays.asList(
            "script", "iframe", "object", "embed", "applet", "meta", "link",
            "style", "base", "form", "input", "button", "select", "textarea",
            "frame", "frameset", "svg", "math", "video", "audio", "canvas"
        ));

        Set<String> safeTags = new HashSet<>();
        for (String tag : tags) {
            String lowerTag = tag.toLowerCase().trim();
            if (!dangerousTags.contains(lowerTag)) {
                safeTags.add(lowerTag);
            }
        }

        return safeTags;
    }

    /**
     * Check for encoded malicious content that might bypass sanitization
     */
    private static boolean containsEncodedMaliciousContent(String html) {
        if (html == null) {
            return false;
        }

        String lowerHtml = html.toLowerCase();

        // Check for various encoding attempts
        if (ENCODED_SCRIPT_PATTERN.matcher(lowerHtml).find()) {
            return true;
        }

        // Check for data URI schemes
        if (lowerHtml.contains("data:text/html") ||
            lowerHtml.contains("data:application/javascript")) {
            return true;
        }

        // Check for various encoded event handlers
		return lowerHtml.matches(".*(%6F|o|%4F)(%6E|n|%4E)\\w+%3D.*") ||
			lowerHtml.matches(".*&#x6F;&#x6E;.*") ||
			lowerHtml.matches(".*&#111;&#110;.*");
	}

    /**
     * Escape HTML for display (converts HTML to text representation)
     */
    public static String escapeHtml(String html) {
        if (html == null) {
            return null;
        }

        return html.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;")
                   .replace("/", "&#x2F;");
    }

    /**
     * Check if HTML contains any potentially dangerous content
     */
    public static boolean containsDangerousContent(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        String original = html;
        String sanitized = STRICT_POLICY.sanitize(html);

        // If content changed after sanitization, it contained HTML
        if (!original.equals(sanitized)) {
            // Check if it's just harmless formatting
            String formattingSanitized = BASIC_FORMATTING_POLICY.sanitize(html);
            return !original.equals(formattingSanitized);
        }

        return containsEncodedMaliciousContent(html);
    }

    /**
     * Predefined sanitization policies
     */
    public enum SanitizationPolicy {
        STRICT,                 // No HTML allowed
        PLAIN_TEXT,            // Strip all HTML, keep text only
        BASIC_FORMATTING,      // Basic text formatting only
        FORMATTING_WITH_LINKS, // Formatting + safe links
        RICH_TEXT             // Full rich text (formatting, links, tables, images)
    }
}