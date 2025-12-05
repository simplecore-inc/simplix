package dev.simplecore.simplix.email.config;

import com.sendgrid.SendGrid;
import dev.simplecore.simplix.email.provider.SendGridEmailProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SendGrid email provider.
 * <p>
 * This configuration is only loaded when SendGrid SDK is on the classpath
 * and SendGrid is enabled in the configuration.
 */
@Configuration
@ConditionalOnClass(name = "com.sendgrid.SendGrid")
@ConditionalOnProperty(prefix = "simplix.email.sendgrid", name = "enabled", havingValue = "true")
public class SendGridConfiguration {

    @Bean
    public SendGrid sendGridClient(EmailProperties properties) {
        return new SendGrid(properties.getSendgrid().getApiKey());
    }

    @Bean
    public SendGridEmailProvider sendGridEmailProvider(SendGrid sendGrid, EmailProperties properties) {
        return new SendGridEmailProvider(
                sendGrid,
                properties.getFrom().getAddress(),
                properties.getFrom().getName()
        );
    }
}
