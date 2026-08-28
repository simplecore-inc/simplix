package dev.simplecore.simplix.springboot.autoconfigure;

import dev.simplecore.simplix.web.i18n.I18nMessagesDevRestController;
import dev.simplecore.simplix.web.i18n.I18nMessagesService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.List;

/**
 * Registers the i18n dev endpoint. Three gates, all must open: servlet web app,
 * {@code simplix.dev.i18n.enabled=true}, and no production profile active.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "simplix.dev.i18n.enabled", havingValue = "true")
@Conditional(SimpliXDevMetaAutoConfiguration.NotProductionCondition.class)
@EnableConfigurationProperties(SimpliXDevProperties.class)
public class SimpliXDevI18nAutoConfiguration {

    @Bean
    public I18nMessagesService i18nMessagesService(SimpliXDevProperties properties,
                                                     BeanFactory beanFactory) {
        List<String> packages = properties.getBasePackages().isEmpty()
                ? AutoConfigurationPackages.get(beanFactory)
                : properties.getBasePackages();
        return new I18nMessagesService(packages);
    }

    @Bean
    public I18nMessagesDevRestController i18nMessagesDevRestController(I18nMessagesService service) {
        return new I18nMessagesDevRestController(service);
    }
}
