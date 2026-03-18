package dev.simplecore.simplix.auth.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("SimpliXAuthOpenApiConfiguration")
@ExtendWith(MockitoExtension.class)
class SimpliXAuthOpenApiConfigurationTest {

    @Mock
    private MessageSource messageSource;

    @Test
    @DisplayName("should create OpenAPI with Bearer and Basic security schemes")
    void shouldCreateOpenApiWithSecuritySchemes() {
        when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenReturn("description");

        SimpliXAuthOpenApiConfiguration config = new SimpliXAuthOpenApiConfiguration();
        OpenAPI openAPI = config.customOpenAPI(messageSource);

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("Bearer");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("Basic");
        assertThat(openAPI.getComponents().getSecuritySchemes().get("Bearer").getScheme()).isEqualTo("bearer");
        assertThat(openAPI.getComponents().getSecuritySchemes().get("Basic").getScheme()).isEqualTo("basic");
        assertThat(openAPI.getSecurity()).isNotEmpty();
    }
}
