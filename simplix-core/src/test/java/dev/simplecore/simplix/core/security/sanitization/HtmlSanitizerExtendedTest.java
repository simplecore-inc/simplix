package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlSanitizer - Extended Coverage")
class HtmlSanitizerExtendedTest {

    @Nested
    @DisplayName("sanitize with options")
    class SanitizeWithOptions {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.sanitize(null, true, true, null)).isNull();
        }

        @Test
        @DisplayName("should detect encoded scripts and apply strict policy")
        void shouldDetectEncodedScriptsAndApplyStrict() {
            // %3Cscript triggers the encoded script pattern detection, so strict policy is applied
            // The strict policy strips HTML tags but preserves the encoded text
            String result = HtmlSanitizer.sanitize("%3Cscript>alert(1)</script>", true, true, null);
            // Verify the actual script tag was removed
            assertThat(result).doesNotContain("<script>").doesNotContain("</script>");
        }

        @Test
        @DisplayName("should allow basic formatting when enabled")
        void shouldAllowBasicFormatting() {
            String result = HtmlSanitizer.sanitize("<b>Bold</b><i>Italic</i>", true, false, null);
            assertThat(result).contains("<b>Bold</b>");
        }

        @Test
        @DisplayName("should allow links when enabled")
        void shouldAllowLinks() {
            String result = HtmlSanitizer.sanitize("<a href=\"https://example.com\">Link</a>", false, true, null);
            assertThat(result).contains("href");
        }

        @Test
        @DisplayName("should allow safe custom tags")
        void shouldAllowSafeCustomTags() {
            String result = HtmlSanitizer.sanitize("<section>Content</section>",
                    false, false, new String[]{"section"});
            assertThat(result).contains("Content");
        }

        @Test
        @DisplayName("should filter dangerous custom tags")
        void shouldFilterDangerousTags() {
            String result = HtmlSanitizer.sanitize("<script>alert(1)</script>",
                    false, false, new String[]{"script"});
            assertThat(result).doesNotContain("<script>");
        }
    }

    @Nested
    @DisplayName("sanitizeWithPolicy")
    class SanitizeWithPolicy {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.sanitizeWithPolicy(null, HtmlSanitizer.SanitizationPolicy.STRICT)).isNull();
        }

        @Test
        @DisplayName("should apply STRICT policy")
        void shouldApplyStrictPolicy() {
            String result = HtmlSanitizer.sanitizeWithPolicy("<b>text</b>", HtmlSanitizer.SanitizationPolicy.STRICT);
            assertThat(result).doesNotContain("<b>");
        }

        @Test
        @DisplayName("should apply BASIC_FORMATTING policy")
        void shouldApplyBasicPolicy() {
            String result = HtmlSanitizer.sanitizeWithPolicy("<b>text</b>", HtmlSanitizer.SanitizationPolicy.BASIC_FORMATTING);
            assertThat(result).contains("<b>");
        }

        @Test
        @DisplayName("should apply FORMATTING_WITH_LINKS policy")
        void shouldApplyFormattingWithLinksPolicy() {
            String html = "<b>text</b> <a href=\"https://example.com\">link</a>";
            String result = HtmlSanitizer.sanitizeWithPolicy(html, HtmlSanitizer.SanitizationPolicy.FORMATTING_WITH_LINKS);
            assertThat(result).contains("<b>").contains("href");
        }

        @Test
        @DisplayName("should apply RICH_TEXT policy")
        void shouldApplyRichTextPolicy() {
            String html = "<table><tr><td>data</td></tr></table>";
            String result = HtmlSanitizer.sanitizeWithPolicy(html, HtmlSanitizer.SanitizationPolicy.RICH_TEXT);
            assertThat(result).contains("<table>");
        }

        @Test
        @DisplayName("should apply PLAIN_TEXT policy")
        void shouldApplyPlainTextPolicy() {
            String result = HtmlSanitizer.sanitizeWithPolicy("<b>text</b>", HtmlSanitizer.SanitizationPolicy.PLAIN_TEXT);
            assertThat(result).contains("text");
        }
    }

    @Nested
    @DisplayName("containsEncodedMaliciousContent")
    class ContainsEncoded {

        @Test
        @DisplayName("should detect data URI schemes")
        void shouldDetectDataUri() {
            assertThat(HtmlSanitizer.containsDangerousContent("data:text/html,<script>alert(1)</script>")).isTrue();
        }

        @Test
        @DisplayName("should detect data application/javascript URI")
        void shouldDetectJsDataUri() {
            assertThat(HtmlSanitizer.containsDangerousContent("data:application/javascript,alert(1)")).isTrue();
        }
    }

    @Nested
    @DisplayName("escapeHtml")
    class EscapeHtml {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.escapeHtml(null)).isNull();
        }

        @Test
        @DisplayName("should escape all special characters")
        void shouldEscapeAll() {
            String result = HtmlSanitizer.escapeHtml("<script>alert('test\"&/x')</script>");
            assertThat(result).contains("&lt;").contains("&gt;")
                    .contains("&#x27;").contains("&quot;").contains("&#x2F;").contains("&amp;");
        }
    }

    @Nested
    @DisplayName("containsDangerousContent")
    class ContainsDangerous {

        @Test
        @DisplayName("should return false for null/empty")
        void shouldReturnFalseForNullEmpty() {
            assertThat(HtmlSanitizer.containsDangerousContent(null)).isFalse();
            assertThat(HtmlSanitizer.containsDangerousContent("")).isFalse();
        }

        @Test
        @DisplayName("should return false for safe text")
        void shouldReturnFalseForSafe() {
            assertThat(HtmlSanitizer.containsDangerousContent("Hello World")).isFalse();
        }

        @Test
        @DisplayName("should return true for script tags")
        void shouldReturnTrueForScripts() {
            assertThat(HtmlSanitizer.containsDangerousContent("<script>alert(1)</script>")).isTrue();
        }

        @Test
        @DisplayName("should return false for basic formatting")
        void shouldReturnFalseForFormatting() {
            assertThat(HtmlSanitizer.containsDangerousContent("<b>bold</b>")).isFalse();
        }
    }
}
