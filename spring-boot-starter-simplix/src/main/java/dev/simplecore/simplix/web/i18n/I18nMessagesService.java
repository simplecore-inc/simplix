package dev.simplecore.simplix.web.i18n;

import dev.simplecore.simplix.core.enums.LabeledEnum;
import dev.simplecore.simplix.core.i18n.EntityMessageResolver;
import dev.simplecore.simplix.core.i18n.EnumMessageResolver;
import jakarta.persistence.Entity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for collecting all i18n messages for entities and enums
 *
 * This service:
 * 1. Auto-detects supported locales from properties files
 * 2. Scans all Entity classes using classpath scanning
 * 3. Scans all LabeledEnum implementations
 * 4. Resolves messages for all locales dynamically
 * 5. Builds complete I18nMessagesResponse
 */
@Slf4j
public class I18nMessagesService {

    private static final String ENTITY_MESSAGES_PATTERN = "classpath*:messages/entities/**/*-messages*.properties";
    private static final String ENUM_MESSAGES_PATTERN = "classpath*:messages/enums/**/*-messages*.properties";
    private static final String ENTITY_KEY_PREFIX = "entities.";
    private static final String COMMON_LABEL_NAMESPACE = "common";
    private static final String API_VERSION = "1.0.0";

    /** Base packages scanned for {@code @Entity} classes and {@link LabeledEnum} implementations. */
    private final List<String> basePackages;

    public I18nMessagesService(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    /**
     * Get complete i18n messages (entities + enums)
     *
     * @return Complete I18nMessagesResponse
     */
    public I18nMessagesResponse getAllMessages() {
        List<String> supportedLocales = detectSupportedLocales();

        I18nMessagesResponse response = new I18nMessagesResponse();
        response.setVersion(API_VERSION);
        response.setSupportedLocales(supportedLocales);

        // Collect entity messages
        Map<String, EntityMessages> entityMessages = collectEntityMessages(supportedLocales);
        response.setEntities(entityMessages);

        // Collect enum messages
        Map<String, EnumMessages> enumMessages = collectEnumMessages(supportedLocales);
        response.setEnums(enumMessages);

        return response;
    }

    /**
     * Get entity messages only
     *
     * @return Map of entity name to EntityMessages
     */
    public Map<String, EntityMessages> getEntityMessages() {
        List<String> supportedLocales = detectSupportedLocales();
        return collectEntityMessages(supportedLocales);
    }

    /**
     * Get enum messages only
     *
     * @return Map of enum name to EnumMessages
     */
    public Map<String, EnumMessages> getEnumMessages() {
        List<String> supportedLocales = detectSupportedLocales();
        return collectEnumMessages(supportedLocales);
    }

    /**
     * Get supported locales only
     *
     * @return List of supported locale codes
     */
    public List<String> getSupportedLocales() {
        return detectSupportedLocales();
    }

    /**
     * Auto-detect supported locales from properties files
     *
     * Scans both entity and enum message files and extracts locale suffixes:
     * - *-messages.properties → "en" (default/root)
     * - *-messages_ko.properties → "ko"
     * - *-messages_ja.properties → "ja"
     *
     * @return Sorted list of unique locale codes
     */
    private List<String> detectSupportedLocales() {
        Set<String> locales = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            // Scan entity message files
            Resource[] entityResources = resolver.getResources(ENTITY_MESSAGES_PATTERN);
            extractLocalesFromResources(entityResources, locales);

            // Scan enum message files
            Resource[] enumResources = resolver.getResources(ENUM_MESSAGES_PATTERN);
            extractLocalesFromResources(enumResources, locales);

        } catch (Exception e) {
            log.warn("Failed to detect supported locales, using default [en]", e);
            locales.add("en");
        }

        // Always ensure "en" is included as default
        if (!locales.contains("en")) {
            locales.add("en");
        }

        return new ArrayList<>(locales);
    }

    /**
     * Extract locale codes from resource filenames
     *
     * @param resources Array of property file resources
     * @param locales Set to add detected locales to
     */
    private void extractLocalesFromResources(Resource[] resources, Set<String> locales) {
        for (Resource resource : resources) {
            try {
                String filename = resource.getFilename();
                if (filename != null) {
                    if (filename.endsWith(".properties") && !filename.contains("_")) {
                        // Base file without locale suffix → "en"
                        locales.add("en");
                    } else if (filename.contains("_")) {
                        // Extract locale from suffix (e.g., "_ko", "_ja")
                        String[] parts = filename.split("_");
                        if (parts.length >= 2) {
                            String localePart = parts[parts.length - 1].replace(".properties", "");
                            if (localePart.matches("[a-z]{2}")) {
                                locales.add(localePart);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // intentionally ignored — invalid filename format
            }
        }
    }

    /**
     * Collect entity messages for all entities
     *
     * @param supportedLocales List of locales to resolve messages for
     * @return Map of entity name to EntityMessages
     */
    private Map<String, EntityMessages> collectEntityMessages(List<String> supportedLocales) {
        Map<String, EntityMessages> entityMessagesMap = new LinkedHashMap<>();

        try {
            Set<Class<?>> entityClasses = scanEntityClasses();

            for (Class<?> entityClass : entityClasses) {
                try {
                    EntityMessages messages = buildEntityMessages(entityClass, supportedLocales);
                    entityMessagesMap.put(entityClass.getSimpleName(), messages);
                } catch (Exception e) {
                    log.warn("Failed to build messages for entity: {}", entityClass.getSimpleName(), e);
                }
            }

            mergeDeclaredLabels(entityMessagesMap, supportedLocales);
        } catch (Exception e) {
            log.error("Failed to collect entity messages", e);
        }

        return entityMessagesMap;
    }

    /**
     * Merges every {@code entities.<Name>[.<field>]} key declared in the label bundles
     * into the reflected entity messages.
     *
     * <p>Reflection alone only reaches persisted entity fields, so labels for derived
     * DTO fields (a resolved FK name, a computed count) and for projection surfaces that
     * have no entity at all (check-in results, reports, import summaries) would never be
     * emitted — the client then falls back to a prettified English field name. The bundle
     * is therefore the source of truth: declaring a key is enough to label a field.
     *
     * @param entityMessagesMap The reflected messages, mutated in place
     * @param supportedLocales The locales to resolve each declared key for
     */
    private void mergeDeclaredLabels(Map<String, EntityMessages> entityMessagesMap, List<String> supportedLocales) {
        for (String key : declaredEntityKeys()) {
            String path = key.substring(ENTITY_KEY_PREFIX.length());
            int dot = path.indexOf('.');
            String entityName = dot < 0 ? path : path.substring(0, dot);
            String fieldName = dot < 0 ? null : path.substring(dot + 1);
            if (entityName.isEmpty() || COMMON_LABEL_NAMESPACE.equals(entityName)
                    || (fieldName != null && fieldName.indexOf('.') >= 0)) {
                continue;
            }

            EntityMessages messages = entityMessagesMap.get(entityName);
            if (messages == null) {
                messages = new EntityMessages();
                messages.setLabel(resolveLabel(ENTITY_KEY_PREFIX + entityName, supportedLocales));
                entityMessagesMap.put(entityName, messages);
            }
            if (fieldName == null || hasTranslation(messages.getFields().get(fieldName))) {
                continue;
            }
            messages.addField(fieldName, resolveLabel(key, supportedLocales));
        }
    }

    /**
     * Whether a reflected field already carries at least one resolved translation. Reflection
     * emits an empty placeholder for every entity field, including the ones no bundle labels.
     */
    private boolean hasTranslation(LocalizedString localized) {
        return localized != null && localized.getTranslations() != null && !localized.getTranslations().isEmpty();
    }

    /**
     * Resolves one label key across the supported locales, omitting the locales where the
     * key is unresolved (the resolver echoes the key back).
     */
    private LocalizedString resolveLabel(String key, List<String> supportedLocales) {
        LocalizedString localized = new LocalizedString();
        for (String locale : supportedLocales) {
            String label = EntityMessageResolver.resolveMessage(key, new Locale(locale));
            if (!key.equals(label)) {
                localized.addTranslation(locale, label);
            }
        }
        return localized;
    }

    /**
     * The union of {@code entities.*} keys declared across every label bundle on the classpath.
     */
    private Set<String> declaredEntityKeys() {
        Set<String> keys = new TreeSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            for (Resource resource : resolver.getResources(ENTITY_MESSAGES_PATTERN)) {
                Properties properties = new Properties();
                try (InputStream stream = resource.getInputStream()) {
                    properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
                for (String name : properties.stringPropertyNames()) {
                    if (name.startsWith(ENTITY_KEY_PREFIX)) {
                        keys.add(name);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read declared entity label keys", e);
        }
        return keys;
    }

    /**
     * Collect enum messages for all enums
     *
     * @param supportedLocales List of locales to resolve messages for
     * @return Map of enum name to EnumMessages
     */
    private Map<String, EnumMessages> collectEnumMessages(List<String> supportedLocales) {
        Map<String, EnumMessages> enumMessagesMap = new LinkedHashMap<>();

        try {
            Set<Class<? extends Enum<?>>> enumClasses = scanEnumClasses();

            for (Class<? extends Enum<?>> enumClass : enumClasses) {
                try {
                    EnumMessages messages = buildEnumMessages(enumClass, supportedLocales);
                    enumMessagesMap.put(enumClass.getSimpleName(), messages);
                } catch (Exception e) {
                    log.warn("Failed to build messages for enum: {}", enumClass.getSimpleName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to collect enum messages", e);
        }

        return enumMessagesMap;
    }

    /**
     * Scan all Entity classes from domain package
     *
     * @return Set of Entity classes sorted by simple name
     */
    private Set<Class<?>> scanEntityClasses() {
        Set<Class<?>> entityClasses = new TreeSet<>(Comparator.comparing(Class::getSimpleName));

        for (String basePackage : basePackages) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                String pattern = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";
                Resource[] resources = resolver.getResources(pattern);

                for (Resource resource : resources) {
                    try {
                        String className = extractClassName(resource, basePackage);
                        Class<?> clazz = Class.forName(className);

                        // Filter: only classes with @Entity annotation
                        if (clazz.isAnnotationPresent(Entity.class)) {
                            entityClasses.add(clazz);
                        }
                    } catch (Exception e) {
                        // intentionally ignored — non-loadable class in classpath scan
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan entity classes in package: {}", basePackage, e);
            }
        }

        return entityClasses;
    }

    /**
     * Scan all Enum classes implementing LabeledEnum
     *
     * @return Set of LabeledEnum classes sorted by simple name
     */
    @SuppressWarnings("unchecked")
    private Set<Class<? extends Enum<?>>> scanEnumClasses() {
        Set<Class<? extends Enum<?>>> enumClasses = new TreeSet<>(Comparator.comparing(Class::getSimpleName));

        for (String basePackage : basePackages) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                String pattern = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";
                Resource[] resources = resolver.getResources(pattern);

                for (Resource resource : resources) {
                    try {
                        String className = extractClassName(resource, basePackage);
                        Class<?> clazz = Class.forName(className);

                        // Filter: only enums implementing LabeledEnum
                        if (clazz.isEnum() && LabeledEnum.class.isAssignableFrom(clazz)) {
                            enumClasses.add((Class<? extends Enum<?>>) clazz);
                        }
                    } catch (Exception e) {
                        // intentionally ignored — non-loadable class in classpath scan
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan enum classes in package: {}", basePackage, e);
            }
        }

        return enumClasses;
    }

    /**
     * Extract fully qualified class name from resource
     *
     * @param resource Class file resource
     * @param basePackage Base package name
     * @return Fully qualified class name
     */
    private String extractClassName(Resource resource, String basePackage) throws Exception {
        String uri = resource.getURI().toString();
        int basePackageIdx = uri.indexOf(basePackage.replace('.', '/'));
        if (basePackageIdx == -1) {
            throw new IllegalStateException("Cannot extract class name from: " + uri);
        }

        String classPath = uri.substring(basePackageIdx);
        classPath = classPath.replace(".class", "");
        classPath = classPath.replace('/', '.');

        return classPath;
    }

    /**
     * Build EntityMessages for a single entity class
     *
     * @param entityClass Entity class
     * @param supportedLocales List of locales
     * @return EntityMessages
     */
    private EntityMessages buildEntityMessages(Class<?> entityClass, List<String> supportedLocales) {
        EntityMessages entityMessages = new EntityMessages();
        String entityName = entityClass.getSimpleName();

        // Resolve entity label
        LocalizedString entityLabel = new LocalizedString();
        for (String locale : supportedLocales) {
            Locale loc = new Locale(locale);
            String key = "entities." + entityName;
            String label = EntityMessageResolver.resolveMessage(key, loc);
            // Only add translation if it exists (label differs from key)
            if (!key.equals(label)) {
                entityLabel.addTranslation(locale, label);
            }
        }
        entityMessages.setLabel(entityLabel);

        // Resolve field labels (including inherited fields from parent classes)
        List<Field> allFields = getAllFields(entityClass);
        for (Field field : allFields) {
            // Skip static, transient, and synthetic fields
            if (Modifier.isStatic(field.getModifiers()) ||
                Modifier.isTransient(field.getModifiers()) ||
                field.isSynthetic()) {
                continue;
            }

            String fieldName = field.getName();
            LocalizedString fieldLabel = new LocalizedString();

            for (String locale : supportedLocales) {
                Locale loc = new Locale(locale);
                String key = "entities." + entityName + "." + fieldName;
                String label = EntityMessageResolver.resolveMessage(key, loc);
                // Fallback to common field label if entity-specific key not found
                if (key.equals(label)) {
                    String commonKey = "entities.common." + fieldName;
                    String commonLabel = EntityMessageResolver.resolveMessage(commonKey, loc);
                    if (!commonKey.equals(commonLabel)) {
                        label = commonLabel;
                    }
                }
                // Only add translation if it exists (label differs from key)
                if (!key.equals(label)) {
                    fieldLabel.addTranslation(locale, label);
                }
            }

            entityMessages.addField(fieldName, fieldLabel);
        }

        return entityMessages;
    }

    /**
     * Get all fields from the class hierarchy, traversing up to Object.
     * Fields from parent classes are added first, followed by child class fields.
     * Duplicate field names in child classes override parent entries.
     *
     * @param clazz Target class
     * @return List of all fields in hierarchy order
     */
    private List<Field> getAllFields(Class<?> clazz) {
        LinkedHashMap<String, Field> fieldMap = new LinkedHashMap<>();
        List<Class<?>> hierarchy = new ArrayList<>();

        // Build hierarchy from parent to child
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }

        // Collect fields in hierarchy order (parent first, child overrides)
        for (Class<?> cls : hierarchy) {
            for (Field field : cls.getDeclaredFields()) {
                fieldMap.put(field.getName(), field);
            }
        }

        return new ArrayList<>(fieldMap.values());
    }

    /**
     * Build EnumMessages for a single enum class
     *
     * @param enumClass Enum class
     * @param supportedLocales List of locales
     * @return EnumMessages
     */
    private EnumMessages buildEnumMessages(Class<? extends Enum<?>> enumClass, List<String> supportedLocales) {
        EnumMessages enumMessages = new EnumMessages();
        String enumName = enumClass.getSimpleName();

        // Get all enum constants
        Enum<?>[] enumConstants = enumClass.getEnumConstants();

        for (Enum<?> enumConstant : enumConstants) {
            String valueName = enumConstant.name();
            LocalizedString valueLabel = new LocalizedString();

            for (String locale : supportedLocales) {
                Locale loc = new Locale(locale);
                String key = "enums." + enumName + "." + valueName;
                String label = EnumMessageResolver.resolveMessage(key, loc);
                // Only add translation if it exists (label differs from key)
                if (!key.equals(label)) {
                    valueLabel.addTranslation(locale, label);
                }
            }

            enumMessages.addValue(valueName, valueLabel);
        }

        return enumMessages;
    }
}
