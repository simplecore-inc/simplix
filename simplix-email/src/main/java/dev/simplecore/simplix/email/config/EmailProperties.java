package dev.simplecore.simplix.email.config;

import dev.simplecore.simplix.email.model.MailProviderType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for email module.
 * <p>
 * Example configuration:
 * <pre>{@code
 * simplix:
 *   email:
 *     enabled: true
 *     provider: SMTP
 *     from:
 *       address: noreply@example.com
 *       name: My Application
 *     smtp:
 *       host: smtp.example.com
 *       port: 587
 *     aws-ses:
 *       region: us-east-1
 *       configuration-set: my-config-set
 *     sendgrid:
 *       api-key: SG.xxxx
 *     resend:
 *       api-key: re_xxxx
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "simplix.email")
public class EmailProperties {

    /**
     * Enable or disable email sending.
     */
    private boolean enabled = true;

    /**
     * Primary email provider to use.
     */
    private MailProviderType provider = MailProviderType.CONSOLE;

    /**
     * Default sender configuration.
     */
    private FromConfig from = new FromConfig();

    /**
     * SMTP provider configuration.
     */
    private SmtpConfig smtp = new SmtpConfig();

    /**
     * AWS SES provider configuration.
     */
    private AwsSesConfig awsSes = new AwsSesConfig();

    /**
     * SendGrid provider configuration.
     */
    private SendGridConfig sendgrid = new SendGridConfig();

    /**
     * Resend provider configuration.
     */
    private ResendConfig resend = new ResendConfig();

    /**
     * Template configuration.
     */
    private TemplateConfig template = new TemplateConfig();

    /**
     * Async configuration.
     */
    private AsyncConfig async = new AsyncConfig();

    @Data
    public static class FromConfig {
        /**
         * Default sender email address.
         */
        private String address;

        /**
         * Default sender display name.
         */
        private String name;
    }

    @Data
    public static class SmtpConfig {
        /**
         * Enable SMTP provider.
         */
        private boolean enabled = false;

        /**
         * SMTP server host.
         */
        private String host;

        /**
         * SMTP server port.
         */
        private int port = 587;

        /**
         * SMTP username.
         */
        private String username;

        /**
         * SMTP password.
         */
        private String password;

        /**
         * Enable STARTTLS.
         */
        private boolean starttls = true;

        /**
         * Enable SSL.
         */
        private boolean ssl = false;

        /**
         * Connection timeout in milliseconds.
         */
        private int connectionTimeout = 10000;

        /**
         * Read timeout in milliseconds.
         */
        private int timeout = 10000;
    }

    @Data
    public static class AwsSesConfig {
        /**
         * Enable AWS SES provider.
         */
        private boolean enabled = false;

        /**
         * AWS region for SES.
         */
        private String region;

        /**
         * SES configuration set name (optional).
         */
        private String configurationSet;

        /**
         * AWS access key (optional, can use default credential chain).
         */
        private String accessKey;

        /**
         * AWS secret key (optional, can use default credential chain).
         */
        private String secretKey;
    }

    @Data
    public static class SendGridConfig {
        /**
         * Enable SendGrid provider.
         */
        private boolean enabled = false;

        /**
         * SendGrid API key.
         */
        private String apiKey;
    }

    @Data
    public static class ResendConfig {
        /**
         * Enable Resend provider.
         */
        private boolean enabled = false;

        /**
         * Resend API key.
         */
        private String apiKey;
    }

    @Data
    public static class TemplateConfig {
        /**
         * Base path for classpath templates.
         */
        private String basePath = "templates/email";

        /**
         * Enable database template resolver.
         */
        private boolean databaseEnabled = false;
    }

    @Data
    public static class AsyncConfig {
        /**
         * Core pool size for async executor.
         */
        private int corePoolSize = 2;

        /**
         * Maximum pool size for async executor.
         */
        private int maxPoolSize = 10;

        /**
         * Queue capacity for async executor.
         */
        private int queueCapacity = 100;

        /**
         * Thread name prefix.
         */
        private String threadNamePrefix = "email-async-";
    }
}
