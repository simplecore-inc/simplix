package dev.simplecore.simplix.email.config;

import dev.simplecore.simplix.email.model.MailProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailProperties")
class EmailPropertiesTest {

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("Should be enabled by default")
        void shouldBeEnabledByDefault() {
            EmailProperties properties = new EmailProperties();

            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should use CONSOLE provider by default")
        void shouldUseConsoleProviderByDefault() {
            EmailProperties properties = new EmailProperties();

            assertThat(properties.getProvider()).isEqualTo(MailProviderType.CONSOLE);
        }

        @Test
        @DisplayName("Should have default sub-configurations")
        void shouldHaveDefaultSubConfigurations() {
            EmailProperties properties = new EmailProperties();

            assertThat(properties.getFrom()).isNotNull();
            assertThat(properties.getSmtp()).isNotNull();
            assertThat(properties.getAwsSes()).isNotNull();
            assertThat(properties.getSendgrid()).isNotNull();
            assertThat(properties.getResend()).isNotNull();
            assertThat(properties.getTemplate()).isNotNull();
            assertThat(properties.getAsync()).isNotNull();
        }
    }

    @Nested
    @DisplayName("FromConfig")
    class FromConfigTest {

        @Test
        @DisplayName("Should have null defaults for address and name")
        void shouldHaveNullDefaults() {
            EmailProperties.FromConfig config = new EmailProperties.FromConfig();

            assertThat(config.getAddress()).isNull();
            assertThat(config.getName()).isNull();
        }

        @Test
        @DisplayName("Should set and get address and name")
        void shouldSetAndGetFields() {
            EmailProperties.FromConfig config = new EmailProperties.FromConfig();
            config.setAddress("noreply@example.com");
            config.setName("My App");

            assertThat(config.getAddress()).isEqualTo("noreply@example.com");
            assertThat(config.getName()).isEqualTo("My App");
        }
    }

    @Nested
    @DisplayName("SmtpConfig")
    class SmtpConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.SmtpConfig config = new EmailProperties.SmtpConfig();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getHost()).isNull();
            assertThat(config.getPort()).isEqualTo(587);
            assertThat(config.getUsername()).isNull();
            assertThat(config.getPassword()).isNull();
            assertThat(config.isStarttls()).isTrue();
            assertThat(config.isSsl()).isFalse();
            assertThat(config.getConnectionTimeout()).isEqualTo(10000);
            assertThat(config.getTimeout()).isEqualTo(10000);
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.SmtpConfig config = new EmailProperties.SmtpConfig();
            config.setEnabled(true);
            config.setHost("smtp.example.com");
            config.setPort(465);
            config.setUsername("user");
            config.setPassword("pass");
            config.setStarttls(false);
            config.setSsl(true);
            config.setConnectionTimeout(5000);
            config.setTimeout(5000);

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getHost()).isEqualTo("smtp.example.com");
            assertThat(config.getPort()).isEqualTo(465);
            assertThat(config.getUsername()).isEqualTo("user");
            assertThat(config.getPassword()).isEqualTo("pass");
            assertThat(config.isStarttls()).isFalse();
            assertThat(config.isSsl()).isTrue();
            assertThat(config.getConnectionTimeout()).isEqualTo(5000);
            assertThat(config.getTimeout()).isEqualTo(5000);
        }
    }

    @Nested
    @DisplayName("AwsSesConfig")
    class AwsSesConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.AwsSesConfig config = new EmailProperties.AwsSesConfig();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getRegion()).isNull();
            assertThat(config.getConfigurationSet()).isNull();
            assertThat(config.getAccessKey()).isNull();
            assertThat(config.getSecretKey()).isNull();
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.AwsSesConfig config = new EmailProperties.AwsSesConfig();
            config.setEnabled(true);
            config.setRegion("us-east-1");
            config.setConfigurationSet("my-set");
            config.setAccessKey("AKID");
            config.setSecretKey("SECRET");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getRegion()).isEqualTo("us-east-1");
            assertThat(config.getConfigurationSet()).isEqualTo("my-set");
            assertThat(config.getAccessKey()).isEqualTo("AKID");
            assertThat(config.getSecretKey()).isEqualTo("SECRET");
        }
    }

    @Nested
    @DisplayName("SendGridConfig")
    class SendGridConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.SendGridConfig config = new EmailProperties.SendGridConfig();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getApiKey()).isNull();
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.SendGridConfig config = new EmailProperties.SendGridConfig();
            config.setEnabled(true);
            config.setApiKey("SG.xxxx");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getApiKey()).isEqualTo("SG.xxxx");
        }
    }

    @Nested
    @DisplayName("ResendConfig")
    class ResendConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.ResendConfig config = new EmailProperties.ResendConfig();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getApiKey()).isNull();
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.ResendConfig config = new EmailProperties.ResendConfig();
            config.setEnabled(true);
            config.setApiKey("re_xxxx");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getApiKey()).isEqualTo("re_xxxx");
        }
    }

    @Nested
    @DisplayName("TemplateConfig")
    class TemplateConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.TemplateConfig config = new EmailProperties.TemplateConfig();

            assertThat(config.getBasePath()).isEqualTo("templates/email");
            assertThat(config.isDatabaseEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.TemplateConfig config = new EmailProperties.TemplateConfig();
            config.setBasePath("custom/templates");
            config.setDatabaseEnabled(true);

            assertThat(config.getBasePath()).isEqualTo("custom/templates");
            assertThat(config.isDatabaseEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("AsyncConfig")
    class AsyncConfigTest {

        @Test
        @DisplayName("Should have correct defaults")
        void shouldHaveCorrectDefaults() {
            EmailProperties.AsyncConfig config = new EmailProperties.AsyncConfig();

            assertThat(config.getCorePoolSize()).isEqualTo(2);
            assertThat(config.getMaxPoolSize()).isEqualTo(10);
            assertThat(config.getQueueCapacity()).isEqualTo(100);
            assertThat(config.getThreadNamePrefix()).isEqualTo("email-async-");
        }

        @Test
        @DisplayName("Should set and get all fields")
        void shouldSetAndGetAllFields() {
            EmailProperties.AsyncConfig config = new EmailProperties.AsyncConfig();
            config.setCorePoolSize(5);
            config.setMaxPoolSize(20);
            config.setQueueCapacity(200);
            config.setThreadNamePrefix("custom-email-");

            assertThat(config.getCorePoolSize()).isEqualTo(5);
            assertThat(config.getMaxPoolSize()).isEqualTo(20);
            assertThat(config.getQueueCapacity()).isEqualTo(200);
            assertThat(config.getThreadNamePrefix()).isEqualTo("custom-email-");
        }
    }

    @Nested
    @DisplayName("setter/getter integration")
    class SetterGetterIntegration {

        @Test
        @DisplayName("Should set and get all top-level properties")
        void shouldSetAndGetTopLevel() {
            EmailProperties properties = new EmailProperties();
            properties.setEnabled(false);
            properties.setProvider(MailProviderType.SMTP);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getProvider()).isEqualTo(MailProviderType.SMTP);
        }
    }
}
