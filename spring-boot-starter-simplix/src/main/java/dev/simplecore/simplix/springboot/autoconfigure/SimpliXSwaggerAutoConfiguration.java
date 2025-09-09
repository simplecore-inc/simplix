package dev.simplecore.simplix.springboot.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Swagger/OpenAPI documentation.
 * Uses Spring Boot's default springdoc properties.
 *
 * <p>Configure in application.yml:
 * <pre>
 * springdoc:
 *   api-docs:
 *     enabled: true
 *     path: /v3/api-docs
 *   swagger-ui:
 *     path: /swagger-ui.html
 *     enabled: true
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
 * </pre>
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@EnableConfigurationProperties(SpringDocConfigProperties.class)
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXSwaggerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXSwaggerAutoConfiguration.class);

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SpringDocConfigProperties springDocConfigProperties() {
        log.info("Initializing SimpliX Swagger/OpenAPI...");
        SpringDocConfigProperties properties = new SpringDocConfigProperties();
        properties.setShowActuator(false);
        properties.setApiDocs(new SpringDocConfigProperties.ApiDocs());
        properties.getApiDocs().setPath("/v3/api-docs");
        return properties;
    }

    private String getMainPath(String name) {
        String path = name.replaceAll("([a-z])([A-Z])", "$1/$2").toLowerCase();
        return path.split("/")[0];
    }
}