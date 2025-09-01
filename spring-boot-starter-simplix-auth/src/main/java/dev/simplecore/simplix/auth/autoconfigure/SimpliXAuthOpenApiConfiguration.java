package dev.simplecore.simplix.auth.autoconfigure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;

@AutoConfiguration
@ConditionalOnProperty(prefix = "simplix.auth.security", name = "enable-token-endpoints", havingValue = "true", matchIfMissing = true)
public class SimpliXAuthOpenApiConfiguration {
    
    @Bean
    public OpenAPI customOpenAPI(MessageSource messageSource) {
        return new OpenAPI()
            .components(new Components()
                .addSecuritySchemes("Bearer", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWE")
                    .description(messageSource.getMessage("openapi.security.bearer.description", null,
                        "JWE token for API authentication", LocaleContextHolder.getLocale())))
                .addSecuritySchemes("Basic", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")
                    .description(messageSource.getMessage("openapi.security.basic.description", null,
                        "Basic authentication for token issuance", LocaleContextHolder.getLocale()))))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
} 