package dev.simplecore.simplix.core.security.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InputSanitizer")
class InputSanitizerTest {

    @Nested
    @DisplayName("sanitizeForSql")
    class SanitizeForSql {

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeForSql(null)).isNull();
        }

        @Test
        @DisplayName("should sanitize safe input preserving alphanumeric content")
        void shouldSanitizeSafeInput() {
            String result = InputSanitizer.sanitizeForSql("hello world");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return empty string for SQL injection attempt")
        void shouldReturnEmptyForSqlInjection() {
            String result = InputSanitizer.sanitizeForSql("' OR 1=1 --");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("sanitizeForHtml")
    class SanitizeForHtml {

        @Test
        @DisplayName("should escape HTML tags")
        void shouldEscapeHtmlTags() {
            String result = InputSanitizer.sanitizeForHtml("<script>alert('xss')</script>");

            assertThat(result).doesNotContain("<script>");
            assertThat(result).contains("&lt;");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeForHtml(null)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeForJavaScript")
    class SanitizeForJavaScript {

        @Test
        @DisplayName("should escape JavaScript special characters")
        void shouldEscapeJsChars() {
            String result = InputSanitizer.sanitizeForJavaScript("test'\"<>");

            assertThat(result).contains("\\'");
            assertThat(result).contains("\\\"");
            assertThat(result).contains("\\x3C");
            assertThat(result).contains("\\x3E");
        }

        @Test
        @DisplayName("should escape newlines and tabs")
        void shouldEscapeNewlinesAndTabs() {
            String result = InputSanitizer.sanitizeForJavaScript("line1\nline2\ttab");

            assertThat(result).contains("\\n");
            assertThat(result).contains("\\t");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeForJavaScript(null)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeForLdap")
    class SanitizeForLdap {

        @Test
        @DisplayName("should remove LDAP special characters")
        void shouldRemoveLdapChars() {
            String result = InputSanitizer.sanitizeForLdap("test*()|&");

            assertThat(result).isEqualTo("test");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeForLdap(null)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeForOsCommand")
    class SanitizeForOsCommand {

        @Test
        @DisplayName("should remove OS command special characters")
        void shouldRemoveOsChars() {
            String result = InputSanitizer.sanitizeForOsCommand("file.txt; rm -rf &");

            assertThat(result).doesNotContain(";");
            assertThat(result).doesNotContain("&");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeForOsCommand(null)).isNull();
        }
    }

    @Nested
    @DisplayName("sanitizeFileName")
    class SanitizeFileName {

        @Test
        @DisplayName("should remove path traversal attempts")
        void shouldRemovePathTraversal() {
            String result = InputSanitizer.sanitizeFileName("../../etc/passwd");

            assertThat(result).doesNotContain("..");
            assertThat(result).doesNotContain("/");
        }

        @Test
        @DisplayName("should keep safe characters")
        void shouldKeepSafeCharacters() {
            String result = InputSanitizer.sanitizeFileName("document-v2.pdf");

            assertThat(result).isEqualTo("document-v2.pdf");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.sanitizeFileName(null)).isNull();
        }
    }

    @Nested
    @DisplayName("isValidEmail")
    class IsValidEmail {

        @Test
        @DisplayName("should return true for valid email")
        void shouldReturnTrueForValidEmail() {
            assertThat(InputSanitizer.isValidEmail("user@example.com")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid email")
        void shouldReturnFalseForInvalidEmail() {
            assertThat(InputSanitizer.isValidEmail("not-an-email")).isFalse();
            assertThat(InputSanitizer.isValidEmail("@example.com")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(InputSanitizer.isValidEmail(null)).isFalse();
            assertThat(InputSanitizer.isValidEmail("")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidUrl")
    class IsValidUrl {

        @Test
        @DisplayName("should return true for valid HTTP URL")
        void shouldReturnTrueForValidHttp() {
            assertThat(InputSanitizer.isValidUrl("http://example.com")).isTrue();
            assertThat(InputSanitizer.isValidUrl("https://example.com/path")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid URL")
        void shouldReturnFalseForInvalidUrl() {
            assertThat(InputSanitizer.isValidUrl("ftp://example.com")).isFalse();
            assertThat(InputSanitizer.isValidUrl("not-a-url")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(InputSanitizer.isValidUrl(null)).isFalse();
            assertThat(InputSanitizer.isValidUrl("")).isFalse();
        }
    }

    @Nested
    @DisplayName("isAlphanumeric")
    class IsAlphanumeric {

        @Test
        @DisplayName("should return true for alphanumeric input")
        void shouldReturnTrueForAlphanumeric() {
            assertThat(InputSanitizer.isAlphanumeric("abc123")).isTrue();
        }

        @Test
        @DisplayName("should return false for input with spaces")
        void shouldReturnFalseForSpaces() {
            assertThat(InputSanitizer.isAlphanumeric("abc 123")).isFalse();
        }

        @Test
        @DisplayName("should return false for special characters")
        void shouldReturnFalseForSpecialChars() {
            assertThat(InputSanitizer.isAlphanumeric("abc@123")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(InputSanitizer.isAlphanumeric(null)).isFalse();
            assertThat(InputSanitizer.isAlphanumeric("")).isFalse();
        }
    }

    @Nested
    @DisplayName("isAlphanumericWithSpace")
    class IsAlphanumericWithSpace {

        @Test
        @DisplayName("should return true for alphanumeric input with spaces")
        void shouldReturnTrueWithSpaces() {
            assertThat(InputSanitizer.isAlphanumericWithSpace("abc 123")).isTrue();
        }

        @Test
        @DisplayName("should return false for special characters")
        void shouldReturnFalseForSpecialChars() {
            assertThat(InputSanitizer.isAlphanumericWithSpace("abc@123")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidUUID")
    class IsValidUUID {

        @Test
        @DisplayName("should return true for valid UUID")
        void shouldReturnTrueForValidUuid() {
            assertThat(InputSanitizer.isValidUUID("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid UUID")
        void shouldReturnFalseForInvalidUuid() {
            assertThat(InputSanitizer.isValidUUID("not-a-uuid")).isFalse();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(InputSanitizer.isValidUUID(null)).isFalse();
            assertThat(InputSanitizer.isValidUUID("")).isFalse();
        }
    }

    @Nested
    @DisplayName("escapeLikePattern")
    class EscapeLikePattern {

        @Test
        @DisplayName("should escape LIKE wildcards")
        void shouldEscapeLikeWildcards() {
            String result = InputSanitizer.escapeLikePattern("100% of_users");

            assertThat(result).contains("\\%");
            assertThat(result).contains("\\_");
        }

        @Test
        @DisplayName("should escape backslash")
        void shouldEscapeBackslash() {
            String result = InputSanitizer.escapeLikePattern("path\\to\\file");

            assertThat(result).contains("\\\\");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(InputSanitizer.escapeLikePattern(null)).isNull();
        }
    }
}
