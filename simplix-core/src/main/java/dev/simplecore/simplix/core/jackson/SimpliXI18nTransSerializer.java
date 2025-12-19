package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import dev.simplecore.simplix.core.config.SimpliXI18nConfigHolder;
import dev.simplecore.simplix.core.jackson.annotation.I18nTrans;
import org.springframework.context.i18n.LocaleContextHolder;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jackson serializer for {@link I18nTrans} annotation.
 * Automatically translates field values based on the current locale during JSON serialization.
 * <p>
 * This serializer supports two modes:
 * <ul>
 *   <li><b>Simple mode</b>: When target is empty, translates the annotated String field</li>
 *   <li><b>Nested mode</b>: When target is specified, modifies a nested object's field before serialization</li>
 * </ul>
 * <p>
 * Fallback chain for translation:
 * <ol>
 *   <li>Exact locale match (e.g., "ko_KR")</li>
 *   <li>Language code only (e.g., "ko")</li>
 *   <li>Default locale from configuration</li>
 *   <li>First available translation from supported locales (in priority order)</li>
 *   <li>Any available translation from the map (for unsupported locales)</li>
 *   <li>Original field value (if not null)</li>
 * </ol>
 *
 * @see SimpliXI18nConfigHolder for configuration
 */
@Slf4j
public class SimpliXI18nTransSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private String sourceFieldPath;
    private String targetFieldPath;
    private String defaultLocale;
    private BeanProperty beanProperty;

    /**
     * Default constructor for Jackson.
     */
    public SimpliXI18nTransSerializer() {
    }

    /**
     * Constructor with annotation metadata.
     *
     * @param sourceFieldPath path to the source field containing i18n Map (supports dot notation)
     * @param targetFieldPath path to the target field to set translated value (supports dot notation)
     * @param defaultLocale   default locale code
     * @param beanProperty    the bean property for contextual serialization
     */
    public SimpliXI18nTransSerializer(String sourceFieldPath, String targetFieldPath,
                                       String defaultLocale, BeanProperty beanProperty) {
        this.sourceFieldPath = sourceFieldPath;
        this.targetFieldPath = targetFieldPath;
        this.defaultLocale = defaultLocale;
        this.beanProperty = beanProperty;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            I18nTrans annotation = property.getAnnotation(I18nTrans.class);
            if (annotation != null) {
                return new SimpliXI18nTransSerializer(
                        annotation.source(),
                        annotation.target(),
                        annotation.defaultLocale(),
                        property  // Store the property for field-level annotations
                );
            }
        }
        return this;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        Object currentBean = gen.currentValue();
        if (currentBean == null) {
            gen.writeObject(value);
            return;
        }

        Locale currentLocale = LocaleContextHolder.getLocale();

        // Nested mode: target is specified
        if (targetFieldPath != null && !targetFieldPath.isEmpty()) {
            serializeNestedObject(value, gen, currentBean, currentLocale, serializers);
            return;
        }

        // Simple mode: translate the annotated field itself
        Map<String, String> i18nMap = getI18nMapByPath(currentBean, sourceFieldPath);

        log.debug("I18nTrans serialize - sourceField: {}, locale: {}, i18nMap: {}, originalValue: {}",
                sourceFieldPath, currentLocale, i18nMap, value);

        if (i18nMap == null || i18nMap.isEmpty()) {
            log.debug("I18nTrans - i18nMap is null or empty, returning original value: {}", value);
            gen.writeObject(value);
            return;
        }

        String translatedValue = extractTranslation(i18nMap, currentLocale, value);
        log.debug("I18nTrans - translated value: {}", translatedValue);
        gen.writeObject(translatedValue);
    }

    /**
     * Serializes a nested object after translating its target field.
     *
     * @param value         the nested object to serialize
     * @param gen           JSON generator
     * @param bean          the parent bean containing source i18n Map
     * @param locale        current locale
     * @param serializers   serializer provider
     */
    private void serializeNestedObject(Object value, JsonGenerator gen, Object bean,
                                       Locale locale, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 1. Get i18n Map from source path
        Map<String, String> i18nMap = getI18nMapByPath(bean, sourceFieldPath);

        log.debug("I18nTrans nested - source: {}, target: {}, locale: {}, i18nMap: {}",
                sourceFieldPath, targetFieldPath, locale, i18nMap);

        String translatedValue = null;
        String originalValue = null;
        String relativeTargetPath = null;

        if (i18nMap != null && !i18nMap.isEmpty()) {
            // 2. Get current value of target field
            Object targetCurrentValue = getValueByPath(bean, targetFieldPath);
            originalValue = targetCurrentValue != null ? targetCurrentValue.toString() : null;

            // 3. Extract translated value
            translatedValue = extractTranslation(i18nMap, locale, targetCurrentValue);

            log.debug("I18nTrans nested - targetCurrentValue: {}, translatedValue: {}",
                    targetCurrentValue, translatedValue);

            // 4. Set translated value to target field in the nested object
            // Remove the first path segment (the annotated field name) from target path
            relativeTargetPath = removeFirstPathSegment(targetFieldPath);
            setValueByPath(value, relativeTargetPath, translatedValue);
        }

        try {
            // 5. Serialize the modified nested object
            // Use findTypedValueSerializer with beanProperty to respect field-level annotations
            // like @JsonIncludeProperties
            if (beanProperty != null) {
                JsonSerializer<Object> serializer = serializers.findTypedValueSerializer(
                        value.getClass(), true, beanProperty);
                serializer.serialize(value, gen, serializers);
            } else {
                serializers.defaultSerializeValue(value, gen);
            }
        } finally {
            // 6. Restore original value to avoid side effects on the original object
            if (relativeTargetPath != null && originalValue != null) {
                setValueByPath(value, relativeTargetPath, originalValue);
            }
        }
    }

    /**
     * Retrieves the i18n Map from a path (supports dot notation for nested objects).
     *
     * @param bean the root bean
     * @param path the field path (e.g., "nameI18n" or "tagGroup.nameI18n")
     * @return i18n Map or null if not accessible
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getI18nMapByPath(Object bean, String path) {
        Object value = getValueByPath(bean, path);
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        return null;
    }

    /**
     * Gets a value from a path using dot notation.
     *
     * @param bean the root bean
     * @param path the field path (e.g., "tagGroup.name")
     * @return the field value or null if not accessible
     */
    private Object getValueByPath(Object bean, String path) {
        if (bean == null || path == null || path.isEmpty()) {
            return null;
        }

        String[] segments = path.split("\\.");
        Object current = bean;

        for (String segment : segments) {
            if (current == null) {
                return null;
            }

            Field field = findField(current.getClass(), segment);
            if (field == null) {
                log.debug("I18nTrans - field not found: {} in class {}", segment, current.getClass().getName());
                return null;
            }

            try {
                field.setAccessible(true);
                current = field.get(current);
            } catch (IllegalAccessException | SecurityException e) {
                log.debug("I18nTrans - failed to access field: {}", segment, e);
                return null;
            }
        }

        return current;
    }

    /**
     * Sets a value to a field in the given object.
     *
     * @param bean      the object containing the field
     * @param fieldName the field name
     * @param value     the value to set
     */
    private void setFieldValue(Object bean, String fieldName, String value) {
        if (bean == null || fieldName == null) {
            return;
        }

        Field field = findField(bean.getClass(), fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                field.set(bean, value);
            } catch (IllegalAccessException | SecurityException e) {
                log.warn("I18nTrans - failed to set field value: {} = {}", fieldName, value, e);
            }
        }
    }

    /**
     * Removes the first segment of a dot-separated path.
     *
     * @param path the path (e.g., "container.inner.name")
     * @return the remaining path (e.g., "inner.name"), or the last segment if only one level
     */
    private String removeFirstPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        int firstDot = path.indexOf('.');
        return firstDot >= 0 ? path.substring(firstDot + 1) : path;
    }

    /**
     * Sets a value at a path using dot notation.
     * Navigates through the object graph and sets the value on the final field.
     *
     * @param bean  the root object to start navigation from
     * @param path  the field path (e.g., "inner.name" or "name")
     * @param value the value to set
     */
    private void setValueByPath(Object bean, String path, String value) {
        if (bean == null || path == null || path.isEmpty()) {
            return;
        }

        String[] segments = path.split("\\.");
        Object current = bean;

        // Navigate to the parent of the target field
        for (int i = 0; i < segments.length - 1; i++) {
            if (current == null) {
                return;
            }

            Field field = findField(current.getClass(), segments[i]);
            if (field == null) {
                log.debug("I18nTrans - field not found during path navigation: {} in class {}",
                        segments[i], current.getClass().getName());
                return;
            }

            try {
                field.setAccessible(true);
                current = field.get(current);
            } catch (IllegalAccessException | SecurityException e) {
                log.debug("I18nTrans - failed to access field during path navigation: {}", segments[i], e);
                return;
            }
        }

        // Set the value on the final field
        if (current != null) {
            String finalFieldName = segments[segments.length - 1];
            setFieldValue(current, finalFieldName, value);
        }
    }

    /**
     * Gets the last segment of a dot-separated path.
     *
     * @param path the path (e.g., "tagGroup.name")
     * @return the last segment (e.g., "name")
     */
    private String getLastPathSegment(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    /**
     * Finds a field by name in the class hierarchy.
     *
     * @param clazz     the class to search
     * @param fieldName the field name
     * @return the Field object or null if not found
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Extracts translation from i18n Map with fallback chain.
     * <p>
     * Fallback order:
     * <ol>
     *   <li>Exact locale match (e.g., "ko_KR")</li>
     *   <li>Language code only (e.g., "ko")</li>
     *   <li>Default locale from configuration</li>
     *   <li>First available from supported locales</li>
     *   <li>Original field value</li>
     * </ol>
     *
     * @param i18nMap       the i18n translation Map
     * @param currentLocale the current locale
     * @param originalValue the original field value
     * @return translated value or original value if no translation found
     */
    private String extractTranslation(Map<String, String> i18nMap, Locale currentLocale, Object originalValue) {
        // 1. Try exact locale match (e.g., "ko_KR")
        String localeKey = currentLocale.toString();
        if (i18nMap.containsKey(localeKey)) {
            return i18nMap.get(localeKey);
        }

        // 2. Try language code only (e.g., "ko")
        String languageCode = currentLocale.getLanguage();
        if (i18nMap.containsKey(languageCode)) {
            return i18nMap.get(languageCode);
        }

        // 3. Try default locale from configuration (prioritize config over annotation)
        String configDefaultLocale = SimpliXI18nConfigHolder.getDefaultLocale();
        if (configDefaultLocale != null && i18nMap.containsKey(configDefaultLocale)) {
            return i18nMap.get(configDefaultLocale);
        }

        // 4. Try default locale from annotation (fallback)
        if (defaultLocale != null && !defaultLocale.equals(configDefaultLocale) && i18nMap.containsKey(defaultLocale)) {
            return i18nMap.get(defaultLocale);
        }

        // 5. Try first available from supported locales (in priority order)
        List<String> supportedLocales = SimpliXI18nConfigHolder.getSupportedLocales();
        if (supportedLocales != null) {
            for (String locale : supportedLocales) {
                if (i18nMap.containsKey(locale)) {
                    return i18nMap.get(locale);
                }
            }
        }

        // 6. Return any available translation from the map (for locales not in supported list)
        if (!i18nMap.isEmpty()) {
            return i18nMap.values().iterator().next();
        }

        // 7. Fallback to original value
        return originalValue != null ? originalValue.toString() : null;
    }
}
