package dev.simplecore.simplix.auth.oauth2.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.Map;

/**
 * BeanPostProcessor that filters OAuth2 client registrations with empty client IDs.
 * <p>
 * This processor intercepts the {@link OAuth2ClientProperties} bean BEFORE its
 * {@code afterPropertiesSet()} method is called (where validation occurs).
 * It removes registrations that have empty or missing client IDs, preventing
 * the validation error.
 * <p>
 * This allows applications to define all possible OAuth2 providers in configuration
 * while only activating those that are properly configured.
 * <p>
 * For each skipped provider, a warning is logged:
 * <pre>{@code
 * WARN  - OAuth2 provider 'github' has no client-id configured. This provider will be disabled.
 * }</pre>
 *
 * @since 1.0.18
 */
public class OAuth2ClientFilteringBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ClientFilteringBeanPostProcessor.class);

    @Override
    public int getOrder() {
        // Run AFTER ConfigurationPropertiesBindingPostProcessor (which has HIGHEST_PRECEDENCE + 1)
        // but still in postProcessBeforeInitialization, before afterPropertiesSet()
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof OAuth2ClientProperties properties) {
            filterEmptyRegistrations(properties);
        }
        return bean;
    }

    private void filterEmptyRegistrations(OAuth2ClientProperties properties) {
        Map<String, OAuth2ClientProperties.Registration> registrations = properties.getRegistration();

        if (registrations == null || registrations.isEmpty()) {
            return;
        }

        // Find registrations to remove (don't modify during iteration)
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, OAuth2ClientProperties.Registration> entry : registrations.entrySet()) {
            String registrationId = entry.getKey();
            OAuth2ClientProperties.Registration registration = entry.getValue();

            if (!StringUtils.hasText(registration.getClientId())) {
                log.warn("OAuth2 provider '{}' has no client-id configured. " +
                        "This provider will be disabled.", registrationId);
                toRemove.add(registrationId);
            }
        }

        // Remove empty registrations
        for (String registrationId : toRemove) {
            registrations.remove(registrationId);
        }
    }
}
