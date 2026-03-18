package dev.simplecore.simplix.core.security.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SqlInjectionValidator")
class SqlInjectionValidatorTest {

    private SqlInjectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SqlInjectionValidator();
    }

    @Nested
    @DisplayName("isSafeInput")
    class IsSafeInput {

        @Test
        @DisplayName("should return true for null input")
        void shouldReturnTrueForNull() {
            assertThat(validator.isSafeInput(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty input")
        void shouldReturnTrueForEmpty() {
            assertThat(validator.isSafeInput("")).isTrue();
        }

        @Test
        @DisplayName("should return true for safe text")
        void shouldReturnTrueForSafeText() {
            assertThat(validator.isSafeInput("Hello World")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "' OR 1=1 --",
            "'; DROP TABLE users;",
            "' UNION SELECT * FROM users",
            "/* comment */",
            "EXEC xp_cmdshell",
            "WAITFOR DELAY '0:0:5'",
            "DELETE FROM users"
        })
        @DisplayName("should return false for SQL injection patterns")
        void shouldReturnFalseForInjection(String input) {
            assertThat(validator.isSafeInput(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("matchesSafePattern")
    class MatchesSafePattern {

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(validator.matchesSafePattern(null)).isTrue();
        }

        @Test
        @DisplayName("should return true for alphanumeric input")
        void shouldReturnTrueForAlphanumeric() {
            assertThat(validator.matchesSafePattern("John123")).isTrue();
        }

        @Test
        @DisplayName("should return true for email-like input")
        void shouldReturnTrueForEmailLike() {
            assertThat(validator.matchesSafePattern("user@example.com")).isTrue();
        }

        @Test
        @DisplayName("should return true for names with apostrophes")
        void shouldReturnTrueForNamesWithApostrophes() {
            assertThat(validator.matchesSafePattern("O'Brien")).isTrue();
        }
    }

    @Nested
    @DisplayName("isSafeEmail")
    class IsSafeEmail {

        @Test
        @DisplayName("should return true for valid safe email")
        void shouldReturnTrueForValidEmail() {
            assertThat(validator.isSafeEmail("user@example.com")).isTrue();
        }

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(validator.isSafeEmail(null)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid email format")
        void shouldReturnFalseForInvalidEmail() {
            assertThat(validator.isSafeEmail("not-an-email")).isFalse();
        }
    }

    @Nested
    @DisplayName("isSafePhoneNumber")
    class IsSafePhoneNumber {

        @Test
        @DisplayName("should return true for valid phone number")
        void shouldReturnTrueForValidPhone() {
            assertThat(validator.isSafePhoneNumber("+821012345678")).isTrue();
        }

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(validator.isSafePhoneNumber(null)).isTrue();
        }

        @Test
        @DisplayName("should return false for invalid phone")
        void shouldReturnFalseForInvalidPhone() {
            assertThat(validator.isSafePhoneNumber("abc")).isFalse();
        }
    }

    @Nested
    @DisplayName("sanitizeInput")
    class SanitizeInput {

        @Test
        @DisplayName("should remove SQL comment indicators")
        void shouldRemoveSqlComments() {
            String result = validator.sanitizeInput("test -- comment /* block */");

            assertThat(result).doesNotContain("--");
            assertThat(result).doesNotContain("/*");
            assertThat(result).doesNotContain("*/");
        }

        @Test
        @DisplayName("should remove semicolons and quotes")
        void shouldRemoveSemicolonsAndQuotes() {
            String result = validator.sanitizeInput("test';\"\\");

            assertThat(result).doesNotContain(";");
            assertThat(result).doesNotContain("'");
            assertThat(result).doesNotContain("\"");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(validator.sanitizeInput(null)).isNull();
        }
    }

    @Nested
    @DisplayName("isAlphanumeric")
    class IsAlphanumeric {

        @Test
        @DisplayName("should return true for alphanumeric without spaces")
        void shouldReturnTrueWithoutSpaces() {
            assertThat(validator.isAlphanumeric("abc123", false)).isTrue();
        }

        @Test
        @DisplayName("should return true for alphanumeric with spaces when allowed")
        void shouldReturnTrueWithSpacesAllowed() {
            assertThat(validator.isAlphanumeric("abc 123", true)).isTrue();
        }

        @Test
        @DisplayName("should return false for spaces when not allowed")
        void shouldReturnFalseForSpacesNotAllowed() {
            assertThat(validator.isAlphanumeric("abc 123", false)).isFalse();
        }

        @Test
        @DisplayName("should return true for null")
        void shouldReturnTrueForNull() {
            assertThat(validator.isAlphanumeric(null, false)).isTrue();
        }
    }

    @Nested
    @DisplayName("isValidUUID")
    class IsValidUUID {

        @Test
        @DisplayName("should return true for valid UUID")
        void shouldReturnTrueForValidUuid() {
            assertThat(validator.isValidUUID("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(validator.isValidUUID(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for invalid UUID")
        void shouldReturnFalseForInvalidUuid() {
            assertThat(validator.isValidUUID("not-a-uuid")).isFalse();
        }
    }

    @Nested
    @DisplayName("isSafeHash")
    class IsSafeHash {

        @Test
        @DisplayName("should return true for valid base64 hash")
        void shouldReturnTrueForValidHash() {
            assertThat(validator.isSafeHash("n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=")).isTrue();
        }

        @Test
        @DisplayName("should return true for null or empty")
        void shouldReturnTrueForNullOrEmpty() {
            assertThat(validator.isSafeHash(null)).isTrue();
            assertThat(validator.isSafeHash("")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-base64 string")
        void shouldReturnFalseForNonBase64() {
            assertThat(validator.isSafeHash("not a valid hash!@#")).isFalse();
        }
    }

    @Nested
    @DisplayName("escapeLikePattern")
    class EscapeLikePattern {

        @Test
        @DisplayName("should escape LIKE wildcards")
        void shouldEscapeWildcards() {
            String result = validator.escapeLikePattern("100% of_users [test]");

            assertThat(result).contains("\\%");
            assertThat(result).contains("\\_");
            assertThat(result).contains("\\[");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(validator.escapeLikePattern(null)).isNull();
        }
    }

    @Nested
    @DisplayName("isValidSortField")
    class IsValidSortField {

        @Test
        @DisplayName("should return true when field is in allowed list")
        void shouldReturnTrueWhenInAllowedList() {
            assertThat(validator.isValidSortField("name", List.of("name", "email", "id"))).isTrue();
        }

        @Test
        @DisplayName("should return false when field is not in allowed list")
        void shouldReturnFalseWhenNotInAllowedList() {
            assertThat(validator.isValidSortField("password", List.of("name", "email", "id"))).isFalse();
        }

        @Test
        @DisplayName("should return true for null or empty field name")
        void shouldReturnTrueForNullField() {
            assertThat(validator.isValidSortField(null, List.of("name"))).isTrue();
            assertThat(validator.isValidSortField("", List.of("name"))).isTrue();
        }

        @Test
        @DisplayName("should validate format when no allowed list provided")
        void shouldValidateFormatWithoutList() {
            assertThat(validator.isValidSortField("validField", null)).isTrue();
            assertThat(validator.isValidSortField("1invalid", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidPagination")
    class IsValidPagination {

        @Test
        @DisplayName("should return true for valid pagination")
        void shouldReturnTrueForValid() {
            assertThat(validator.isValidPagination(0, 20)).isTrue();
            assertThat(validator.isValidPagination(5, 100)).isTrue();
        }

        @Test
        @DisplayName("should return false for negative page")
        void shouldReturnFalseForNegativePage() {
            assertThat(validator.isValidPagination(-1, 20)).isFalse();
        }

        @Test
        @DisplayName("should return false for zero or negative size")
        void shouldReturnFalseForInvalidSize() {
            assertThat(validator.isValidPagination(0, 0)).isFalse();
            assertThat(validator.isValidPagination(0, -1)).isFalse();
        }

        @Test
        @DisplayName("should return false for size exceeding 1000")
        void shouldReturnFalseForExcessiveSize() {
            assertThat(validator.isValidPagination(0, 1001)).isFalse();
        }

        @Test
        @DisplayName("should return true for null parameters")
        void shouldReturnTrueForNull() {
            assertThat(validator.isValidPagination(null, null)).isTrue();
        }
    }
}
