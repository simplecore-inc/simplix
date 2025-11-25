package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.web.config.EnumSchemaExtractor;
import dev.simplecore.simplix.web.config.NestedObjectSchemaExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

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
 * # Scalar UI (modern alternative to Swagger UI) - OPTIONAL
 * # Requires: implementation 'org.springdoc:springdoc-openapi-starter-webmvc-scalar'
 * # NOTE: Uses 'scalar.*' prefix, NOT 'springdoc.scalar.*'
 * scalar:
 *   enabled: true            # Enable modern Scalar UI (default: false)
 *   path: /scalar            # Scalar UI path
 * </pre>
 *
 * <p>Scalar UI Support (Optional):
 * - Add dependency: org.springdoc:springdoc-openapi-starter-webmvc-scalar
 * - Access at /scalar when enabled (scalar.enabled=true)
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
@EnableConfigurationProperties(SpringDocConfigProperties.class)
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
        log.info("  - Scalar UI: /scalar (optional - add dependency & set scalar.enabled=true)");

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

    private String getMainPath(String name) {
        String path = name.replaceAll("([a-z])([A-Z])", "$1/$2").toLowerCase();
        return path.split("/")[0];
    }
}