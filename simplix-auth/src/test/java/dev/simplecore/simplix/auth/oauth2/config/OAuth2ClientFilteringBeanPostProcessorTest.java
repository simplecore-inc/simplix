package dev.simplecore.simplix.auth.oauth2.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2ClientFilteringBeanPostProcessor")
class OAuth2ClientFilteringBeanPostProcessorTest {

    private OAuth2ClientFilteringBeanPostProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OAuth2ClientFilteringBeanPostProcessor();
    }

    @Test
    @DisplayName("should remove registrations with empty client ID")
    void shouldRemoveEmptyClientId() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        OAuth2ClientProperties.Registration emptyReg = new OAuth2ClientProperties.Registration();
        emptyReg.setClientId("");
        properties.getRegistration().put("google", emptyReg);

        OAuth2ClientProperties.Registration validReg = new OAuth2ClientProperties.Registration();
        validReg.setClientId("valid-client-id");
        validReg.setClientSecret("secret");
        properties.getRegistration().put("github", validReg);

        processor.postProcessBeforeInitialization(properties, "oauth2ClientProperties");

        assertThat(properties.getRegistration()).containsKey("github");
        assertThat(properties.getRegistration()).doesNotContainKey("google");
    }

    @Test
    @DisplayName("should remove registrations with null client ID")
    void shouldRemoveNullClientId() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        OAuth2ClientProperties.Registration nullReg = new OAuth2ClientProperties.Registration();
        // clientId defaults to null
        properties.getRegistration().put("kakao", nullReg);

        processor.postProcessBeforeInitialization(properties, "oauth2ClientProperties");

        assertThat(properties.getRegistration()).doesNotContainKey("kakao");
    }

    @Test
    @DisplayName("should keep registrations with valid client ID")
    void shouldKeepValidRegistrations() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        OAuth2ClientProperties.Registration validReg = new OAuth2ClientProperties.Registration();
        validReg.setClientId("my-client-id");
        validReg.setClientSecret("my-secret");
        properties.getRegistration().put("google", validReg);

        processor.postProcessBeforeInitialization(properties, "oauth2ClientProperties");

        assertThat(properties.getRegistration()).containsKey("google");
    }

    @Test
    @DisplayName("should handle empty registration map")
    void shouldHandleEmptyRegistrationMap() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();

        Object result = processor.postProcessBeforeInitialization(properties, "bean");

        assertThat(result).isSameAs(properties);
    }

    @Test
    @DisplayName("should pass through non-OAuth2ClientProperties beans")
    void shouldPassThroughOtherBeans() {
        String bean = "some other bean";

        Object result = processor.postProcessBeforeInitialization(bean, "bean");

        assertThat(result).isSameAs(bean);
    }

    @Test
    @DisplayName("should have high priority order")
    void shouldHaveHighPriorityOrder() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
