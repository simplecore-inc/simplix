package dev.simplecore.simplix.core.convert.enumeration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standard implementation of EnumConverter
 */
public class StandardEnumConverter implements EnumConverter {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Enum<?>> T fromString(String value, Class<T> enumType) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return (T) Enum.valueOf((Class<? extends Enum>) enumType, value);
        } catch (IllegalArgumentException e) {
            // Find enum constant with case-insensitive name match
            String normalizedValue = value.trim();
            for (T constant : enumType.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(normalizedValue)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException("Cannot find value '" + value + 
                "' in enum '" + enumType.getSimpleName() + "'", e);
        }
    }

    @Override
    public String toString(Enum<?> value) {
        return value == null ? null : value.name();
    }

    @Override
    public Map<String, Object> toMap(Enum<?> value) {
        if (value == null) {
            return null;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", value.getClass().getSimpleName());
        fields.put("value", value.name());

        try {
            // Use getMethods() instead of getDeclaredMethods() to include inherited methods from interfaces
            for (Method method : value.getClass().getMethods()) {
                String methodName = method.getName();
                // Process only getter methods
                if (methodName.startsWith("get")
                    && !methodName.equals("getClass")
                    && !methodName.equals("getDeclaringClass")
                    && method.getParameterCount() == 0
                    && method.getDeclaringClass() != Enum.class
                    && method.getDeclaringClass() != Object.class) {

                    String fieldName = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                    Object fieldValue = method.invoke(value);
                    if (fieldValue != null) {
                        fields.put(fieldName, fieldValue);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract enum fields", e);
        }
        return fields;
    }
} 