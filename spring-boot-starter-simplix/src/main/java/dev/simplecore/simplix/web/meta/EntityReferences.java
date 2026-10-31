package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.BooleanTone;
import dev.simplecore.simplix.core.annotation.CodeValue;
import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads what an entity's mapping says about a field pointing at another entity. Three sources,
 * in order:
 * <ol>
 *     <li>{@link References} on the entity field (an id or id collection JPA does not map);</li>
 *     <li>a scalar column that shares its join column with a {@code @ManyToOne} or
 *         {@code @OneToOne} of the same entity (the read-only id beside the association);</li>
 *     <li>a dotted path {@code association.targetId} through such an association.</li>
 * </ol>
 * The entity's own identifier, and every field none of these describe, yields {@code null}.
 */
final class EntityReferences {

    private EntityReferences() {}

    static ReferenceMeta resolve(Class<?> entity, String path) {
        if (entity == null || path == null || path.isEmpty()) return null;
        int dot = path.indexOf('.');
        if (dot > 0) {
            Field association = findField(entity, path.substring(0, dot));
            if (association == null || !isToOne(association)) return null;
            Class<?> target = association.getType();
            String idField = idFieldOf(target);
            return path.substring(dot + 1).equals(idField)
                    ? new ReferenceMeta(target.getSimpleName(), idField, false) : null;
        }
        Field field = findField(entity, path);
        if (field == null || field.isAnnotationPresent(Id.class)) return null;

        References declared = field.getAnnotation(References.class);
        if (declared != null) {
            Class<?> target = declared.value();
            String matched = declared.field().isEmpty() ? idFieldOf(target) : declared.field();
            return new ReferenceMeta(target.getSimpleName(), matched,
                    Collection.class.isAssignableFrom(field.getType()));
        }

        Column column = field.getAnnotation(Column.class);
        if (column == null || column.name().isEmpty()) return null;
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field candidate : c.getDeclaredFields()) {
                if (!isToOne(candidate)) continue;
                JoinColumn join = candidate.getAnnotation(JoinColumn.class);
                if (join != null && join.name().equalsIgnoreCase(column.name())) {
                    Class<?> target = candidate.getType();
                    return new ReferenceMeta(target.getSimpleName(), idFieldOf(target), false);
                }
            }
        }
        return null;
    }

    /**
     * The {@code @BooleanTone} of a flag: on the DTO field first (a derived flag), then on the
     * entity field it maps to. {@code null} when neither declares one.
     */
    static Map<String, String> booleanTones(Class<?> dto, String dtoField, Class<?> entity, String path) {
        Field own = findField(dto, dtoField);
        BooleanTone tone = own != null ? own.getAnnotation(BooleanTone.class) : null;
        if (tone == null && entity != null && path != null && path.indexOf('.') < 0) {
            Field mapped = findField(entity, path);
            if (mapped != null) tone = mapped.getAnnotation(BooleanTone.class);
        }
        if (tone == null) return null;
        Map<String, String> tones = new TreeMap<>();
        tones.put("true", tone.whenTrue().name().toLowerCase(Locale.ROOT));
        tones.put("false", tone.whenFalse().name().toLowerCase(Locale.ROOT));
        return tones;
    }

    /**
     * Whether the DTO field, or the entity field it maps to, carries {@link CodeValue}. A dotted
     * path reaches into another entity, whose fields this entity's mapping does not describe.
     */
    static boolean isCode(Class<?> dto, String dtoField, Class<?> entity, String path) {
        Field own = findField(dto, dtoField);
        if (own != null && own.isAnnotationPresent(CodeValue.class)) return true;
        if (entity == null || path == null || path.indexOf('.') >= 0) return false;
        Field mapped = findField(entity, path);
        return mapped != null && mapped.isAnnotationPresent(CodeValue.class);
    }

    static String idFieldOf(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) return field.getName();
            }
        }
        return null;
    }

    private static boolean isToOne(Field field) {
        return field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class);
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
