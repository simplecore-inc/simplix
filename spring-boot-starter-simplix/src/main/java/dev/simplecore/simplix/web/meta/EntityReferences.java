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
 *     <li>{@link References} on the entity field (an id or id collection JPA does not map), naming
 *         its target by class or by a served entity's simple name;</li>
 *     <li>a scalar column that shares its join column with a {@code @ManyToOne} or
 *         {@code @OneToOne} of the same entity (the read-only id beside the association);</li>
 *     <li>a dotted path {@code association.targetId} through such an association.</li>
 * </ol>
 * The entity's own identifier, and every field none of these describe, yields {@code null}.
 */
final class EntityReferences {

    private EntityReferences() {}

    /**
     * The entities a {@link References#entity()} name is looked up among: those SimpliX Meta
     * serves, by simple name. Two served entities sharing a simple name stay recorded, so a name
     * that could mean either is refused rather than resolved to whichever was met last.
     */
    static final class Directory {

        static final Directory EMPTY = new Directory(List.of());

        private final Map<String, List<Class<?>>> byName = new TreeMap<>();

        Directory(Collection<Class<?>> entities) {
            for (Class<?> type : entities) {
                List<Class<?>> named = byName.computeIfAbsent(type.getSimpleName(), k -> new ArrayList<>());
                if (!named.contains(type)) named.add(type);
            }
        }

        /** The entity {@code name} denotes, for the field {@code declaredAt} names it from. */
        Class<?> named(String name, String declaredAt) {
            List<Class<?>> named = byName.getOrDefault(name, List.of());
            if (named.isEmpty()) {
                throw new IllegalStateException(declaredAt + " declares @References(entity = \"" + name
                        + "\"), and SimpliX Meta serves no entity of that simple name");
            }
            if (named.size() > 1) {
                List<String> classes = named.stream().map(Class::getName).sorted().toList();
                throw new IllegalStateException(declaredAt + " declares @References(entity = \"" + name
                        + "\"), which names more than one served entity: " + String.join(", ", classes)
                        + "; name the target by class instead");
            }
            return named.get(0);
        }
    }

    static ReferenceMeta resolve(Class<?> entity, String path) {
        return resolve(entity, path, Directory.EMPTY);
    }

    static ReferenceMeta resolve(Class<?> entity, String path, Directory entities) {
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
            Class<?> target = targetOf(declared, entity.getName() + "." + field.getName(), entities);
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
     * The {@code nameField} the entity field {@code path} declares on its {@link References} or
     * {@link ReferencesByKind}, or {@code null} when it declares none. A dotted path reaches into
     * another entity, whose fields this entity's mapping does not describe.
     */
    static String declaredNameField(Class<?> entity, String path) {
        if (entity == null || path == null || path.isEmpty() || path.indexOf('.') >= 0) return null;
        Field field = findField(entity, path);
        if (field == null) return null;
        ReferencesByKind byKind = field.getAnnotation(ReferencesByKind.class);
        String declared = byKind != null ? byKind.nameField()
                : field.isAnnotationPresent(References.class) ? field.getAnnotation(References.class).nameField() : "";
        return declared.isBlank() ? null : declared;
    }

    /**
     * The entity a {@link References} names: its class, or the served entity its simple name
     * denotes. Exactly one of the two is set; both or neither is refused, naming the field.
     */
    private static Class<?> targetOf(References declared, String declaredAt, Directory entities) {
        boolean byClass = declared.value() != void.class;
        boolean byName = !declared.entity().isEmpty();
        if (byClass && byName) {
            throw new IllegalStateException(declaredAt + " declares @References with both a class ("
                    + declared.value().getName() + ") and an entity name (\"" + declared.entity()
                    + "\"); set exactly one");
        }
        if (!byClass && !byName) {
            throw new IllegalStateException(declaredAt
                    + " declares @References with neither a class nor an entity name; set exactly one");
        }
        return byClass ? declared.value() : entities.named(declared.entity(), declaredAt);
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
