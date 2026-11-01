package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.core.annotation.BooleanTone;
import dev.simplecore.simplix.core.annotation.CodeValue;
import dev.simplecore.simplix.core.annotation.MoneyValue;
import dev.simplecore.simplix.core.annotation.NameField;
import dev.simplecore.simplix.core.annotation.NameFields;
import dev.simplecore.simplix.core.annotation.References;
import dev.simplecore.simplix.core.annotation.ReferencesByKind;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

        ReferencesByKind byKind = field.getAnnotation(ReferencesByKind.class);
        if (byKind != null) {
            Map<String, String> kinds = new TreeMap<>();
            for (ReferencesByKind.Kind kind : byKind.kinds()) kinds.put(kind.value(), kind.entity());
            return new ReferenceMeta(null, null, false, byKind.kindField(), Collections.unmodifiableMap(kinds));
        }

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

    /**
     * The {@link MoneyValue} on the DTO field, or else on the entity field it maps to; null when
     * neither carries one. A dotted path reaches into another entity, whose fields this entity's
     * mapping does not describe.
     */
    static MoneyValue moneyOf(Class<?> dto, String dtoField, Class<?> entity, String path) {
        Field own = findField(dto, dtoField);
        if (own != null && own.isAnnotationPresent(MoneyValue.class)) return own.getAnnotation(MoneyValue.class);
        if (entity == null || path == null || path.indexOf('.') >= 0) return null;
        Field mapped = findField(entity, path);
        return mapped == null ? null : mapped.getAnnotation(MoneyValue.class);
    }

    static String idFieldOf(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) return field.getName();
            }
        }
        return null;
    }

    /**
     * The fields the entity's {@link NameFields} lists, else the fields marked {@link NameField}
     * ordered by {@link NameField#order()}, or {@code null} when the entity names none. Two marks
     * with the same order leave a client two answers for one position, and a list beside marks
     * leaves it two lists, so both are refused rather than resolved; a listed field the hierarchy
     * does not declare is refused too.
     */
    static List<String> nameFieldsOf(Class<?> entity) {
        List<String> marked = markedNameFieldsOf(entity);
        NameFields listed = listedNameFieldsOf(entity);
        if (listed == null) return marked;
        if (marked != null) {
            throw new IllegalStateException(entity.getName() + " declares @NameFields and marks @NameField on "
                    + String.join(", ", marked));
        }
        for (String name : listed.value()) {
            if (findField(entity, name) == null) {
                throw new IllegalStateException(entity.getName() + " lists " + name + " in @NameFields, which it does not declare");
            }
        }
        return listed.value().length == 0 ? null : List.of(listed.value());
    }

    private static NameFields listedNameFieldsOf(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            NameFields listed = c.getAnnotation(NameFields.class);
            if (listed != null) return listed;
        }
        return null;
    }

    private static List<String> markedNameFieldsOf(Class<?> entity) {
        Map<Integer, List<String>> byOrder = new TreeMap<>();
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                NameField mark = field.getAnnotation(NameField.class);
                if (mark != null) byOrder.computeIfAbsent(mark.order(), k -> new ArrayList<>()).add(field.getName());
            }
        }
        List<String> named = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> position : byOrder.entrySet()) {
            if (position.getValue().size() > 1) {
                throw new IllegalStateException(entity.getName() + " marks more than one field with @NameField(order = "
                        + position.getKey() + "): " + String.join(", ", position.getValue()));
            }
            named.add(position.getValue().get(0));
        }
        return named.isEmpty() ? null : List.copyOf(named);
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
