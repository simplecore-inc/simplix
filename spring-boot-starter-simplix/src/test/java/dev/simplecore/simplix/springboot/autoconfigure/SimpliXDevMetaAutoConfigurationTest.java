package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.web.meta.SimpliXMetaDevController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SimpliXDevMetaAutoConfigurationTest {

    @Configuration
    static class Stubs {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean RequestMappingHandlerMapping requestMappingHandlerMapping() {
            return mock(RequestMappingHandlerMapping.class);
        }
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpliXDevMetaAutoConfiguration.class))
            .withUserConfiguration(Stubs.class);

    @Test
    void offByDefault() {
        runner.run(context ->
            assertThat(context).doesNotHaveBean(SimpliXMetaDevController.class));
    }

    @Test
    void onWhenPropertyEnabled() {
        runner.withPropertyValues("simplix.dev.meta.enabled=true").run(context ->
            assertThat(context).hasSingleBean(SimpliXMetaDevController.class));
    }

    @Test
    void refusedUnderProductionProfile() {
        runner.withPropertyValues("simplix.dev.meta.enabled=true",
                        "spring.profiles.active=prod")
              .run(context ->
            assertThat(context).doesNotHaveBean(SimpliXMetaDevController.class));
    }
}
