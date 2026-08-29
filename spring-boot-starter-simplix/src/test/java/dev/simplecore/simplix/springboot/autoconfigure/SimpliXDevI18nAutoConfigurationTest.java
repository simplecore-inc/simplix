package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.web.i18n.I18nMessagesDevRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SimpliXDevI18nAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpliXDevI18nAutoConfiguration.class));

    @Test
    void offByDefault() {
        runner.run(context ->
            assertThat(context).doesNotHaveBean(I18nMessagesDevRestController.class));
    }

    @Test
    void onWhenPropertyEnabled() {
        runner.withPropertyValues("simplix.dev.i18n.enabled=true")
              .withInitializer(ctx -> AutoConfigurationPackages.register(
                      (BeanDefinitionRegistry) ctx.getBeanFactory(), "dev.simplecore.simplix"))
              .run(context ->
            assertThat(context).hasSingleBean(I18nMessagesDevRestController.class));
    }

    @Test
    void refusedUnderProductionProfile() {
        runner.withPropertyValues("simplix.dev.i18n.enabled=true",
                        "spring.profiles.active=prod")
              .withInitializer(ctx -> AutoConfigurationPackages.register(
                      (BeanDefinitionRegistry) ctx.getBeanFactory(), "dev.simplecore.simplix"))
              .run(context ->
            assertThat(context).doesNotHaveBean(I18nMessagesDevRestController.class));
    }
}
