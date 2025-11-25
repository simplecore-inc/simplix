package dev.simplecore.simplix.springboot.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.*;

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
 *     basename:
 *       - messages/validation
 *       - messages/errors
 *       - messages/messages
 *     encoding: UTF-8
 *     use-code-as-default-message: false
 *
 * simplix:
 *   message-source:
 *     enabled: true
 * </pre>
 */
@Slf4j
@AutoConfiguration(before = {MessageSourceAutoConfiguration.class, org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class})
@Order(0)
@ConditionalOnProperty(prefix = "simplix.message-source", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXMessageSourceAutoConfiguration implements WebMvcConfigurer {

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.messages")
    public MessageSourceProperties messageSourceProperties(Environment environment) {
        MessageSourceProperties properties = new MessageSourceProperties();

        // Explicitly read basename from environment with sensible defaults
        String basenameProperty = environment.getProperty("spring.messages.basename");
        if (basenameProperty != null && !basenameProperty.isEmpty()) {
            // Use configured basenames (comma-separated)
            properties.setBasename(Arrays.asList(basenameProperty.split(",")));
        } else {
            // Set default basenames if not configured
            // Include "messages" for Spring Boot compatibility, plus validation/error message locations
            properties.setBasename(Arrays.asList("messages", "messages/validation", "messages/errors", "messages/messages"));
        }

        properties.setUseCodeAsDefaultMessage(false);
        properties.setFallbackToSystemLocale(true);
        return properties;
    }

    /**
     * Primary MessageSource that combines library and application messages using
     * Spring's HierarchicalMessageSource pattern.
     *
     * Priority chain:
     * 1. Application messages (highest priority)
     * 2. SimpliX library messages (fallback via parent)
     */
    @Bean
    @Primary
    public MessageSource messageSource(MessageSourceProperties properties) {
        log.info("Initializing SimpliX MessageSource with HierarchicalMessageSource pattern");

        // 1. Create library message source (will be set as parent)
        ReloadableResourceBundleMessageSource libraryMessageSource = createLibraryMessageSource();

        // 2. Create application message source with library as parent
        ReloadableResourceBundleMessageSource applicationMessageSource = createApplicationMessageSource(properties);
        applicationMessageSource.setParentMessageSource(libraryMessageSource);

        log.info("SimpliX MessageSource initialized successfully");
        return applicationMessageSource;
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
     * Create library message source that automatically discovers SimpliX library messages.
     * Discovers all message files matching the pattern: classpath*:messages/simplix_*.properties
     */
    private ReloadableResourceBundleMessageSource createLibraryMessageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();

        Set<String> basenames = discoverLibraryMessageBasenames();

        if (!basenames.isEmpty()) {
            messageSource.setBasenames(basenames.toArray(new String[0]));
            log.info("Library MessageSource configured with basenames: {}", basenames);
        } else {
            messageSource.setBasenames("classpath:messages/simplix_core");
            log.info("Library MessageSource using fallback basename: classpath:messages/simplix_core");
        }

        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(false);
        messageSource.setCacheSeconds(3600);

        return messageSource;
    }

    /**
     * Discover all SimpliX library message basenames from classpath.
     * Scans for files matching: classpath*:messages/simplix_*.properties
     */
    private Set<String> discoverLibraryMessageBasenames() {
        Set<String> basenames = new LinkedHashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources("classpath*:messages/simplix_*.properties");

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.startsWith("simplix_")) {
                    // Remove locale suffix: simplix_core_ko.properties -> simplix_core
                    String basename = filename.replaceAll("(_[a-z]{2}(_[A-Z]{2})?)?\\.properties$", "");
                    basenames.add("classpath:messages/" + basename);
                }
            }
        } catch (IOException e) {
            log.debug("Error scanning for SimpliX message files: {}", e.getMessage());
        }

        return basenames;
    }
}