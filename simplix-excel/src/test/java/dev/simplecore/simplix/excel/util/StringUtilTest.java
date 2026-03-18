package dev.simplecore.simplix.excel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StringUtil")
class StringUtilTest {

    @Nested
    @DisplayName("hasText")
    class HasTextTests {

        @Test
        @DisplayName("should return true for string with content")
        void shouldReturnTrueForContent() {
            assertThat(StringUtil.hasText("hello")).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(StringUtil.hasText(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for empty string")
        void shouldReturnFalseForEmpty() {
            assertThat(StringUtil.hasText("")).isFalse();
        }

        @Test
        @DisplayName("should return false for whitespace-only string")
        void shouldReturnFalseForWhitespace() {
            assertThat(StringUtil.hasText("   ")).isFalse();
        }

        @Test
        @DisplayName("should return true for string with leading/trailing spaces")
        void shouldReturnTrueWithSpaces() {
            assertThat(StringUtil.hasText("  hello  ")).isTrue();
        }
    }

    @Nested
    @DisplayName("isEmpty")
    class IsEmptyTests {

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(StringUtil.isEmpty(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty string")
        void shouldReturnTrueForEmpty() {
            assertThat(StringUtil.isEmpty("")).isTrue();
        }

        @Test
        @DisplayName("should return false for whitespace-only string")
        void shouldReturnFalseForWhitespace() {
            assertThat(StringUtil.isEmpty("  ")).isFalse();
        }

        @Test
        @DisplayName("should return false for non-empty string")
        void shouldReturnFalseForContent() {
            assertThat(StringUtil.isEmpty("hello")).isFalse();
        }
    }

    @Nested
    @DisplayName("isBlank")
    class IsBlankTests {

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(StringUtil.isBlank(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty string")
        void shouldReturnTrueForEmpty() {
            assertThat(StringUtil.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("should return true for whitespace-only string")
        void shouldReturnTrueForWhitespace() {
            assertThat(StringUtil.isBlank("   \t\n")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-blank string")
        void shouldReturnFalseForContent() {
            assertThat(StringUtil.isBlank("hello")).isFalse();
        }
    }

    @Nested
    @DisplayName("defaultIfEmpty")
    class DefaultIfEmptyTests {

        @Test
        @DisplayName("should return default value for null string")
        void shouldReturnDefaultForNull() {
            assertThat(StringUtil.defaultIfEmpty(null, "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("should return default value for empty string")
        void shouldReturnDefaultForEmpty() {
            assertThat(StringUtil.defaultIfEmpty("", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("should return original string when not empty")
        void shouldReturnOriginal() {
            assertThat(StringUtil.defaultIfEmpty("hello", "default")).isEqualTo("hello");
        }

        @Test
        @DisplayName("should return whitespace string (not empty)")
        void shouldReturnWhitespace() {
            assertThat(StringUtil.defaultIfEmpty("  ", "default")).isEqualTo("  ");
        }
    }
}
