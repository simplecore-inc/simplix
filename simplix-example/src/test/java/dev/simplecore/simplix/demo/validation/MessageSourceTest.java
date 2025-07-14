package dev.simplecore.simplix.demo.validation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class MessageSourceTest {

    @Autowired
    private MessageSource messageSource;

    @Test
    public void testMessageSourceKorean() {
        String message = messageSource.getMessage("validation.roles.required", null, Locale.KOREAN);
        assertThat(message).isEqualTo("최소 하나 이상의 권한이 필요합니다");
    }

    @Test
    public void testMessageSourceEnglish() {
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