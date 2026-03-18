package dev.simplecore.simplix.core.validator;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidateWithValidator")
class ValidateWithValidatorTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ConstraintValidatorContext context;

    @InjectMocks
    private ValidateWithValidator validator;

    // Test service with validation method
    public static class TestValidationService {
        public boolean validateName(String name) {
            return name != null && name.length() >= 3;
        }

        public boolean validateAge(Integer age) {
            return age != null && age > 0 && age < 150;
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("should return true for null value")
        void shouldReturnTrueForNull() {
            ValidateWith annotation = mock(ValidateWith.class);
            when(annotation.service()).thenReturn("testService.validateName");
            validator.initialize(annotation);

            assertThat(validator.isValid(null, context)).isTrue();
        }

        @Test
        @DisplayName("should return true when service method returns true")
        void shouldReturnTrueWhenServiceReturnsTrue() {
            TestValidationService service = new TestValidationService();

            ValidateWith annotation = mock(ValidateWith.class);
            when(annotation.service()).thenReturn("testService.validateName");
            validator.initialize(annotation);

            when(applicationContext.getBean("testService")).thenReturn(service);

            assertThat(validator.isValid("John", context)).isTrue();
        }

        @Test
        @DisplayName("should return false when service method returns false")
        void shouldReturnFalseWhenServiceReturnsFalse() {
            TestValidationService service = new TestValidationService();

            ValidateWith annotation = mock(ValidateWith.class);
            when(annotation.service()).thenReturn("testService.validateName");
            validator.initialize(annotation);

            when(applicationContext.getBean("testService")).thenReturn(service);

            assertThat(validator.isValid("ab", context)).isFalse();
        }

        @Test
        @DisplayName("should return false when service bean not found")
        void shouldReturnFalseWhenBeanNotFound() {
            ValidateWith annotation = mock(ValidateWith.class);
            when(annotation.service()).thenReturn("nonExistentService.validate");
            validator.initialize(annotation);

            when(applicationContext.getBean("nonExistentService"))
                    .thenThrow(new RuntimeException("Bean not found"));

            assertThat(validator.isValid("test", context)).isFalse();
        }

        @Test
        @DisplayName("should return false when method not found")
        void shouldReturnFalseWhenMethodNotFound() {
            TestValidationService service = new TestValidationService();

            ValidateWith annotation = mock(ValidateWith.class);
            when(annotation.service()).thenReturn("testService.nonExistentMethod");
            validator.initialize(annotation);

            when(applicationContext.getBean("testService")).thenReturn(service);

            assertThat(validator.isValid("test", context)).isFalse();
        }
    }
}
