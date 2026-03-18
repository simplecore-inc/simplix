package dev.simplecore.simplix.core.security.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SafeHtmlValidator")
class SafeHtmlValidatorTest {

    private SafeHtmlValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private SafeHtml annotation;

    @BeforeEach
    void setUp() {
        validator = new SafeHtmlValidator();

        lenient().when(annotation.allowBasicFormatting()).thenReturn(false);
        lenient().when(annotation.allowLinks()).thenReturn(false);
        lenient().when(annotation.maxLength()).thenReturn(-1);
        lenient().when(annotation.allowedTags()).thenReturn(new String[]{});

        validator.initialize(annotation);

        lenient().doNothing().when(context).disableDefaultConstraintViolation();
        lenient().when(context.buildConstraintViolationWithTemplate(anyString()))
            .thenReturn(violationBuilder);
        lenient().when(violationBuilder.addConstraintViolation()).thenReturn(context);
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should return true for null value")
        void shouldReturnTrueForNull() {
            assertThat(validator.isValid(null, context)).isTrue();
        }

        @Test
        @DisplayName("should return true for empty value")
        void shouldReturnTrueForEmpty() {
            assertThat(validator.isValid("", context)).isTrue();
        }

        @Test
        @DisplayName("should return true for safe plain text")
        void shouldReturnTrueForPlainText() {
            assertThat(validator.isValid("Hello World", context)).isTrue();
        }

        @Test
        @DisplayName("should return false for script tag content")
        void shouldReturnFalseForScriptTag() {
            assertThat(validator.isValid("<script>alert('xss')</script>", context)).isFalse();
        }

        @Test
        @DisplayName("should return false for HTML content in strict mode")
        void shouldReturnFalseForHtmlInStrict() {
            assertThat(validator.isValid("<b>Bold text</b>", context)).isFalse();
        }
    }

    @Nested
    @DisplayName("isValid with basic formatting allowed")
    class IsValidWithFormatting {

        @BeforeEach
        void setUpFormatting() {
            when(annotation.allowBasicFormatting()).thenReturn(true);
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("should return true for basic formatting tags")
        void shouldAllowBasicFormatting() {
            assertThat(validator.isValid("<b>Bold</b>", context)).isTrue();
        }
    }

    @Nested
    @DisplayName("isValid with maxLength")
    class IsValidWithMaxLength {

        @BeforeEach
        void setUpMaxLength() {
            when(annotation.maxLength()).thenReturn(10);
            validator.initialize(annotation);
        }

        @Test
        @DisplayName("should return false when content exceeds maxLength")
        void shouldReturnFalseWhenExceedsMaxLength() {
            assertThat(validator.isValid("This is a very long text that exceeds the limit", context)).isFalse();
        }

        @Test
        @DisplayName("should return true when content within maxLength")
        void shouldReturnTrueWhenWithinMaxLength() {
            assertThat(validator.isValid("Short", context)).isTrue();
        }
    }
}
