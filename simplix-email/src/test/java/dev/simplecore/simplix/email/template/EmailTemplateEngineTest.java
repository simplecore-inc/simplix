package dev.simplecore.simplix.email.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EmailTemplateEngine")
class EmailTemplateEngineTest {

    private EmailTemplateEngine engine;

    @BeforeEach
    void setUp() {
        SpringTemplateEngine textEngine = new SpringTemplateEngine();
        StringTemplateResolver textResolver = new StringTemplateResolver();
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setCacheable(false);
        textEngine.addTemplateResolver(textResolver);

        SpringTemplateEngine htmlEngine = new SpringTemplateEngine();
        StringTemplateResolver htmlResolver = new StringTemplateResolver();
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCacheable(false);
        htmlEngine.addTemplateResolver(htmlResolver);

        engine = new EmailTemplateEngine(textEngine, htmlEngine);
    }

    @Nested
    @DisplayName("processText")
    class ProcessText {

        @Test
        @DisplayName("Should process TEXT template with variables")
        void shouldProcessTextTemplate() {
            String template = "Hello, [(${name})]! Welcome to [(${appName})].";
            Map<String, Object> variables = Map.of("name", "John", "appName", "MyApp");

            String result = engine.processText(template, variables, Locale.ENGLISH);

            assertThat(result).isEqualTo("Hello, John! Welcome to MyApp.");
        }

        @Test
        @DisplayName("Should return null for null template")
        void shouldReturnNullForNullTemplate() {
            String result = engine.processText(null, Map.of(), Locale.ENGLISH);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return blank template as-is")
        void shouldReturnBlankTemplateAsIs() {
            String result = engine.processText("  ", Map.of(), Locale.ENGLISH);

            assertThat(result).isEqualTo("  ");
        }

        @Test
        @DisplayName("Should handle null variables")
        void shouldHandleNullVariables() {
            String template = "Static text without variables.";

            String result = engine.processText(template, null, Locale.ENGLISH);

            assertThat(result).isEqualTo("Static text without variables.");
        }

        @Test
        @DisplayName("Should throw EmailTemplateException for invalid template")
        void shouldThrowExceptionForInvalidTemplate() {
            // Use a template expression that references an operation on a null object
            // to trigger a processing error
            String template = "[(${#strings.toUpperCase(undefinedVar.nested.value)})]";

            assertThatThrownBy(() -> engine.processText(template, Map.of(), Locale.ENGLISH))
                    .isInstanceOf(EmailTemplateEngine.EmailTemplateException.class)
                    .hasMessageContaining("Template processing failed");
        }
    }

    @Nested
    @DisplayName("processHtml")
    class ProcessHtml {

        @Test
        @DisplayName("Should process HTML template with variables")
        void shouldProcessHtmlTemplate() {
            String template = "<html><body><h1 th:text=\"${title}\">Default</h1><p th:text=\"${message}\">msg</p></body></html>";
            Map<String, Object> variables = Map.of("title", "Welcome", "message", "Hello World");

            String result = engine.processHtml(template, variables, Locale.ENGLISH);

            assertThat(result).contains("Welcome");
            assertThat(result).contains("Hello World");
        }

        @Test
        @DisplayName("Should return null for null template")
        void shouldReturnNullForNullTemplate() {
            String result = engine.processHtml(null, Map.of(), Locale.ENGLISH);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return blank template as-is")
        void shouldReturnBlankTemplateAsIs() {
            String result = engine.processHtml("  ", Map.of(), Locale.ENGLISH);

            assertThat(result).isEqualTo("  ");
        }

        @Test
        @DisplayName("Should handle null variables")
        void shouldHandleNullVariables() {
            String template = "<p>Static HTML</p>";

            String result = engine.processHtml(template, null, Locale.ENGLISH);

            assertThat(result).contains("Static HTML");
        }
    }

    @Nested
    @DisplayName("process with ResolvedTemplate")
    class ProcessResolvedTemplate {

        @Test
        @DisplayName("Should process all parts of resolved template")
        void shouldProcessAllParts() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "welcome",
                    "Welcome, [(${name})]!",
                    "<html><body><span th:text=\"${name}\">User</span></body></html>",
                    "Hello, [(${name})]!",
                    Locale.ENGLISH
            );
            Map<String, Object> variables = Map.of("name", "Alice");

            EmailTemplateEngine.ProcessedTemplate result = engine.process(resolved, variables);

            assertThat(result.subject()).isEqualTo("Welcome, Alice!");
            assertThat(result.htmlBody()).contains("Alice");
            assertThat(result.textBody()).isEqualTo("Hello, Alice!");
        }

        @Test
        @DisplayName("Should handle template with HTML body only")
        void shouldHandleHtmlBodyOnly() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test",
                    "Subject",
                    "<p>HTML content</p>",
                    null,
                    Locale.ENGLISH
            );

            EmailTemplateEngine.ProcessedTemplate result = engine.process(resolved, Map.of());

            assertThat(result.subject()).isEqualTo("Subject");
            assertThat(result.htmlBody()).contains("HTML content");
            assertThat(result.textBody()).isNull();
        }

        @Test
        @DisplayName("Should handle template with text body only")
        void shouldHandleTextBodyOnly() {
            EmailTemplateResolver.ResolvedTemplate resolved = new EmailTemplateResolver.ResolvedTemplate(
                    "test",
                    "Subject",
                    null,
                    "Plain text content",
                    Locale.ENGLISH
            );

            EmailTemplateEngine.ProcessedTemplate result = engine.process(resolved, Map.of());

            assertThat(result.subject()).isEqualTo("Subject");
            assertThat(result.htmlBody()).isNull();
            assertThat(result.textBody()).isEqualTo("Plain text content");
        }
    }

    @Nested
    @DisplayName("ProcessedTemplate record")
    class ProcessedTemplateRecord {

        @Test
        @DisplayName("hasHtmlBody should return true when HTML body is present")
        void hasHtmlBodyShouldReturnTrue() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", "<p>html</p>", null);

            assertThat(pt.hasHtmlBody()).isTrue();
        }

        @Test
        @DisplayName("hasHtmlBody should return false when HTML body is null")
        void hasHtmlBodyShouldReturnFalseWhenNull() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", null, "text");

            assertThat(pt.hasHtmlBody()).isFalse();
        }

        @Test
        @DisplayName("hasHtmlBody should return false when HTML body is blank")
        void hasHtmlBodyShouldReturnFalseWhenBlank() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", "  ", "text");

            assertThat(pt.hasHtmlBody()).isFalse();
        }

        @Test
        @DisplayName("hasTextBody should return true when text body is present")
        void hasTextBodyShouldReturnTrue() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", null, "text");

            assertThat(pt.hasTextBody()).isTrue();
        }

        @Test
        @DisplayName("hasTextBody should return false when text body is null")
        void hasTextBodyShouldReturnFalseWhenNull() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", "<p>html</p>", null);

            assertThat(pt.hasTextBody()).isFalse();
        }

        @Test
        @DisplayName("hasTextBody should return false when text body is blank")
        void hasTextBodyShouldReturnFalseWhenBlank() {
            var pt = new EmailTemplateEngine.ProcessedTemplate("subj", "<p>html</p>", "  ");

            assertThat(pt.hasTextBody()).isFalse();
        }
    }

    @Nested
    @DisplayName("EmailTemplateException")
    class EmailTemplateExceptionTest {

        @Test
        @DisplayName("Should preserve message and cause")
        void shouldPreserveMessageAndCause() {
            RuntimeException cause = new RuntimeException("root cause");
            EmailTemplateEngine.EmailTemplateException ex =
                    new EmailTemplateEngine.EmailTemplateException("Processing failed", cause);

            assertThat(ex.getMessage()).isEqualTo("Processing failed");
            assertThat(ex.getCause()).isEqualTo(cause);
        }
    }
}
