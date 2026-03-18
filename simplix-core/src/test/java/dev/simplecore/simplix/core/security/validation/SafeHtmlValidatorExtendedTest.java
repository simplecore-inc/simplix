package dev.simplecore.simplix.core.security.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("SafeHtmlValidator - Extended Coverage")
class SafeHtmlValidatorExtendedTest {

    private final SafeHtmlValidator validator = new SafeHtmlValidator();

    @Mock
    private ConstraintValidatorContext ctx;

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should accept null")
        void shouldAcceptNull() {
            assertThat(validator.isValid(null, ctx)).isTrue();
        }

        @Test
        @DisplayName("should accept empty string")
        void shouldAcceptEmpty() {
            assertThat(validator.isValid("", ctx)).isTrue();
        }

        @Test
        @DisplayName("should accept plain text")
        void shouldAcceptPlainText() {
            assertThat(validator.isValid("Hello World", ctx)).isTrue();
        }

        @Test
        @DisplayName("should reject script tags")
        void shouldRejectScript() {
            ConstraintValidatorContext.ConstraintViolationBuilder builder =
                    mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
            lenient().when(ctx.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
            lenient().doNothing().when(ctx).disableDefaultConstraintViolation();

            assertThat(validator.isValid("<script>alert(1)</script>", ctx)).isFalse();
        }
    }
}
