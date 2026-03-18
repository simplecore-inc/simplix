package dev.simplecore.simplix.auth.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthEnvironmentPostProcessor")
class SimpliXAuthEnvironmentPostProcessorTest {

    @Test
    @DisplayName("should add default auth properties")
    void shouldAddDefaultProperties() {
        SimpliXAuthEnvironmentPostProcessor processor = new SimpliXAuthEnvironmentPostProcessor();
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("simplix.auth.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("simplix.auth.token.enable-ip-validation")).isEqualTo("false");
        assertThat(environment.getProperty("simplix.auth.token.enable-user-agent-validation")).isEqualTo("false");
        assertThat(environment.getProperty("simplix.auth.token.enable-token-rotation")).isEqualTo("true");
        assertThat(environment.getProperty("simplix.auth.token.enable-blacklist")).isEqualTo("false");
        assertThat(environment.getProperty("simplix.auth.token.create-session-on-token-issue")).isEqualTo("true");
    }

    @Test
    @DisplayName("should add default security properties")
    void shouldAddDefaultSecurityProperties() {
        SimpliXAuthEnvironmentPostProcessor processor = new SimpliXAuthEnvironmentPostProcessor();
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("simplix.auth.security.require-https")).isEqualTo("false");
        assertThat(environment.getProperty("simplix.auth.security.enable-csrf")).isEqualTo("true");
        assertThat(environment.getProperty("simplix.auth.security.enable-cors")).isEqualTo("true");
    }

    @Test
    @DisplayName("should add default CORS properties with correct port")
    void shouldAddDefaultCorsProperties() {
        SimpliXAuthEnvironmentPostProcessor processor = new SimpliXAuthEnvironmentPostProcessor();
        MockEnvironment environment = new MockEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("simplix.auth.cors.allowed-origins"))
                .isEqualTo("http://localhost:8080");
        assertThat(environment.getProperty("simplix.auth.cors.allowed-methods")).isEqualTo("*");
        assertThat(environment.getProperty("simplix.auth.cors.allow-credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("should use configured server port for CORS origin")
    void shouldUseConfiguredPort() {
        SimpliXAuthEnvironmentPostProcessor processor = new SimpliXAuthEnvironmentPostProcessor();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("server.port", "9090");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("simplix.auth.cors.allowed-origins"))
                .isEqualTo("http://localhost:9090");
    }

    @Test
    @DisplayName("should not override existing application properties")
    void shouldNotOverrideExistingProperties() {
        SimpliXAuthEnvironmentPostProcessor processor = new SimpliXAuthEnvironmentPostProcessor();
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("simplix.auth.enabled", "false");

        processor.postProcessEnvironment(environment, new SpringApplication());

        // The existing property should take precedence because
        // defaults are added with addLast
        assertThat(environment.getProperty("simplix.auth.enabled")).isEqualTo("false");
    }
}
