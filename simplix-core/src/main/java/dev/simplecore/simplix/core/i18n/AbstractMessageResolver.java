package dev.simplecore.simplix.core.i18n;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.*;

/**
 * Abstract base class for message resolvers
 * <p>
 * Provides common functionality for resolving localized messages from ResourceBundle.
 * Subclasses must provide the message pattern for discovering bundles.
 */
@Slf4j
public abstract class AbstractMessageResolver {

    private final String[] bundles;

    // System-wide default locale (initialized by Spring)
    private static Locale systemDefaultLocale = Locale.ENGLISH;

    private final ThreadLocal<Locale> currentLocale = ThreadLocal.withInitial(() -> systemDefaultLocale);

    /**
     * Constructor that discovers message bundles using the provided pattern
     */
    protected AbstractMessageResolver() {
        this.bundles = discoverBundles(getMessagePattern());
    }

    /**
     * Get the message pattern for discovering bundles
     *
     * @return Message pattern (e.g., "classpath*:messages/entities/**<!---->/*-messages.properties")
     */
    protected abstract String getMessagePattern();

    /**
     * Get the name of this resolver for logging purposes
     *
     * @return Resolver name
     */
    protected abstract String getResolverName();

    /**
     * Set system default locale (called during Spring initialization)
     * @param locale Default locale from configuration
     */
    public static void setSystemDefaultLocale(Locale locale) {
        systemDefaultLocale = locale != null ? locale : Locale.ENGLISH;
    }

    /**
     * Discover message bundles from classpath
     *
     * @param pattern Resource pattern to search for
     * @return Array of bundle names
     */
    private String[] discoverBundles(String pattern) {
        Set<String> bundleNames = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource[] resources = resolver.getResources(pattern);

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && !filename.contains("_")) {
                    // Extract bundle name from file path
                    String uri = resource.getURI().toString();
                    // Extract the part after "messages/"
                    int messagesIdx = uri.indexOf("messages/");
                    if (messagesIdx != -1) {
                        String bundlePath = uri.substring(messagesIdx);
                        // Remove ".properties" extension and convert to bundle format
                        bundlePath = bundlePath.replace(".properties", "");
                        bundlePath = bundlePath.replace("/", ".");
                        bundleNames.add(bundlePath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to discover {} message bundles", getResolverName(), e);
        }

        return bundleNames.toArray(new String[0]);
    }

    /**
     * Set the current locale for message resolution
     *
     * @param locale The locale to use
     */
    protected void internalSetLocale(Locale locale) {
        currentLocale.set(locale != null ? locale : systemDefaultLocale);
    }

    /**
     * Get the current locale
     *
     * @return Current locale
     */
    protected Locale internalGetLocale() {
        return currentLocale.get();
    }

    /**
     * Resolve message for the given key
     *
     * @param key Message key
     * @return Resolved message or key if not found
     */
    protected String internalResolveMessage(String key) {
        return internalResolveMessage(key, currentLocale.get());
    }

    /**
     * Resolve message for the given key and locale
     *
     * @param key Message key
     * @param locale Locale to use
     * @return Resolved message or key if not found
     */
    protected String internalResolveMessage(String key, Locale locale) {
        // Use NO_FALLBACK control to prevent falling back to default locale
        ResourceBundle.Control control = ResourceBundle.Control.getNoFallbackControl(
            ResourceBundle.Control.FORMAT_PROPERTIES);
        Locale targetLocale = locale != null ? locale : systemDefaultLocale;

        // For English, try to load the base bundle (without suffix)
        if (targetLocale.getLanguage().equals("en")) {
            targetLocale = Locale.ROOT;
        }

        // Try to find message in all bundles
        for (String bundleName : bundles) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(bundleName, targetLocale,
                    getClass().getClassLoader(), control);
                String message = bundle.getString(key);
                if (!message.isEmpty()) {
                    return message;
                }
            } catch (MissingResourceException e) {
                // Continue to next bundle
            }
        }

        // Try with ROOT locale if specific locale failed
        if (!targetLocale.equals(Locale.ROOT)) {
            for (String bundleName : bundles) {
                try {
                    ResourceBundle bundle = ResourceBundle.getBundle(bundleName, Locale.ROOT,
                        getClass().getClassLoader(), control);
                    String message = bundle.getString(key);
                    if (!message.isEmpty()) {
                        return message;
                    }
                } catch (MissingResourceException e) {
                    // Continue to next bundle
                }
            }
        }

        // Return the key itself if message not found
        return key;
    }

    /**
     * Resolve message with parameters
     *
     * @param key Message key
     * @param params Parameters for message formatting
     * @return Resolved and formatted message
     */
    protected String internalResolveMessage(String key, Object... params) {
        return internalResolveMessage(key, currentLocale.get(), params);
    }

    /**
     * Resolve message with parameters and locale
     *
     * @param key Message key
     * @param locale Locale to use
     * @param params Parameters for message formatting
     * @return Resolved and formatted message
     */
    protected String internalResolveMessage(String key, Locale locale, Object... params) {
        String message = internalResolveMessage(key, locale);
        if (params != null && params.length > 0 && !message.equals(key)) {
            try {
                return String.format(message, params);
            } catch (Exception e) {
                return message;
            }
        }
        return message;
    }

    /**
     * Clear the thread local locale
     */
    protected void internalClearLocale() {
        currentLocale.remove();
    }

    /**
     * Get all discovered bundle names (for debugging/testing)
     *
     * @return Array of bundle names
     */
    protected String[] internalGetDiscoveredBundles() {
        return bundles.clone();
    }
}
