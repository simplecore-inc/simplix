package dev.simplecore.simplix.core.i18n;

import java.util.Locale;

/**
 * Utility class for resolving entity messages from ResourceBundle
 * <p>
 * This class provides functionality to resolve localized labels and descriptions
 * for entity classes, similar to EnumMessageResolver but for entities.
 * <p>
 * Messages are automatically discovered from the classpath based on the pattern:
 * classpath*:messages/entities/**{@literal /}*-messages.properties
 */
public class EntityMessageResolver extends AbstractMessageResolver {

    private static final EntityMessageResolver INSTANCE = new EntityMessageResolver();

    /**
     * Private constructor for singleton pattern
     */
    private EntityMessageResolver() {
        super();
    }

    @Override
    protected String getMessagePattern() {
        return "classpath*:messages/entities/**/*-messages.properties";
    }

    @Override
    protected String getResolverName() {
        return "entity";
    }

    /**
     * Set the current locale for message resolution
     * @param locale The locale to use
     */
    public static void setLocale(Locale locale) {
        INSTANCE.internalSetLocale(locale);
    }

    /**
     * Get the current locale
     * @return Current locale
     */
    public static Locale getLocale() {
        return INSTANCE.internalGetLocale();
    }

    /**
     * Resolve message for the given key
     * @param key Message key
     * @return Resolved message or key if not found
     */
    public static String resolveMessage(String key) {
        return INSTANCE.internalResolveMessage(key);
    }

    /**
     * Resolve message for the given key and locale
     * @param key Message key
     * @param locale Locale to use
     * @return Resolved message or key if not found
     */
    public static String resolveMessage(String key, Locale locale) {
        return INSTANCE.internalResolveMessage(key, locale);
    }

    /**
     * Resolve message with parameters
     * @param key Message key
     * @param params Parameters for message formatting
     * @return Resolved and formatted message
     */
    public static String resolveMessage(String key, Object... params) {
        return INSTANCE.internalResolveMessage(key, params);
    }

    /**
     * Resolve message with parameters and locale
     * @param key Message key
     * @param locale Locale to use
     * @param params Parameters for message formatting
     * @return Resolved and formatted message
     */
    public static String resolveMessage(String key, Locale locale, Object... params) {
        return INSTANCE.internalResolveMessage(key, locale, params);
    }

    /**
     * Clear the thread local locale
     */
    public static void clearLocale() {
        INSTANCE.internalClearLocale();
    }

    /**
     * Get all discovered bundle names (for debugging/testing)
     * @return Array of bundle names
     */
    public static String[] getDiscoveredBundles() {
        return INSTANCE.internalGetDiscoveredBundles();
    }
}
