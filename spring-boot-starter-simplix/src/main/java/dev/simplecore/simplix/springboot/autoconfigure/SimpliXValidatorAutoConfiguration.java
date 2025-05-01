package dev.simplecore.simplix.springboot.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Auto-configuration for Bean Validation with i18n support.
 * 
 * <p>Configure in application.yml:
 * <pre>
 * simplix:
 *   validator:
 *     enabled: true
 *     provider-class-name: org.hibernate.validator.HibernateValidator
 * </pre>
 */
@Configuration
@AutoConfiguration(before = ValidationAutoConfiguration.class)
@Order(0)
@ConditionalOnProperty(prefix = "simplix.validator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXValidatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
} 