package dev.simplecore.simplix.springboot.autoconfigure;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.util.Locale;

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
@AutoConfiguration(
        after = SimpliXMessageSourceAutoConfiguration.class,
        before = ValidationAutoConfiguration.class
)
@ConditionalOnProperty(prefix = "simplix.validator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXValidatorAutoConfiguration {

    /**
     * Provides a comprehensive Spring Validator with MessageSource integration.
     * Uses a custom MessageInterpolator that prioritizes Spring MessageSource
     * over Hibernate Validator's default ResourceBundle lookup.
     */
    @Bean
    @Primary
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        log.info("Configuring SimpliX Validator with MessageSource-first MessageInterpolator");

        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();

        // Use custom MessageInterpolator that prioritizes Spring MessageSource
        factory.setMessageInterpolator(new MessageSourceFirstInterpolator(messageSource));

        // Configure Hibernate Validator properties for better parameter handling
        factory.getValidationPropertyMap().put("hibernate.validator.fail_fast", "false");
        factory.getValidationPropertyMap().put("hibernate.validator.allow_parameter_name_provider", "true");
        factory.getValidationPropertyMap().put("hibernate.validator.allow_multiple_cascaded_validation", "true");

        log.debug("Configured LocalValidatorFactoryBean with MessageSource-first interpolator");
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

    /**
     * Custom MessageInterpolator that prioritizes Spring MessageSource over
     * Hibernate Validator's default ResourceBundle lookup.
     *
     * Lookup order:
     * 1. Spring MessageSource (simplix_validation, application messages)
     * 2. Hibernate Validator default (ValidationMessages.properties, built-in)
     */
    static class MessageSourceFirstInterpolator implements MessageInterpolator {

        private final MessageSource messageSource;
        private final MessageInterpolator delegate;

        MessageSourceFirstInterpolator(MessageSource messageSource) {
            this.messageSource = messageSource;
            // Get default MessageInterpolator from the validation provider
            this.delegate = Validation.byDefaultProvider()
                    .configure()
                    .getDefaultMessageInterpolator();
        }

        @Override
        public String interpolate(String messageTemplate, Context context) {
            return interpolate(messageTemplate, context, LocaleContextHolder.getLocale());
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            // Extract message key from template: {jakarta.validation.constraints.NotNull.message}
            String messageKey = extractMessageKey(messageTemplate);
            log.debug("Interpolating message template: {}, key: {}, locale: {}", messageTemplate, messageKey, locale);

            if (messageKey != null) {
                try {
                    // Try Spring MessageSource first
                    String message = messageSource.getMessage(messageKey, null, locale);
                    log.debug("MessageSource returned: {} for key: {}", message, messageKey);
                    if (message != null && !message.equals(messageKey)) {
                        // Interpolate parameters like {min}, {max}, {value}
                        String result = interpolateParameters(message, context);
                        log.debug("Final interpolated message: {}", result);
                        return result;
                    }
                } catch (NoSuchMessageException e) {
                    log.debug("Message not found in MessageSource for key: {}, falling back to delegate", messageKey);
                }
            }

            // Fallback to Hibernate Validator default interpolation
            String delegateResult = delegate.interpolate(messageTemplate, context, locale);
            log.debug("Delegate interpolator returned: {}", delegateResult);
            return delegateResult;
        }

        private String extractMessageKey(String messageTemplate) {
            if (messageTemplate != null && messageTemplate.startsWith("{") && messageTemplate.endsWith("}")) {
                return messageTemplate.substring(1, messageTemplate.length() - 1);
            }
            return null;
        }

        private String interpolateParameters(String message, Context context) {
            String result = message;
            for (var entry : context.getConstraintDescriptor().getAttributes().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
            return result;
        }
    }
} 