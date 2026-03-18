package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlSanitizer")
class HtmlSanitizerTest {

    @Nested
    @DisplayName("sanitize (strict)")
    class SanitizeStrict {

        @Test
        @DisplayName("should strip all HTML tags by default")
        void shouldStripAllHtml() {
            String result = HtmlSanitizer.sanitize("<p>Hello <b>World</b></p>");

            assertThat(result).doesNotContain("<p>");
            assertThat(result).doesNotContain("<b>");
            assertThat(result).contains("Hello");
            assertThat(result).contains("World");
        }

        @Test
        @DisplayName("should remove script tags")
        void shouldRemoveScriptTags() {
            String result = HtmlSanitizer.sanitize("<script>alert('xss')</script>");

            assertThat(result).doesNotContain("<script>");
            assertThat(result).doesNotContain("alert");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.sanitize(null)).isNull();
        }

        @Test
        @DisplayName("should preserve plain text")
        void shouldPreservePlainText() {
            String result = HtmlSanitizer.sanitize("Hello World");

            assertThat(result).isEqualTo("Hello World");
        }
    }

    @Nested
    @DisplayName("sanitize with options")
    class SanitizeWithOptions {

        @Test
        @DisplayName("should allow basic formatting when enabled")
        void shouldAllowBasicFormatting() {
            String result = HtmlSanitizer.sanitize("<b>Bold</b>", true, false, null);

            assertThat(result).contains("<b>");
            assertThat(result).contains("Bold");
        }

        @Test
        @DisplayName("should apply strict policy for encoded malicious content")
        void shouldApplyStrictForEncodedMaliciousContent() {
            // Encoded script pattern triggers strict policy which strips all HTML
            String input = "%3Cscript%3Ealert('xss')%3C/script%3E";
            String result = HtmlSanitizer.sanitize(input, true, true, null);

            // The strict policy sanitizes it; the URL-encoded text remains as literal text
            // but any actual HTML tags would have been stripped
            assertThat(result).doesNotContain("<script>");
        }

        @Test
        @DisplayName("should filter out dangerous custom tags")
        void shouldFilterDangerousTags() {
            String result = HtmlSanitizer.sanitize(
                "<script>evil</script><article>safe</article>",
                false, false, new String[]{"script", "article"}
            );

            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("article");
        }
    }

    @Nested
    @DisplayName("sanitizeWithPolicy")
    class SanitizeWithPolicy {

        @Test
        @DisplayName("should strip all HTML with STRICT policy")
        void shouldStripAllWithStrict() {
            String result = HtmlSanitizer.sanitizeWithPolicy(
                "<b>Bold</b> <script>alert('xss')</script>",
                HtmlSanitizer.SanitizationPolicy.STRICT
            );

            assertThat(result).doesNotContain("<b>");
            assertThat(result).doesNotContain("<script>");
        }

        @Test
        @DisplayName("should allow formatting with BASIC_FORMATTING policy")
        void shouldAllowFormattingWithBasic() {
            String result = HtmlSanitizer.sanitizeWithPolicy(
                "<b>Bold</b> <em>Italic</em>",
                HtmlSanitizer.SanitizationPolicy.BASIC_FORMATTING
            );

            assertThat(result).contains("<b>");
            assertThat(result).contains("<em>");
        }

        @Test
        @DisplayName("should return null for null input with any policy")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.sanitizeWithPolicy(null, HtmlSanitizer.SanitizationPolicy.STRICT)).isNull();
        }
    }

    @Nested
    @DisplayName("escapeHtml")
    class EscapeHtml {

        @Test
        @DisplayName("should escape HTML special characters")
        void shouldEscapeSpecialCharacters() {
            String result = HtmlSanitizer.escapeHtml("<script>alert(\"xss\")</script>");

            assertThat(result).contains("&lt;");
            assertThat(result).contains("&gt;");
            assertThat(result).contains("&quot;");
            assertThat(result).doesNotContain("<script>");
        }

        @Test
        @DisplayName("should escape ampersand")
        void shouldEscapeAmpersand() {
            String result = HtmlSanitizer.escapeHtml("a & b");

            assertThat(result).contains("&amp;");
        }

        @Test
        @DisplayName("should escape single quote and forward slash")
        void shouldEscapeQuoteAndSlash() {
            String result = HtmlSanitizer.escapeHtml("'test'/");

            assertThat(result).contains("&#x27;");
            assertThat(result).contains("&#x2F;");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(HtmlSanitizer.escapeHtml(null)).isNull();
        }
    }

    @Nested
    @DisplayName("containsDangerousContent")
    class ContainsDangerousContent {

        @Test
        @DisplayName("should detect script tags")
        void shouldDetectScriptTags() {
            assertThat(HtmlSanitizer.containsDangerousContent("<script>alert('xss')</script>")).isTrue();
        }

        @Test
        @DisplayName("should return false for plain text")
        void shouldReturnFalseForPlainText() {
            assertThat(HtmlSanitizer.containsDangerousContent("Hello World")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(HtmlSanitizer.containsDangerousContent(null)).isFalse();
            assertThat(HtmlSanitizer.containsDangerousContent("")).isFalse();
        }
    }
}
