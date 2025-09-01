package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.io.IOException;
import java.util.*;
import java.util.Arrays;

/**
 * SimpliX MessageSource Auto-configuration for comprehensive i18n support.
 * 
 * This configuration provides:
 * 1. Automatic discovery of library message files
 * 2. Integration with application-specific message files
 * 3. Proper validation message source configuration
 * 4. Locale resolution and change interceptor
 * 
 * Configuration in application.yml:
 * <pre>
 * spring:
 *   messages:
 *     basename: messages/validation,messages/errors,messages/messages
 *     encoding: UTF-8
 *     use-code-as-default-message: false
 *     fallback-to-system-locale: true
 * 
 * simplix:
 *   message-source:
 *     enabled: true
 *     default-locale: ko
 * </pre>
 */
@Slf4j
@AutoConfiguration(before = MessageSourceAutoConfiguration.class)
@Order(0)
@ConditionalOnProperty(prefix = "simplix.message-source", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXMessageSourceAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.messages")
    public MessageSourceProperties messageSourceProperties() {
        MessageSourceProperties properties = new MessageSourceProperties();
        // Set sensible defaults if not configured
        if (properties.getBasename() == null || properties.getBasename().isEmpty()) {
            properties.setBasename(Arrays.asList("messages/validation", "messages/errors", "messages/messages"));
        }
        properties.setUseCodeAsDefaultMessage(false);
        properties.setFallbackToSystemLocale(true);
        return properties;
    }

    /**
     * Primary MessageSource that combines library and application messages
     */
    @Bean
    @Primary
    public MessageSource messageSource(MessageSourceProperties properties) {
        log.info("Initializing SimpliX MessageSource with comprehensive i18n support");
        
        // Create composite message source
        CompositeMessageSource compositeMessageSource = new CompositeMessageSource();
        
        // 1. Add application message source (highest priority)
        ReloadableResourceBundleMessageSource applicationMessageSource = createApplicationMessageSource(properties);
        compositeMessageSource.addMessageSource(applicationMessageSource);
        
        // 2. Add library message source (fallback)
        ReloadableResourceBundleMessageSource libraryMessageSource = createLibraryMessageSource();
        compositeMessageSource.addMessageSource(libraryMessageSource);
        
        log.info("SimpliX MessageSource initialized successfully");
        return compositeMessageSource;
    }

    /**
     * Create application-specific message source based on Spring Boot properties
     */
    private ReloadableResourceBundleMessageSource createApplicationMessageSource(MessageSourceProperties properties) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        
        // Get basenames from properties (already a List<String>)
        List<String> basenames = properties.getBasename();
        
        // Convert to classpath format
        String[] classpathBasenames = basenames.stream()
            .map(basename -> basename.startsWith("classpath:") ? basename : "classpath:" + basename)
            .toArray(String[]::new);
        
        messageSource.setBasenames(classpathBasenames);
        messageSource.setDefaultEncoding(properties.getEncoding().name());
        messageSource.setFallbackToSystemLocale(false); // Disable fallback for exact locale matching
        messageSource.setUseCodeAsDefaultMessage(false); // Don't use code as default message
        
        if (properties.getCacheDuration() != null) {
            messageSource.setCacheSeconds((int) properties.getCacheDuration().getSeconds());
        }
        
        log.info("Application MessageSource configured with basenames: {}", Arrays.toString(classpathBasenames));
        return messageSource;
    }

    /**
     * Create library message source that automatically discovers SimpliX library messages
     */
    private ReloadableResourceBundleMessageSource createLibraryMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        
        Set<String> basenames = new HashSet<>();
        
        try {
            // Discover library message files
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:messages/**/*.properties");
            
            for (Resource resource : resources) {
                try {
                    String uri = resource.getURI().toString();
                    String filename = resource.getFilename();
                    
                    if (filename != null && uri.contains("/messages/")) {
                        // Extract basename (remove locale suffix and .properties extension)
                        String basename = filename.replaceAll("(_[a-z]{2}(_[A-Z]{2})?)?\\.properties$", "");
                        
                        // Extract path after messages directory
                        int messagesIndex = uri.indexOf("/messages/");
                        if (messagesIndex >= 0) {
                            String messagePath = uri.substring(messagesIndex + 1);
                            // Remove filename from path
                            String pathPrefix = messagePath.substring(0, messagePath.lastIndexOf('/') + 1);
                            String fullBasename = "classpath:" + pathPrefix + basename;
                            
                            // Only include library messages (simplix_core, etc.)
                            // Exclude application-specific messages (validation, errors, messages)
                            if ((basename.startsWith("simplix_") || uri.contains("spring-boot-starter-simplix")) && 
                                !basename.equals("validation") && !basename.equals("errors") && !basename.equals("messages")) {
                                basenames.add(fullBasename);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error processing resource: {}", resource, e);
                }
            }
            
        } catch (IOException e) {
            log.warn("Error scanning for library message files", e);
        }
        
        if (!basenames.isEmpty()) {
            messageSource.setBasenames(basenames.toArray(new String[0]));
            log.info("Library MessageSource configured with basenames: {}", basenames);
        } else {
            // Fallback to known library messages
            messageSource.setBasenames("classpath:messages/simplix_core");
            log.info("Library MessageSource using fallback basename: classpath:messages/simplix_core");
        }
        
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false); // Disable fallback for exact locale matching
        messageSource.setUseCodeAsDefaultMessage(false);
        messageSource.setCacheSeconds(3600); // Cache library messages longer
        
        return messageSource;
    }



    /**
     * Locale resolver with sensible defaults
     */
    @Bean
    @ConditionalOnMissingBean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH); // Default to English
        resolver.setCookieName("locale");
        resolver.setCookieMaxAge(3600 * 24 * 30); // 30 days
        log.info("LocaleResolver configured with default locale: {}", Locale.ENGLISH);
        return resolver;
    }

    /**
     * Locale change interceptor for URL-based locale switching
     */
    @Bean
    @ConditionalOnMissingBean
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
            .order(Integer.MIN_VALUE); // Highest priority
    }

    /**
     * Composite MessageSource that searches multiple message sources in order
     * with proper locale matching priority
     */
    public static class CompositeMessageSource implements MessageSource {
        private final List<MessageSource> messageSources = new ArrayList<>();
        
        public void addMessageSource(MessageSource messageSource) {
            if (messageSource != null) {
                messageSources.add(messageSource);
                log.debug("Added MessageSource: {}", messageSource.getClass().getSimpleName());
            }
        }
        
        @Override
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            log.debug("Searching for message '{}' with locale '{}'", code, locale);
            
            // Search through all message sources for the exact locale
            for (MessageSource source : messageSources) {
                try {
                    String message = source.getMessage(code, args, null, locale);
                    if (message != null && !message.equals(code)) {
                        log.trace("Found message for '{}' in {}: {}", code, source.getClass().getSimpleName(), message);
                        return message;
                    }
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            
            log.debug("No message found for code '{}' with locale '{}', using default: {}", code, locale, defaultMessage);
            return defaultMessage;
        }
        
        @Override
        public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            log.debug("Searching for message '{}' with locale '{}'", code, locale);
            
            // Search through all message sources for the exact locale
            for (MessageSource source : messageSources) {
                try {
                    String message = source.getMessage(code, args, locale);
                    if (message != null && !message.equals(code)) {
                        log.trace("Found message for '{}' in {}: {}", code, source.getClass().getSimpleName(), message);
                        return message;
                    }
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            
            log.debug("No message found for code '{}' with locale '{}'", code, locale);
            throw new NoSuchMessageException(code, locale);
        }
        
        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
            for (MessageSource source : messageSources) {
                try {
                    String message = source.getMessage(resolvable, locale);
                    if (message != null) {
                        log.trace("Found message for resolvable in {}: {}", source.getClass().getSimpleName(), message);
                        return message;
                    }
                } catch (NoSuchMessageException ignored) {
                    // Continue to next message source
                }
            }
            
            log.debug("No message found for resolvable with locale '{}'", locale);
            throw new NoSuchMessageException("No message found for resolvable", locale);
        }

    }
}