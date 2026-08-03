package dev.simplecore.simplix.auth.web;

import dev.simplecore.simplix.auth.properties.SimpliXAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXAuthLoginController")
class SimpliXAuthLoginControllerTest {

    private SimpliXAuthProperties properties;
    private SimpliXAuthLoginController controller;
    private Model model;

    @BeforeEach
    void setUp() {
        properties = new SimpliXAuthProperties();
        controller = new SimpliXAuthLoginController(properties);
        model = new ConcurrentModel();
    }

    @Test
    @DisplayName("should return default login page template")
    void shouldReturnDefaultLoginTemplate() {
        String result = controller.login(model);

        assertThat(result).isEqualTo("login");
    }

    @Test
    @DisplayName("should return custom login page template")
    void shouldReturnCustomLoginTemplate() {
        properties.getSecurity().setLoginPageTemplate("custom-login");

        String result = controller.login(model);

        assertThat(result).isEqualTo("custom-login");
    }

    @Test
    @DisplayName("should expose the configured login processing URL to the view")
    void shouldExposeLoginProcessingUrl() {
        properties.getSecurity().setLoginProcessingUrl("/backend/login");

        controller.login(model);

        assertThat(model.getAttribute("loginProcessingUrl")).isEqualTo("/backend/login");
    }

    @Test
    @DisplayName("should prefix a processing URL configured without a leading slash")
    void shouldPrefixProcessingUrlWithoutLeadingSlash() {
        properties.getSecurity().setLoginProcessingUrl("backend/login");

        controller.login(model);

        assertThat(model.getAttribute("loginProcessingUrl")).isEqualTo("/backend/login");
    }
}
