package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.springboot.properties.SimpliXProperties;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import dev.simplecore.simplix.web.config.SwaggerSchemaEnhancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableConfigurationProperties(SimpliXProperties.class)
@AutoConfigureAfter({WebMvcAutoConfiguration.class})
public class SimpliXWebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SimpliXWebAutoConfiguration.class);

    @Configuration
    @ConditionalOnProperty(prefix = "simplix.exception-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class ExceptionHandlerConfiguration {

        @Bean
        @ConditionalOnMissingBean(SimpliXExceptionHandler.class)
        @Order(Integer.MAX_VALUE)
        public SimpliXExceptionHandler<?> simpliXExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
            log.info("Initializing SimpliX Web Exception Handler...");
            return new DefaultSimpliXExceptionHandler(messageSource, objectMapper);
        }

        @RestControllerAdvice
        private static class DefaultSimpliXExceptionHandler extends SimpliXExceptionHandler<SimpliXApiResponse<Object>> {
            DefaultSimpliXExceptionHandler(MessageSource messageSource, ObjectMapper objectMapper) {
                super(messageSource, objectMapper);
            }
        }
    }

    @Configuration
    @ConditionalOnClass(name = "io.swagger.v3.oas.models.OpenAPI")
    @ConditionalOnProperty(prefix = "simplix.swagger", name = "i18n-enabled", havingValue = "true", matchIfMissing = true)
    static class SwaggerI18nConfiguration {

        @Bean
        @ConditionalOnMissingBean(SwaggerSchemaEnhancer.class)
        public SwaggerSchemaEnhancer swaggerSchemaEnhancer() {
            log.info("Initializing SimpliX Swagger Schema Enhancer as OpenApiCustomiser...");
            return new SwaggerSchemaEnhancer();
        }
    }
} 