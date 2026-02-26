package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.web.config.openapi.DtoSchemaAutoRegistrar;
import dev.simplecore.simplix.web.config.openapi.EnumSchemaExtractor;
import dev.simplecore.simplix.web.config.openapi.GenericResponseSchemaCustomizer;
import dev.simplecore.simplix.web.config.openapi.NestedObjectSchemaExtractor;
import dev.simplecore.simplix.web.config.openapi.OperationIdCustomizer;
import dev.simplecore.simplix.web.config.openapi.SchemaOrganizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Auto-configuration for Swagger/OpenAPI documentation with Scalar UI support.
 * Uses Spring Boot's default springdoc properties.
 *
 * <p>IMPORTANT: This configuration uses string-based @ConditionalOnClass to avoid
 * ClassNotFoundException when SpringDoc OpenAPI dependencies are not present.
 *
 * <p>Configure in application.yml:
 * <pre>
 * springdoc:
 *   api-docs:
 *     enabled: true
 *     path: /v3/api-docs
 *   swagger-ui:
 *     enabled: true
 *     path: /swagger-ui.html
 *   info:
 *     title: API Documentation
 *     description: API Description
 *     version: 1.0.0
 *     terms-of-service: https://example.com/terms
 *     contact:
 *       name: API Support
 *       email: support@example.com
 *       url: https://example.com/support
 *     license:
 *       name: Apache 2.0
 *       url: https://www.apache.org/licenses/LICENSE-2.0
 *   packages-to-scan: com.example.controller
 *
 * # Scalar UI (modern alternative to Swagger UI)
 * # Dependency: com.scalar.maven:scalar (included in spring-boot-starter-simplix)
 * # See: https://github.com/scalar/scalar
 * scalar:
 *   enabled: true            # Enable Scalar UI (default: true when dependency present)
 *   url: /v3/api-docs        # OpenAPI specification URL
 *   path: /scalar            # Scalar UI endpoint path
 * </pre>
 *
 * <p>Scalar UI endpoints:
 * - /scalar - API Reference UI
 * - /v3/api-docs - OpenAPI JSON specification
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXSwaggerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXSwaggerAutoConfiguration.class);

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SpringDocConfigProperties springDocConfigProperties() {
        log.info("Initializing SimpliX Swagger/OpenAPI v2.7.0...");
        log.info("OpenAPI endpoints:");
        log.info("  - API Docs (JSON): /v3/api-docs");
        log.info("  - Swagger UI: /swagger-ui.html");
        log.info("  - Scalar UI: /scalar (set scalar.enabled=true)");

        SpringDocConfigProperties properties = new SpringDocConfigProperties();
        properties.setShowActuator(false);

        // API Docs configuration
        SpringDocConfigProperties.ApiDocs apiDocs = new SpringDocConfigProperties.ApiDocs();
        apiDocs.setPath("/v3/api-docs");
        apiDocs.setEnabled(true);
        properties.setApiDocs(apiDocs);

        return properties;
    }

    /**
     * Optional EnumSchemaExtractor bean.
     * Enabled by default, can be disabled by setting: simplix.swagger.customizers.enum-extractor.enabled=false
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simplix.swagger.customizers.enum-extractor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public EnumSchemaExtractor enumSchemaExtractor() {
        log.info("Enabling SimpliX EnumSchemaExtractor");
        log.info("  To disable: simplix.swagger.customizers.enum-extractor.enabled=false");
        return new EnumSchemaExtractor();
    }

    /**
     * Optional NestedObjectSchemaExtractor bean.
     * Enabled by default, can be disabled by setting: simplix.swagger.customizers.nested-object-extractor.enabled=false
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simplix.swagger.customizers.nested-object-extractor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NestedObjectSchemaExtractor nestedObjectSchemaExtractor() {
        log.info("Enabling SimpliX NestedObjectSchemaExtractor");
        log.info("  To disable: simplix.swagger.customizers.nested-object-extractor.enabled=false");
        return new NestedObjectSchemaExtractor();
    }

    /**
     * Generic response schema customizer that resolves type erasure issues
     * for nested generic return types like {@code SimpliXApiResponse<Page<UserDto>>}.
     *
     * <p>Uses {@link org.springframework.core.ResolvableType} to recover erased
     * generic type information and build accurate OpenAPI schemas.
     *
     * <p>When {@code auto-wrap} is enabled, non-SimpliXApiResponse return types
     * are automatically wrapped with SimpliXApiResponse schema structure,
     * matching the runtime behavior of SimpliXResponseBodyAdvice.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simplix.swagger.customizers.generic-response", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GenericResponseSchemaCustomizer genericResponseSchemaCustomizer(
            @Value("${simplix.swagger.customizers.generic-response.auto-wrap:true}") boolean autoWrap) {
        log.info("Enabling SimpliX GenericResponseSchemaCustomizer (auto-wrap: {})", autoWrap);
        log.info("  To disable: simplix.swagger.customizers.generic-response.enabled=false");
        return new GenericResponseSchemaCustomizer(autoWrap);
    }

    /**
     * Operation ID customizer that generates unique operationId values
     * by combining controller class name with method name.
     * Prevents SpringDoc's default _N suffix numbering for duplicate method names.
     */
    @Bean
    @ConditionalOnMissingBean
    public OperationIdCustomizer operationIdCustomizer() {
        return new OperationIdCustomizer();
    }

    /**
     * Schema organizer that sorts schemas alphabetically and marks enums
     * with {@code x-schema-type: "enum"} extension.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchemaOrganizer schemaOrganizer() {
        return new SchemaOrganizer();
    }

    /**
     * DTO schema auto-registrar that ensures all DTO classes referenced
     * by {@link GenericResponseSchemaCustomizer}'s {@code $ref} pointers
     * are registered in OpenAPI components.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simplix.swagger.customizers.generic-response", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DtoSchemaAutoRegistrar dtoSchemaAutoRegistrar(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        log.info("Enabling SimpliX DtoSchemaAutoRegistrar");
        return new DtoSchemaAutoRegistrar(handlerMapping);
    }

}