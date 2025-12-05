package dev.simplecore.simplix.email.config;

import dev.simplecore.simplix.email.model.EmailAddress;
import dev.simplecore.simplix.email.provider.ConsoleEmailProvider;
import dev.simplecore.simplix.email.provider.EmailProvider;
import dev.simplecore.simplix.email.provider.SmtpEmailProvider;
import dev.simplecore.simplix.email.service.DefaultEmailService;
import dev.simplecore.simplix.email.service.EmailService;
import dev.simplecore.simplix.email.template.ClasspathEmailTemplateResolver;
import dev.simplecore.simplix.email.template.EmailTemplateEngine;
import dev.simplecore.simplix.email.template.EmailTemplateResolver;
import dev.simplecore.simplix.email.template.EmailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Auto-configuration for email module.
 * <p>
 * Automatically configures email providers, template engine, and email service
 * based on application properties.
 * <p>
 * Provider-specific configurations (AWS SES, SendGrid, Resend) are in separate
 * configuration classes that are only loaded when their respective SDKs are
 * on the classpath.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(EmailProperties.class)
@ConditionalOnProperty(prefix = "simplix.email", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailAutoConfiguration {

    /**
     * Text template engine for processing subject and plain text body.
     * <p>
     * Uses TEXT mode for [(${...})] syntax in subject.txt and body.txt files.
     */
    @Bean
    @ConditionalOnMissingBean(name = "textTemplateEngine")
    public TemplateEngine textTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCacheable(false);
        engine.addTemplateResolver(resolver);
        return engine;
    }

    /**
     * HTML template engine for processing HTML body.
     * <p>
     * Uses HTML mode for ${...} syntax in body.html files.
     */
    @Bean
    @ConditionalOnMissingBean(name = "htmlTemplateEngine")
    public TemplateEngine htmlTemplateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        engine.addTemplateResolver(resolver);
        return engine;
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailTemplateEngine emailTemplateEngineWrapper(
            @Qualifier("textTemplateEngine") TemplateEngine textTemplateEngine,
            @Qualifier("htmlTemplateEngine") TemplateEngine htmlTemplateEngine) {
        return new EmailTemplateEngine(textTemplateEngine, htmlTemplateEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClasspathEmailTemplateResolver classpathEmailTemplateResolver(EmailProperties properties) {
        return new ClasspathEmailTemplateResolver(properties.getTemplate().getBasePath());
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailTemplateService emailTemplateService(List<EmailTemplateResolver> resolvers,
                                                      EmailTemplateEngine templateEngine) {
        return new EmailTemplateService(resolvers, templateEngine);
    }

    @Bean
    @ConditionalOnMissingBean(name = "emailAsyncExecutor")
    public Executor emailAsyncExecutor(EmailProperties properties) {
        EmailProperties.AsyncConfig asyncConfig = properties.getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncConfig.getCorePoolSize());
        executor.setMaxPoolSize(asyncConfig.getMaxPoolSize());
        executor.setQueueCapacity(asyncConfig.getQueueCapacity());
        executor.setThreadNamePrefix(asyncConfig.getThreadNamePrefix());
        executor.initialize();

        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsoleEmailProvider consoleEmailProvider(EmailProperties properties) {
        boolean enabled = properties.getProvider() == dev.simplecore.simplix.email.model.MailProviderType.CONSOLE;
        return new ConsoleEmailProvider(enabled);
    }

    @Bean
    @ConditionalOnProperty(prefix = "simplix.email.smtp", name = "enabled", havingValue = "true")
    public JavaMailSender javaMailSender(EmailProperties properties) {
        EmailProperties.SmtpConfig smtp = properties.getSmtp();

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtp.getHost());
        mailSender.setPort(smtp.getPort());
        mailSender.setUsername(smtp.getUsername());
        mailSender.setPassword(smtp.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", smtp.getUsername() != null);
        props.put("mail.smtp.starttls.enable", smtp.isStarttls());
        props.put("mail.smtp.ssl.enable", smtp.isSsl());
        props.put("mail.smtp.connectiontimeout", smtp.getConnectionTimeout());
        props.put("mail.smtp.timeout", smtp.getTimeout());

        return mailSender;
    }

    @Bean
    @ConditionalOnProperty(prefix = "simplix.email.smtp", name = "enabled", havingValue = "true")
    public SmtpEmailProvider smtpEmailProvider(JavaMailSender mailSender, EmailProperties properties) {
        return new SmtpEmailProvider(
                mailSender,
                properties.getFrom().getAddress(),
                properties.getFrom().getName()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailService emailService(List<EmailProvider> providers,
                                     EmailTemplateService templateService,
                                     Executor emailAsyncExecutor,
                                     EmailProperties properties) {
        EmailAddress defaultFrom = null;
        if (properties.getFrom().getAddress() != null) {
            defaultFrom = EmailAddress.of(
                    properties.getFrom().getName(),
                    properties.getFrom().getAddress()
            );
        }

        List<EmailProvider> availableProviders = new ArrayList<>(providers);

        log.info("Configuring EmailService with providers: {}",
                availableProviders.stream()
                        .map(p -> p.getType().name() + "(available=" + p.isAvailable() + ")")
                        .toList());

        return new DefaultEmailService(
                availableProviders,
                templateService,
                emailAsyncExecutor,
                defaultFrom
        );
    }
}
