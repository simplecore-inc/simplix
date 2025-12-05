package dev.simplecore.simplix.email.config;

import com.resend.Resend;
import dev.simplecore.simplix.email.provider.ResendEmailProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Resend email provider.
 * <p>
 * This configuration is only loaded when Resend SDK is on the classpath
 * and Resend is enabled in the configuration.
 */
@Configuration
@ConditionalOnClass(name = "com.resend.Resend")
@ConditionalOnProperty(prefix = "simplix.email.resend", name = "enabled", havingValue = "true")
public class ResendConfiguration {

    @Bean
    public Resend resendClient(EmailProperties properties) {
        return new Resend(properties.getResend().getApiKey());
    }

    @Bean
    public ResendEmailProvider resendEmailProvider(Resend resend, EmailProperties properties) {
        return new ResendEmailProvider(
                resend,
                properties.getFrom().getAddress(),
                properties.getFrom().getName()
        );
    }
}
