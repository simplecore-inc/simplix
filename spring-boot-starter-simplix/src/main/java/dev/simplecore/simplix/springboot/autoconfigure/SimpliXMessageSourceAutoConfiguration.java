package dev.simplecore.simplix.springboot.autoconfigure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auto-configuration for MessageSource with i18n support.
 * Uses Spring Boot's default message source properties.
 * 
 * <p>Configure in application.yml:
 * <pre>
 * spring:
 *   messages:
 *     # Comma-separated list of basenames
 *     basename: messages/common,messages/validation,messages/errors
 *     # Or using YAML list syntax
 *     basename: |
 *       messages/common,
 *       messages/validation,
 *       messages/errors
 *     encoding: UTF-8
 *     cache-duration: 3600
 *     use-code-as-default-message: false
 *     fallback-to-system-locale: true
 * </pre>
 * 
 * <p>Message properties file example (messages/validation_en.properties):
 * <pre>
 * user.name.empty=Name is required
 * user.email.invalid=Invalid email format
 * </pre>
 * 
 * <p>Usage in code:
 * <pre>{@code
 * @Autowired
 * private MessageSource messageSource;
 * 
 * String message = messageSource.getMessage("user.name.empty", null, LocaleContextHolder.getLocale());
 * }</pre>
 */
@Configuration
@AutoConfiguration(before = MessageSourceAutoConfiguration.class)
@Order(0)
@ConditionalOnProperty(prefix = "simplix.message-source", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXMessageSourceAutoConfiguration implements WebMvcConfigurer {
    
    private static final Logger LOGGER = Logger.getLogger(SimpliXMessageSourceAutoConfiguration.class.getName());

    @Bean
    @ConfigurationProperties(prefix = "spring.messages")
    public MessageSourceProperties messageSourceProperties() {
        return new MessageSourceProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setDefaultLocale(null);
        resolver.setCookieName("locale");
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor())
            .addPathPatterns("/**")
            .order(Ordered.HIGHEST_PRECEDENCE);
    }

    /**
     * Provides a message source that automatically discovers and integrates all message bundles.
     * This message source scans all message files in the messages directory across all classpaths
     * and integrates them without depending on specific patterns or paths.
     */
    @Bean
    public MessageSource simplixLibraryMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        
        try {
            // Search for all property files in the messages directory across all classpaths
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:messages/**/*.properties");
            
            // Extract basenames from discovered resources
            Set<String> basenames = new HashSet<>();
            for (Resource resource : resources) {
                try {
                    String path = resource.getURI().toString();
                    String filename = resource.getFilename();
                    
                    if (filename != null) {
                        // Remove locale part and .properties extension from filename
                        String basename = filename.replaceAll("(_[a-z]{2}(_[A-Z]{2})?)?\\.properties$", "");
                        
                        // Extract path after messages directory
                        int messagesIndex = path.indexOf("/messages/");
                        if (messagesIndex >= 0) {
                            String messagePath = path.substring(messagesIndex + 1);
                            // Remove filename and extension
                            messagePath = messagePath.substring(0, messagePath.lastIndexOf('/') + 1) + basename;
                            // Remove locale part
                            messagePath = messagePath.replaceAll("/[^/]*(_[a-z]{2}(_[A-Z]{2})?)\\.properties$", "");
                            
                            // Use classpath: instead of classpath*:
                            basenames.add("classpath:" + messagePath);
                        }
                    }
                } catch (Exception e) {
                    // Skip individual resources that cause errors
                    LOGGER.log(Level.WARNING, "Error processing resource: " + resource, e);
                }
            }
            
            if (!basenames.isEmpty()) {
                messageSource.setBasenames(basenames.toArray(new String[0]));
                LOGGER.info("Registered message basenames: " + basenames);
            } else {
                // Set default basename if no resources are found
                messageSource.setBasenames("classpath:messages/default");
                LOGGER.info("No message resources found, using default basename");
            }
        } catch (IOException e) {
            // Set default basename on exception
            messageSource.setBasenames("classpath:messages/default");
            LOGGER.log(Level.SEVERE, "Error scanning for message properties", e);
        }
        
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setCacheSeconds(60); // Short cache duration for development
        messageSource.setUseCodeAsDefaultMessage(true); // Use code as default message if not found
        return messageSource;
    }

    @Bean
    @Primary
    public MessageSource messageSource(
        MessageSourceProperties properties,
        @Autowired(required = false) List<MessageSource> messageSources,
        @Autowired MessageSource simplixLibraryMessageSource
    ) {
        // 1. Create default message source using Spring Boot settings
        ResourceBundleMessageSource defaultMessageSource = new ResourceBundleMessageSource();
        defaultMessageSource.setBasenames(StringUtils.commaDelimitedListToStringArray(
                StringUtils.trimAllWhitespace(properties.getBasename())));
        defaultMessageSource.setDefaultEncoding(properties.getEncoding().name());
        defaultMessageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
        defaultMessageSource.setCacheSeconds(properties.getCacheDuration() != null ? 
            (int) properties.getCacheDuration().getSeconds() : -1);
        defaultMessageSource.setAlwaysUseMessageFormat(properties.isAlwaysUseMessageFormat());
        defaultMessageSource.setUseCodeAsDefaultMessage(properties.isUseCodeAsDefaultMessage());
        
        // 2. Create composite message source
        CompositeMessageSource compositeMessageSource = new CompositeMessageSource();
        
        // 3. Add default message source (user-defined messages have priority)
        compositeMessageSource.addMessageSource(defaultMessageSource);
        
        // 4. Add library message source
        compositeMessageSource.addMessageSource(simplixLibraryMessageSource);
        
        // 5. Add other injected message sources
        if (messageSources != null) {
            for (MessageSource source : messageSources) {
                if (source != defaultMessageSource && source != simplixLibraryMessageSource) {
                    compositeMessageSource.addMessageSource(source);
                }
            }
        }
        
        return compositeMessageSource;
    }

    /**
     * Composite message source that integrates multiple message sources.
     * Searches for messages in the registered message sources in order.
     */
    private static class CompositeMessageSource implements MessageSource {
        private final List<MessageSource> messageSources = new ArrayList<>();
        
        public void addMessageSource(MessageSource messageSource) {
            if (messageSource != null) {
                messageSources.add(messageSource);
            }
        }
        
        @Override
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            for (MessageSource source : messageSources) {
                try {
                    String message = source.getMessage(code, args, null, locale);
                    if (message != null) {
                        return message;
                    }
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            return defaultMessage;
        }
        
        @Override
        public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            for (MessageSource source : messageSources) {
                try {
                    return source.getMessage(code, args, locale);
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            throw new NoSuchMessageException(code, locale);
        }
        
        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
            for (MessageSource source : messageSources) {
                try {
                    return source.getMessage(resolvable, locale);
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            throw new NoSuchMessageException("No message found for resolvable", locale);
        }
    }
}