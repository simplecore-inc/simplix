package dev.simplecore.simplix.web.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reads the value a new record starts a field with, so a client's form for a new record starts
 * where the server would. Two sources, in order: the DTO field's own initial value, then the
 * initial value of the entity field it maps to (a dotted path reaches another entity and is not
 * read). An initial value is the field initializer, or the {@code $default$<field>} method Lombok's
 * {@code @Builder.Default} moves it into. Only a boolean, a number, a string or an enum constant
 * (published by name) is a default; anything else, and a field with no initial value, yields
 * {@code null}.
 */
final class FieldDefaults {

    private FieldDefaults() {}

    static Object of(Class<?> dto, String dtoField, Class<?> entity, String path) {
        Object own = initialValue(dto, dtoField);
        if (own != null) return own;
        if (entity == null || path == null || path.indexOf('.') >= 0) return null;
        return initialValue(entity, path);
    }

    private static Object initialValue(Class<?> type, String name) {
        Field field = findField(type, name);
        if (field == null || Modifier.isStatic(field.getModifiers())) return null;
        Object value = builderDefault(field.getDeclaringClass(), name);
        if (value == null) value = constructed(type, field);
        return scalar(value);
    }

    /** Lombok's {@code @Builder.Default} keeps the initializer in a private static method. */
    private static Object builderDefault(Class<?> declaring, String name) {
        try {
            Method method = declaring.getDeclaredMethod("$default$" + name);
            if (!Modifier.isStatic(method.getModifiers())) return null;
            method.setAccessible(true);
            return method.invoke(null);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException | InaccessibleObjectException | SecurityException unreadable) {
            return null;
        }
    }

    /** The field's value on an instance made through the no-argument constructor, where one exists. */
    private static Object constructed(Class<?> type, Field field) {
        if (Modifier.isAbstract(type.getModifiers()) || type.isInterface()) return null;
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            field.setAccessible(true);
            return field.get(instance);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (ReflectiveOperationException | InaccessibleObjectException | SecurityException unreadable) {
            return null;
        }
    }

    private static Object scalar(Object value) {
        if (value instanceof Boolean || value instanceof Number || value instanceof String) return value;
        if (value instanceof Enum<?> constant) return constant.name();
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        return null;
    }
}
