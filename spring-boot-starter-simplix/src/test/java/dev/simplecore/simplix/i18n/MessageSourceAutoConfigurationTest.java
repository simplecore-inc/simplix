package dev.simplecore.simplix.i18n;

import dev.simplecore.simplix.springboot.autoconfigure.SimpliXMessageSourceAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SimpliXMessageSourceAutoConfiguration} verifying that the
 * MessageSource is correctly assembled, discovers library message files,
 * and resolves messages across locales with proper fallback behavior.
 */
@DisplayName("SimpliXMessageSourceAutoConfiguration")
class MessageSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpliXMessageSourceAutoConfiguration.class));

    // =========================================================================
    // Bean registration
    // =========================================================================

    @Test
    @DisplayName("Should register MessageSource bean when enabled (default)")
    void registersMessageSourceByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageSource.class);
        });
    }

    @Test
    @DisplayName("Should not register MessageSource bean when disabled")
    void doesNotRegisterWhenDisabled() {
        contextRunner
                .withPropertyValues("simplix.message-source.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SimpliXMessageSourceAutoConfiguration.class);
                });
    }

    // =========================================================================
    // Library message discovery
    // =========================================================================

    @Test
    @DisplayName("Should resolve simplix_core messages in EN locale")
    void resolvesEnCoreMessages() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            String message = messageSource.getMessage(
                    "error.gen.not.found", null, Locale.ENGLISH);

            assertThat(message).isEqualTo("Resource not found");
        });
    }

    @Test
    @DisplayName("Should resolve simplix_core messages in KO locale")
    void resolvesKoCoreMessages() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            String message = messageSource.getMessage(
                    "error.gen.not.found", null, Locale.KOREAN);

            assertThat(message)
                    .as("KO message for error.gen.not.found")
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("Resource not found"); // Should be Korean, not English
        });
    }

    @Test
    @DisplayName("Should resolve simplix_validation messages in EN locale")
    void resolvesEnValidationMessages() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            String message = messageSource.getMessage(
                    "validation.notnull", null, Locale.ENGLISH);

            assertThat(message).isEqualTo("This field is required");
        });
    }

    @Test
    @DisplayName("Should resolve simplix_validation messages in KO locale")
    void resolvesKoValidationMessages() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            String message = messageSource.getMessage(
                    "validation.notnull", null, Locale.KOREAN);

            assertThat(message)
                    .as("KO message for validation.notnull")
                    .isNotNull()
                    .isNotBlank()
                    .isNotEqualTo("This field is required");
        });
    }

    // =========================================================================
    // Locale fallback
    // =========================================================================

    @Test
    @DisplayName("Should fall back to EN default for unsupported locale")
    void fallsBackToDefaultForUnsupportedLocale() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            // French is not supported; should fall back to default (EN)
            String message = messageSource.getMessage(
                    "error.gen.not.found", null, Locale.FRENCH);

            assertThat(message)
                    .as("Unsupported locale should fall back to EN default message")
                    .isEqualTo("Resource not found");
        });
    }

    @Test
    @DisplayName("Should throw NoSuchMessageException for completely unknown key without default")
    void throwsForUnknownKey() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            assertThatThrownBy(() ->
                    messageSource.getMessage(
                            "this.key.does.not.exist.anywhere", null, Locale.ENGLISH))
                    .isInstanceOf(NoSuchMessageException.class);
        });
    }

    @Test
    @DisplayName("Should return default message when provided and key is missing")
    void returnsDefaultMessageForMissingKey() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            String message = messageSource.getMessage(
                    "this.key.does.not.exist", null, "Fallback text", Locale.ENGLISH);

            assertThat(message).isEqualTo("Fallback text");
        });
    }

    // =========================================================================
    // Message parameter substitution
    // =========================================================================

    @Test
    @DisplayName("Should correctly substitute parameters in messages")
    void substitutesParametersCorrectly() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            // error.notFound.detail uses {0} placeholder
            String message = messageSource.getMessage(
                    "error.notFound.detail", new Object[]{"/api/users/123"}, Locale.ENGLISH);

            assertThat(message)
                    .contains("/api/users/123");
        });
    }

    // =========================================================================
    // Multiple library bundles
    // =========================================================================

    @Test
    @DisplayName("Should discover and resolve messages from multiple simplix_ bundles")
    void discoversMultipleLibraryBundles() {
        contextRunner.run(context -> {
            MessageSource messageSource = context.getBean(MessageSource.class);

            // From simplix_core
            String coreMsg = messageSource.getMessage(
                    "error.gen.bad.request", null, Locale.ENGLISH);
            assertThat(coreMsg).isNotBlank();

            // From simplix_validation
            String validationMsg = messageSource.getMessage(
                    "validation.email", null, Locale.ENGLISH);
            assertThat(validationMsg).isNotBlank();
        });
    }
}
