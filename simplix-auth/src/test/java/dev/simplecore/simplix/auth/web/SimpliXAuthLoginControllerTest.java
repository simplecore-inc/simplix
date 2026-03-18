package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthLoginController")
class SimpliXAuthLoginControllerTest {

    private SimpliXAuthProperties properties;
    private SimpliXAuthLoginController controller;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
        controller = new SimpliXAuthLoginController(properties);
    }

    @Test
    @DisplayName("should return default login page template")
    void shouldReturnDefaultLoginTemplate() {
        String result = controller.login();

        assertThat(result).isEqualTo("login");
    }

    @Test
    @DisplayName("should return custom login page template")
    void shouldReturnCustomLoginTemplate() {
        properties.getSecurity().setLoginPageTemplate("custom-login");

        String result = controller.login();

        assertThat(result).isEqualTo("custom-login");
    }
}
