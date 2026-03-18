package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import dev.simplecore.simplix.web.config.openapi.SwaggerSchemaEnhancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimpliXWebAutoConfiguration - web-specific auto-configuration")
class SimpliXWebAutoConfigurationTest {

    @Test
    @DisplayName("Should be annotated with @ConditionalOnWebApplication")
    void conditionalOnWebApplication() {
        ConditionalOnWebApplication annotation =
                SimpliXWebAutoConfiguration.class.getAnnotation(ConditionalOnWebApplication.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    @DisplayName("Should contain ExceptionHandlerConfiguration inner class")
    void hasExceptionHandlerConfig() {
        Class<?>[] innerClasses = SimpliXWebAutoConfiguration.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> inner : innerClasses) {
            if (inner.getSimpleName().equals("ExceptionHandlerConfiguration")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Should contain SwaggerI18nConfiguration inner class")
    void hasSwaggerI18nConfig() {
        Class<?>[] innerClasses = SimpliXWebAutoConfiguration.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> inner : innerClasses) {
            if (inner.getSimpleName().equals("SwaggerI18nConfiguration")) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Nested
    @DisplayName("ExceptionHandlerConfiguration with WebApplicationContextRunner")
    class ExceptionHandlerAutoConfig {

        private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpliXWebAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class);

        @Test
        @DisplayName("Should create SimpliXExceptionHandler bean when enabled")
        void createsExceptionHandler() {
            contextRunner
                    .withPropertyValues("simplix.exception-handler.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(SimpliXExceptionHandler.class);
                    });
        }

        @Test
        @DisplayName("Should create SimpliXExceptionHandler bean by default (matchIfMissing=true)")
        void createsExceptionHandlerByDefault() {
            contextRunner
                    .run(context -> {
                        assertThat(context).hasSingleBean(SimpliXExceptionHandler.class);
                    });
        }

        @Test
        @DisplayName("Should not create SimpliXExceptionHandler when disabled")
        void noExceptionHandlerWhenDisabled() {
            contextRunner
                    .withPropertyValues("simplix.exception-handler.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(SimpliXExceptionHandler.class);
                    });
        }
    }

    @Nested
    @DisplayName("SwaggerI18nConfiguration with WebApplicationContextRunner")
    class SwaggerI18nAutoConfig {

        private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpliXWebAutoConfiguration.class))
                .withUserConfiguration(TestConfig.class);

        @Test
        @DisplayName("Should create SwaggerSchemaEnhancer bean when swagger i18n is enabled")
        void createsSwaggerSchemaEnhancer() {
            contextRunner
                    .withPropertyValues("simplix.swagger.i18n-enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(SwaggerSchemaEnhancer.class);
                    });
        }

        @Test
        @DisplayName("Should create SwaggerSchemaEnhancer bean by default (matchIfMissing=true)")
        void createsSwaggerSchemaEnhancerByDefault() {
            contextRunner
                    .run(context -> {
                        assertThat(context).hasSingleBean(SwaggerSchemaEnhancer.class);
                    });
        }

        @Test
        @DisplayName("Should not create SwaggerSchemaEnhancer when disabled")
        void noSwaggerSchemaEnhancerWhenDisabled() {
            contextRunner
                    .withPropertyValues("simplix.swagger.i18n-enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(SwaggerSchemaEnhancer.class);
                    });
        }

        @Test
        @DisplayName("Should pass additional packages to SwaggerSchemaEnhancer")
        void passAdditionalPackages() {
            contextRunner
                    .withPropertyValues("simplix.swagger.i18n-packages=com.example.model,com.example.dto")
                    .run(context -> {
                        assertThat(context).hasSingleBean(SwaggerSchemaEnhancer.class);
                    });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        public MessageSource messageSource() {
            return new StaticMessageSource();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
