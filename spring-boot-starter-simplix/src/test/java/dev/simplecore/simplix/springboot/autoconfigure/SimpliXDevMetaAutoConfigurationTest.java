package dev.simplecore.simplix.springboot.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.web.advice.SimpliXExceptionHandler;
import dev.simplecore.simplix.web.meta.SimpliXMetaDevController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    /**
     * Stubs for the beans that {@code SimpliXAutoConfiguration}'s own {@code @ComponentScan}
     * pulls in beside the dev endpoints (namely {@code SimpliXErrorController}), so the umbrella
     * configuration can actually load in a slice instead of only being asserted on by reflection.
     *
     * <p>{@code simpliXExceptionHandler} is {@code @Primary}: the same scan also picks up
     * {@code SimpliXExceptionHandlerAdditionalTest.TestExceptionHandler} — a
     * {@code @RestControllerAdvice} test helper that happens to sit in the scanned
     * {@code dev.simplecore.simplix.web} package tree — which otherwise ties for the single
     * {@code SimpliXExceptionHandler<SimpliXApiResponse<Object>>} bean this stub provides.
     */
    @Configuration
    static class UmbrellaStubs {
        @Bean MessageSource messageSource() { return mock(MessageSource.class); }
        @Bean ErrorAttributes errorAttributes() { return mock(ErrorAttributes.class); }
        @Primary
        @Bean SimpliXExceptionHandler<SimpliXApiResponse<Object>> simpliXExceptionHandler(
                MessageSource messageSource, ObjectMapper objectMapper) {
            return new SimpliXExceptionHandler<>(messageSource, objectMapper);
        }
    }

    /**
     * Regression test for the umbrella {@code @ComponentScan} in {@code SimpliXAutoConfiguration}
     * registering {@link SimpliXMetaDevController} unconditionally, bypassing the property gate
     * and the not-production guard on {@link SimpliXDevMetaAutoConfiguration}. Loading only
     * {@code SimpliXDevMetaAutoConfiguration} (as the tests above do) never exercises that scan,
     * so it never caught the collision between the two. Loading the umbrella here does.
     */
    @Test
    void umbrellaScanDoesNotRegisterDevControllerWithoutTheProperty() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpliXAutoConfiguration.class))
                .withUserConfiguration(Stubs.class, UmbrellaStubs.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SimpliXMetaDevController.class);
                });
    }
}
