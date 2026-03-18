package dev.simplecore.simplix.email.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClasspathEmailTemplateResolver")
class ClasspathEmailTemplateResolverTest {

    private ClasspathEmailTemplateResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClasspathEmailTemplateResolver("templates/email");
    }

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        @DisplayName("Should resolve template for English locale")
        void shouldResolveForEnglishLocale() {
            Optional<EmailTemplateResolver.ResolvedTemplate> result = resolver.resolve("welcome", Locale.ENGLISH);

            assertThat(result).isPresent();
            EmailTemplateResolver.ResolvedTemplate template = result.get();
            assertThat(template.code()).isEqualTo("welcome");
            assertThat(template.subject()).contains("Welcome");
            assertThat(template.htmlBody()).isNotNull();
            assertThat(template.textBody()).isNotNull();
            assertThat(template.locale()).isEqualTo(Locale.ENGLISH);
        }

        @Test
        @DisplayName("Should resolve template for Korean locale")
        void shouldResolveForKoreanLocale() {
            Optional<EmailTemplateResolver.ResolvedTemplate> result = resolver.resolve("welcome", Locale.KOREAN);

            assertThat(result).isPresent();
            EmailTemplateResolver.ResolvedTemplate template = result.get();
            assertThat(template.code()).isEqualTo("welcome");
            assertThat(template.subject()).isNotNull();
        }

        @Test
        @DisplayName("Should fall back to English when locale-specific template is not found")
        void shouldFallbackToEnglish() {
            // Japanese locale does not exist, should fall back to English
            Optional<EmailTemplateResolver.ResolvedTemplate> result = resolver.resolve("welcome", Locale.JAPANESE);

            assertThat(result).isPresent();
            EmailTemplateResolver.ResolvedTemplate template = result.get();
            assertThat(template.subject()).contains("Welcome");
        }

        @Test
        @DisplayName("Should return empty when template does not exist")
        void shouldReturnEmptyWhenNotFound() {
            Optional<EmailTemplateResolver.ResolvedTemplate> result = resolver.resolve("nonexistent", Locale.ENGLISH);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should trim the subject content")
        void shouldTrimSubject() {
            Optional<EmailTemplateResolver.ResolvedTemplate> result = resolver.resolve("welcome", Locale.ENGLISH);

            assertThat(result).isPresent();
            // Subject should not have leading/trailing whitespace
            assertThat(result.get().subject()).doesNotStartWith(" ").doesNotEndWith(" ");
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("Should return true when template exists in default locale")
        void shouldReturnTrueWhenExists() {
            assertThat(resolver.exists("welcome")).isTrue();
        }

        @Test
        @DisplayName("Should return false when template does not exist")
        void shouldReturnFalseWhenNotExists() {
            assertThat(resolver.exists("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("getPriority")
    class GetPriority {

        @Test
        @DisplayName("Should have priority 10")
        void shouldHavePriority10() {
            assertThat(resolver.getPriority()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("default constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("Should use default base path")
        void shouldUseDefaultBasePath() {
            ClasspathEmailTemplateResolver defaultResolver = new ClasspathEmailTemplateResolver();

            // Default path is "templates/email", same as test setup
            assertThat(defaultResolver.exists("welcome")).isTrue();
        }
    }

    @Nested
    @DisplayName("ResolvedTemplate record")
    class ResolvedTemplateRecord {

        @Test
        @DisplayName("hasHtmlBody should return true when HTML body is present")
        void hasHtmlBodyShouldReturnTrue() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", "<p>html</p>", null, Locale.ENGLISH
            );

            assertThat(resolved.hasHtmlBody()).isTrue();
        }

        @Test
        @DisplayName("hasHtmlBody should return false when HTML body is null")
        void hasHtmlBodyShouldReturnFalseWhenNull() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", null, "text", Locale.ENGLISH
            );

            assertThat(resolved.hasHtmlBody()).isFalse();
        }

        @Test
        @DisplayName("hasHtmlBody should return false when HTML body is blank")
        void hasHtmlBodyShouldReturnFalseWhenBlank() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", "  ", "text", Locale.ENGLISH
            );

            assertThat(resolved.hasHtmlBody()).isFalse();
        }

        @Test
        @DisplayName("hasTextBody should return true when text body is present")
        void hasTextBodyShouldReturnTrue() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", null, "text body", Locale.ENGLISH
            );

            assertThat(resolved.hasTextBody()).isTrue();
        }

        @Test
        @DisplayName("hasTextBody should return false when text body is null")
        void hasTextBodyShouldReturnFalseWhenNull() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", "<p>html</p>", null, Locale.ENGLISH
            );

            assertThat(resolved.hasTextBody()).isFalse();
        }

        @Test
        @DisplayName("hasTextBody should return false when text body is blank")
        void hasTextBodyShouldReturnFalseWhenBlank() {
            var resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test", "subject", "<p>html</p>", "  ", Locale.ENGLISH
            );

            assertThat(resolved.hasTextBody()).isFalse();
        }
    }
}
