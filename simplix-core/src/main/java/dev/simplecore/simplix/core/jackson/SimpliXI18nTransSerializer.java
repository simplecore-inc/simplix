package dev.simplecore.simplix.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import dev.simplecore.simplix.core.jackson.annotation.I18nTrans;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;

/**
 * Jackson serializer for {@link SimpliXI18nTransSerializer} annotation.
 * Automatically translates field values based on the current locale during JSON serialization.
 *
 * <p>This serializer:
 * <ul>
 *   <li>Retrieves the current locale from {@link LocaleContextHolder}</li>
 *   <li>Accesses the source i18n Map field via reflection</li>
 *   <li>Extracts the appropriate translation based on locale with fallback chain</li>
 *   <li>Returns the original value if no translation is available</li>
 * </ul>
 *
 * <p>Fallback chain:
 * <ol>
 *   <li>Exact locale match (e.g., "ko_KR")</li>
 *   <li>Language code only (e.g., "ko")</li>
 *   <li>Default locale from annotation</li>
 *   <li>Original field value (if not null)</li>
 * </ol>
 */
public class SimpliXI18nTransSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private String sourceFieldName;
    private String defaultLocale;

    /**
     * Default constructor for Jackson.
     */
    public SimpliXI18nTransSerializer() {
    }

    /**
     * Constructor with annotation metadata.
     *
     * @param sourceFieldName name of the source field containing i18n Map
     * @param defaultLocale   default locale code
     */
    public SimpliXI18nTransSerializer(String sourceFieldName, String defaultLocale) {
        this.sourceFieldName = sourceFieldName;
        this.defaultLocale = defaultLocale;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            I18nTrans annotation = property.getAnnotation(I18nTrans.class);
            if (annotation != null) {
                return new SimpliXI18nTransSerializer(annotation.source(), annotation.defaultLocale());
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

        // Get current locale
        Locale currentLocale = LocaleContextHolder.getLocale();
        if (currentLocale == null) {
            currentLocale = Locale.forLanguageTag(defaultLocale);
        }

        // Get i18n Map from source field
        Map<String, String> i18nMap = getI18nMap(currentBean);
        if (i18nMap == null || i18nMap.isEmpty()) {
            gen.writeObject(value);
            return;
        }

        // Extract translation with fallback chain
        String translatedValue = extractTranslation(i18nMap, currentLocale, value);
        gen.writeObject(translatedValue);
    }

    /**
     * Retrieves the i18n Map from the source field using reflection.
     *
     * @param bean the bean containing the source field
     * @return i18n Map or null if not accessible
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getI18nMap(Object bean) {
        try {
            Field field = findField(bean.getClass(), sourceFieldName);
            if (field == null) {
                return null;
            }

            field.setAccessible(true);
            Object fieldValue = field.get(bean);

            if (fieldValue instanceof Map) {
                return (Map<String, String>) fieldValue;
            }
        } catch (IllegalAccessException | SecurityException e) {
            // ℹ Log warning but continue with fallback
        }
        return null;
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

        // 3. Try default locale
        if (i18nMap.containsKey(defaultLocale)) {
            return i18nMap.get(defaultLocale);
        }

        // 4. Fallback to original value
        return originalValue != null ? originalValue.toString() : null;
    }
}
