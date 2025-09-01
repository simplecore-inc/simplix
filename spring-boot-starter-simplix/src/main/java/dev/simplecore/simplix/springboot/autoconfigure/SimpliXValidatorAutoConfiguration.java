package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * SimpliX Validator Auto-configuration.
 * 
 * Provides comprehensive validator configuration that integrates with MessageSource
 * and ensures proper parameter substitution in validation messages.
 * 
 * Features:
 * - MessageSource integration for internationalized validation messages
 * - Proper parameter substitution for {min}, {max}, {value} etc.
 * - Method validation support
 * - Hibernate Validator configuration
 */
@Slf4j
@AutoConfiguration(after = SimpliXMessageSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "simplix.validator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXValidatorAutoConfiguration {

    /**
     * Provides a comprehensive Spring Validator with MessageSource integration.
     * This validator properly handles parameter substitution in validation messages.
     */
    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        log.info("Configuring SimpliX Validator with MessageSource integration");
        
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        
        // Set the MessageSource for internationalized validation messages
        factory.setValidationMessageSource(messageSource);
        
        // Configure Hibernate Validator properties for better parameter handling
        factory.getValidationPropertyMap().put("hibernate.validator.fail_fast", "false");
        factory.getValidationPropertyMap().put("hibernate.validator.allow_parameter_name_provider", "true");
        factory.getValidationPropertyMap().put("hibernate.validator.allow_multiple_cascaded_validation", "true");
        
        log.debug("Configured LocalValidatorFactoryBean with MessageSource and Hibernate Validator properties");
        return factory;
    }

    /**
     * Provides method validation support for @Validated annotations on service methods.
     */
    @Bean
    @ConditionalOnMissingBean(MethodValidationPostProcessor.class)
    @ConditionalOnClass(name = "org.springframework.validation.beanvalidation.LocalValidatorFactoryBean")
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        log.info("Configuring Method Validation Post Processor");
        
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        
        log.debug("Configured MethodValidationPostProcessor");
        return processor;
    }
} 