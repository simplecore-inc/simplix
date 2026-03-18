package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXSecurityAutoConfiguration - security defaults when auth is disabled")
class SimpliXSecurityAutoConfigurationTest {

    @Test
    @DisplayName("Should be conditional on EnableWebSecurity class")
    void conditionalOnWebSecurity() {
        ConditionalOnClass annotation =
                SimpliXSecurityAutoConfiguration.class.getAnnotation(ConditionalOnClass.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should be activated only when simplix.auth.enabled=false")
    void conditionalOnAuthDisabled() {
        ConditionalOnProperty annotation =
                SimpliXSecurityAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("simplix.auth");
        assertThat(annotation.name()).contains("enabled");
        assertThat(annotation.havingValue()).isEqualTo("false");
        assertThat(annotation.matchIfMissing()).isFalse();
    }
}
