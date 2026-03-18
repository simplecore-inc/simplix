package dev.simplecore.simplix.auth.oauth2.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2ClientFilteringAutoConfiguration")
class OAuth2ClientFilteringAutoConfigurationTest {

    @Test
    @DisplayName("should create OAuth2ClientFilteringBeanPostProcessor")
    void shouldCreateBeanPostProcessor() {
        OAuth2ClientFilteringBeanPostProcessor processor =
                OAuth2ClientFilteringAutoConfiguration.oauth2ClientFilteringBeanPostProcessor();
        assertThat(processor).isNotNull();
    }
}
