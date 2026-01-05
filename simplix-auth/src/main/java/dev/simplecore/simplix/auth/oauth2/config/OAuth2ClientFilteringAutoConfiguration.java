package dev.simplecore.simplix.auth.oauth2.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;

/**
 * Auto-configuration for OAuth2 client registration filtering.
 * <p>
 * This configuration registers a {@link OAuth2ClientFilteringBeanPostProcessor} that
 * removes OAuth2 client registrations with empty client IDs before Spring's
 * validation runs. This prevents application startup failures when some OAuth2
 * providers are not configured.
 * <p>
 * The BeanPostProcessor is registered as a static bean to ensure it runs
 * very early in the Spring lifecycle.
 *
 * @since 1.0.18
 * @see OAuth2ClientFilteringBeanPostProcessor
 */
@AutoConfiguration(
        beforeName = "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
)
@ConditionalOnClass(OAuth2LoginConfigurer.class)
public class OAuth2ClientFilteringAutoConfiguration {

    /**
     * Creates the OAuth2 client filtering bean post processor.
     * <p>
     * This bean MUST be static to ensure it is processed during the
     * BeanPostProcessor registration phase, before regular beans are created.
     *
     * @return the filtering bean post processor
     */
    @Bean
    public static OAuth2ClientFilteringBeanPostProcessor oauth2ClientFilteringBeanPostProcessor() {
        return new OAuth2ClientFilteringBeanPostProcessor();
    }
}
