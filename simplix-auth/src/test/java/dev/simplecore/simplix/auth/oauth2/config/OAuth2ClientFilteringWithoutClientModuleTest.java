package dev.simplecore.simplix.auth.oauth2.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application that uses Spring Security without Spring Boot's OAuth2 client module, such as a
 * resource server, starts without the client filtering.
 */
class OAuth2ClientFilteringWithoutClientModuleTest {

    @Test
    void starts_without_the_boot_oauth2_client_module() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.security.oauth2.client"))
                .withConfiguration(AutoConfigurations.of(OAuth2ClientFilteringAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OAuth2ClientFilteringBeanPostProcessor.class);
                });
    }
}
