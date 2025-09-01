package dev.simplecore.simplix.demo.validation;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageSourceTest {

    private final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

    public MessageSourceTest() {
        messageSource.setBasenames("messages/validation", "messages/errors", "messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false); // Do not fallback to system locale
        messageSource.setUseCodeAsDefaultMessage(false);
    }

    @Test
    public void testMessageSourceKorean() {
        String message = messageSource.getMessage("validation.roles.required", null, Locale.KOREAN);
        assertThat(message).isEqualTo("최소 하나 이상의 권한이 필요합니다");
    }

    @Test
    public void testMessageSourceEnglish() {
        // Use default file message since English message file doesn't exist
        // ResourceBundleMessageSource uses default file for English locale
        String message = messageSource.getMessage("validation.roles.required", null, Locale.ENGLISH);
        assertThat(message).isEqualTo("At least one role is required");
    }

    @Test
    public void testMessageSourceDefault() {
        String message = messageSource.getMessage("validation.roles.required", null, Locale.getDefault());
        assertThat(message).isNotNull();
        assertThat(message).doesNotContain("{validation.roles.required}");
    }
} 