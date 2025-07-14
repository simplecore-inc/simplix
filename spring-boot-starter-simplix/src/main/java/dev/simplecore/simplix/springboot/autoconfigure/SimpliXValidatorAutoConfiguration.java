package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * SimpliX Validator Auto-configuration.
 * 
 * Provides a simple validator configuration that integrates with MessageSource.
 */
@Slf4j
@AutoConfiguration(after = SimpliXMessageSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "simplix.validator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXValidatorAutoConfiguration {

    /**
     * Provides a fallback Spring Validator if no other validator is present.
     */
    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public Validator springValidator(MessageSource messageSource) {
        log.info("Configuring SimpliX Validator with MessageSource");
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }
} 