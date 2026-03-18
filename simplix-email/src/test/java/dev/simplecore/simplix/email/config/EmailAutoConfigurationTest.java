package dev.simplecore.simplix.email.config;

import dev.simplecore.simplix.email.model.MailProviderType;
import dev.simplecore.simplix.email.provider.ConsoleEmailProvider;
import dev.simplecore.simplix.email.service.DefaultEmailService;
import dev.simplecore.simplix.email.service.EmailService;
import dev.simplecore.simplix.email.template.ClasspathEmailTemplateResolver;
import dev.simplecore.simplix.email.template.EmailTemplateEngine;
import dev.simplecore.simplix.email.template.EmailTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailAutoConfiguration")
class EmailAutoConfigurationTest {

    private final EmailAutoConfiguration config = new EmailAutoConfiguration();

    @Nested
    @DisplayName("textTemplateEngine")
    class TextTemplateEngineBean {

        @Test
        @DisplayName("Should create a text template engine")
        void shouldCreateTextTemplateEngine() {
            TemplateEngine engine = config.textTemplateEngine();

            assertThat(engine).isNotNull();
        }
    }

    @Nested
    @DisplayName("htmlTemplateEngine")
    class HtmlTemplateEngineBean {

        @Test
        @DisplayName("Should create an HTML template engine")
        void shouldCreateHtmlTemplateEngine() {
            TemplateEngine engine = config.htmlTemplateEngine();

            assertThat(engine).isNotNull();
        }
    }

    @Nested
    @DisplayName("emailTemplateEngineWrapper")
    class EmailTemplateEngineWrapperBean {

        @Test
        @DisplayName("Should create EmailTemplateEngine wrapping both template engines")
        void shouldCreateWrapper() {
            TemplateEngine textEngine = config.textTemplateEngine();
            TemplateEngine htmlEngine = config.htmlTemplateEngine();

            EmailTemplateEngine wrapper = config.emailTemplateEngineWrapper(textEngine, htmlEngine);

            assertThat(wrapper).isNotNull();
        }
    }

    @Nested
    @DisplayName("classpathEmailTemplateResolver")
    class ClasspathResolverBean {

        @Test
        @DisplayName("Should create classpath resolver with configured base path")
        void shouldCreateClasspathResolver() {
            EmailProperties properties = new EmailProperties();
            properties.getTemplate().setBasePath("custom/path");

            ClasspathEmailTemplateResolver resolver = config.classpathEmailTemplateResolver(properties);

            assertThat(resolver).isNotNull();
        }
    }

    @Nested
    @DisplayName("emailTemplateService")
    class EmailTemplateServiceBean {

        @Test
        @DisplayName("Should create email template service")
        void shouldCreateTemplateService() {
            EmailProperties properties = new EmailProperties();
            ClasspathEmailTemplateResolver resolver = config.classpathEmailTemplateResolver(properties);
            TemplateEngine textEngine = config.textTemplateEngine();
            TemplateEngine htmlEngine = config.htmlTemplateEngine();
            EmailTemplateEngine engineWrapper = config.emailTemplateEngineWrapper(textEngine, htmlEngine);

            EmailTemplateService service = config.emailTemplateService(List.of(resolver), engineWrapper);

            assertThat(service).isNotNull();
        }
    }

    @Nested
    @DisplayName("emailAsyncExecutor")
    class AsyncExecutorBean {

        @Test
        @DisplayName("Should create async executor with configured properties")
        void shouldCreateAsyncExecutor() {
            EmailProperties properties = new EmailProperties();
            properties.getAsync().setCorePoolSize(3);
            properties.getAsync().setMaxPoolSize(15);

            Executor executor = config.emailAsyncExecutor(properties);

            assertThat(executor).isNotNull();
        }
    }

    @Nested
    @DisplayName("consoleEmailProvider")
    class ConsoleEmailProviderBean {

        @Test
        @DisplayName("Should create enabled console provider when provider type is CONSOLE")
        void shouldCreateEnabledConsoleProvider() {
            EmailProperties properties = new EmailProperties();
            properties.setProvider(MailProviderType.CONSOLE);

            ConsoleEmailProvider provider = config.consoleEmailProvider(properties);

            assertThat(provider).isNotNull();
            assertThat(provider.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should create disabled console provider when provider type is not CONSOLE")
        void shouldCreateDisabledConsoleProvider() {
            EmailProperties properties = new EmailProperties();
            properties.setProvider(MailProviderType.SMTP);

            ConsoleEmailProvider provider = config.consoleEmailProvider(properties);

            assertThat(provider).isNotNull();
            assertThat(provider.isAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("emailService")
    class EmailServiceBean {

        @Test
        @DisplayName("Should create email service with default from address")
        void shouldCreateEmailServiceWithDefaultFrom() {
            EmailProperties properties = new EmailProperties();
            properties.getFrom().setAddress("noreply@example.com");
            properties.getFrom().setName("My App");

            ConsoleEmailProvider consoleProvider = config.consoleEmailProvider(properties);
            ClasspathEmailTemplateResolver resolver = config.classpathEmailTemplateResolver(properties);
            TemplateEngine textEngine = config.textTemplateEngine();
            TemplateEngine htmlEngine = config.htmlTemplateEngine();
            EmailTemplateEngine engineWrapper = config.emailTemplateEngineWrapper(textEngine, htmlEngine);
            EmailTemplateService templateService = config.emailTemplateService(List.of(resolver), engineWrapper);
            Executor executor = config.emailAsyncExecutor(properties);

            EmailService service = config.emailService(
                    List.of(consoleProvider), templateService, executor, properties
            );

            assertThat(service).isNotNull().isInstanceOf(DefaultEmailService.class);
        }

        @Test
        @DisplayName("Should create email service without default from when not configured")
        void shouldCreateEmailServiceWithoutDefaultFrom() {
            EmailProperties properties = new EmailProperties();

            ConsoleEmailProvider consoleProvider = config.consoleEmailProvider(properties);
            ClasspathEmailTemplateResolver resolver = config.classpathEmailTemplateResolver(properties);
            TemplateEngine textEngine = config.textTemplateEngine();
            TemplateEngine htmlEngine = config.htmlTemplateEngine();
            EmailTemplateEngine engineWrapper = config.emailTemplateEngineWrapper(textEngine, htmlEngine);
            EmailTemplateService templateService = config.emailTemplateService(List.of(resolver), engineWrapper);
            Executor executor = config.emailAsyncExecutor(properties);

            EmailService service = config.emailService(
                    List.of(consoleProvider), templateService, executor, properties
            );

            assertThat(service).isNotNull();
        }
    }
}
