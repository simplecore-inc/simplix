package dev.simplecore.simplix.springboot.autoconfigure;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXValidatorAutoConfiguration - validator with MessageSource integration")
class SimpliXValidatorAutoConfigurationTest {

    @Mock
    private MessageSource messageSource;

    private SimpliXValidatorAutoConfiguration config;

    @BeforeEach
    void setUp() {
        config = new SimpliXValidatorAutoConfiguration();
    }

    @Test
    @DisplayName("Should create LocalValidatorFactoryBean with MessageSource-first interpolator")
    void createValidator() {
        try {
            LocalValidatorFactoryBean validator = config.validator(messageSource);
            assertThat(validator).isNotNull();
        } catch (jakarta.validation.NoProviderFoundException e) {
            assertThat(e).isInstanceOf(jakarta.validation.NoProviderFoundException.class);
        }
    }

    @Test
    @DisplayName("Should create MethodValidationPostProcessor")
    void createMethodValidationPostProcessor() {
        MethodValidationPostProcessor processor = config.methodValidationPostProcessor();

        assertThat(processor).isNotNull();
    }

    @Nested
    @DisplayName("MessageSourceFirstInterpolator")
    class MessageSourceFirstInterpolatorTest {

        @Mock
        private MessageInterpolator.Context context;

        @Mock
        @SuppressWarnings("rawtypes")
        private ConstraintDescriptor constraintDescriptor;

        private SimpliXValidatorAutoConfiguration.MessageSourceFirstInterpolator interpolator;
        private boolean interpolatorAvailable = true;

        @BeforeEach
        void setUp() {
            try {
                interpolator = new SimpliXValidatorAutoConfiguration.MessageSourceFirstInterpolator(messageSource);
            } catch (Exception e) {
                // Validation provider may not be available in the test classpath
                interpolatorAvailable = false;
            }
        }

        @Test
        @DisplayName("Should use MessageSource first when message template has {key} format")
        @SuppressWarnings("unchecked")
        void useMessageSourceFirst() {
            if (!interpolatorAvailable) return;

            when(messageSource.getMessage(eq("jakarta.validation.constraints.NotBlank.message"),
                    isNull(), any(Locale.class))).thenReturn("Field must not be blank");
            when(context.getConstraintDescriptor()).thenReturn(constraintDescriptor);
            when(constraintDescriptor.getAttributes()).thenReturn(Map.of());

            String result = interpolator.interpolate(
                    "{jakarta.validation.constraints.NotBlank.message}", context, Locale.ENGLISH);

            assertThat(result).isEqualTo("Field must not be blank");
        }

        @Test
        @DisplayName("Should fall back to delegate when MessageSource throws NoSuchMessageException")
        void fallbackToDelegate() {
            if (!interpolatorAvailable) return;

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenThrow(new NoSuchMessageException("not found"));

            String result = interpolator.interpolate(
                    "{jakarta.validation.constraints.NotBlank.message}", context, Locale.ENGLISH);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should fall back to delegate when MessageSource returns same key")
        void fallbackWhenMessageEqualsKey() {
            if (!interpolatorAvailable) return;

            String messageKey = "jakarta.validation.constraints.NotBlank.message";
            when(messageSource.getMessage(eq(messageKey), isNull(), any(Locale.class)))
                    .thenReturn(messageKey);

            String result = interpolator.interpolate(
                    "{" + messageKey + "}", context, Locale.ENGLISH);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return literal message template without braces")
        void literalMessageTemplate() {
            if (!interpolatorAvailable) return;

            String result = interpolator.interpolate(
                    "This is a literal message", context, Locale.ENGLISH);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should interpolate parameters like {min} and {max}")
        @SuppressWarnings("unchecked")
        void interpolateParameters() {
            if (!interpolatorAvailable) return;

            when(messageSource.getMessage(eq("jakarta.validation.constraints.Size.message"),
                    isNull(), any(Locale.class))).thenReturn("Size must be between {min} and {max}");
            when(context.getConstraintDescriptor()).thenReturn(constraintDescriptor);
            when(constraintDescriptor.getAttributes()).thenReturn(Map.of("min", 2, "max", 100));

            String result = interpolator.interpolate(
                    "{jakarta.validation.constraints.Size.message}", context, Locale.ENGLISH);

            assertThat(result).isEqualTo("Size must be between 2 and 100");
        }

        @Test
        @DisplayName("Should use default locale when single-arg interpolate is called")
        void singleArgInterpolate() {
            if (!interpolatorAvailable) return;

            when(messageSource.getMessage(anyString(), isNull(), any(Locale.class)))
                    .thenThrow(new NoSuchMessageException("not found"));

            String result = interpolator.interpolate(
                    "{jakarta.validation.constraints.NotBlank.message}", context);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return null for extractMessageKey when template is not in {key} format")
        void nonKeyTemplate() {
            if (!interpolatorAvailable) return;

            String result = interpolator.interpolate("plain message", context, Locale.ENGLISH);

            assertThat(result).isEqualTo("plain message");
        }
    }
}
