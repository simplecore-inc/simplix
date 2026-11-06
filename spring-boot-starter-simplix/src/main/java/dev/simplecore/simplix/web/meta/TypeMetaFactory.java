package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import dev.simplecore.simplix.core.annotation.MoneyValue;
import dev.simplecore.simplix.core.annotation.FieldLabel;
import dev.simplecore.simplix.web.meta.model.ConstraintMeta;
import dev.simplecore.simplix.web.meta.model.ReferenceMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
import dev.simplecore.simplix.web.meta.model.MoneyMeta;
import dev.simplecore.simplix.web.meta.model.SearchableMeta;
import dev.simplecore.simplix.web.meta.model.TypeMeta;
import dev.simplecore.simplix.web.meta.model.TypeRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.ResolvableType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link TypeMeta} for one DTO class. Serialization membership and wire names come from
 * the APPLICATION's ObjectMapper, never a fresh one; declarations come from the backing field:
 * constraints, {@code @FieldLabel}, {@code @SearchableField} (read reflectively, the library is
 * optional), {@code @Schema}, {@code @JsonFormat} patterns.
 *
 * <p>A type declared inside an {@code <Entity>DTOs} container whose entity is known to the
 * builder is tied to that entity: its fields gain reference metadata from the entity mapping
 * ({@link EntityReferences}), and a field sorted through locale siblings gains a sort alias.
 */
public class TypeMetaFactory {

    private static final Pattern MESSAGE_KEY = Pattern.compile("^\\{(.+)}$");
    private static final String SEARCHABLE_FIELD = "dev.simplecore.searchable.core.annotation.SearchableField";

    private final ObjectMapper mapper;
    private final TypeRefMapper typeRefMapper;
    private static final String DTO_CONTAINER_SUFFIX = "DTOs";
    private static final Pattern LOCALE_SORT_SIBLING = Pattern.compile("^(.+)Sort([A-Z][a-z])$");

    private final MetaTypeRegistry registry;
    private final Map<String, Class<?>> entities;
    private final EntityReferences.Directory directory;
    private final List<UnresolvedName> unresolvedNames = new ArrayList<>();

    /**
     * A reference whose declared {@code nameField} the DTO publishing it does not carry: neither a
     * property of that name nor a nested record reaching it. {@code type} is the DTO's type name,
     * {@code field} its field, {@code declaredAt} the entity field declaring the name.
     */
    public record UnresolvedName(String type, String field, String declaredAt, String nameField) {}

    public TypeMetaFactory(ObjectMapper mapper, TypeRefMapper typeRefMapper, MetaTypeRegistry registry) {
        this(mapper, typeRefMapper, registry, Map.of());
    }

    public TypeMetaFactory(ObjectMapper mapper, TypeRefMapper typeRefMapper, MetaTypeRegistry registry,
                           Map<String, Class<?>> entities) {
        this(mapper, typeRefMapper, registry, entities, entities.values());
    }

    /**
     * @param entities the served entities by simple name, which a DTO container is matched to
     * @param served every served entity, a simple name shared by two of them included, which a
     *               {@code @References(entity = ...)} name is resolved against
     */
    public TypeMetaFactory(ObjectMapper mapper, TypeRefMapper typeRefMapper, MetaTypeRegistry registry,
                           Map<String, Class<?>> entities, Collection<Class<?>> served) {
        this.mapper = mapper;
        this.typeRefMapper = typeRefMapper;
        this.registry = registry;
        this.entities = entities;
        this.directory = new EntityReferences.Directory(served);
    }

    /** The entity whose {@code <Entity>DTOs} container declares {@code type}, when the builder knows it. */
    String entityOf(Class<?> type) {
        Class<?> container = type.getEnclosingClass();
        if (container == null) return null;
        String name = container.getSimpleName();
        if (!name.endsWith(DTO_CONTAINER_SUFFIX)) return null;
        String entity = name.substring(0, name.length() - DTO_CONTAINER_SUFFIX.length());
        return entities.containsKey(entity) ? entity : null;
    }

    public TypeMeta create(Class<?> type) {
        BeanDescription bean = mapper.getSerializationConfig()
                .introspect(mapper.constructType(type));

        List<FieldMeta> fields = new ArrayList<>();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            Field backing = backingField(property);
            Class<?> declaring = backing != null ? backing.getDeclaringClass()
                    : property.getAccessor() != null ? property.getAccessor().getDeclaringClass() : type;
            if (!declaring.equals(type)) continue; // inherited: lives on the parent TypeMeta
            FieldMeta field = toFieldMeta(property, backing, type);
            if (field != null) fields.add(field);
        }
        fields.addAll(writeOnlyFields(type));

        fields.sort(declarationOrder(type));

        String entity = entityOf(type);
        Class<?> entityClass = entity != null ? entities.get(entity) : null;
        fields.replaceAll(field -> {
            String path = field.searchable() != null && field.searchable().entityField() != null
                    ? field.searchable().entityField() : field.name();
            FieldMeta result = field;
            if (entityClass != null) {
                ReferenceMeta reference = EntityReferences.resolve(entityClass, path, directory);
                if (reference != null) result = result.withReference(named(reference, type, field.name(), entityClass, path));
            }
            Map<String, String> tones = EntityReferences.booleanTones(type, field.name(), entityClass, path);
            if (tones != null) result = result.withTones(tones);
            if (EntityReferences.isCode(type, field.name(), entityClass, path)) {
                result = result.withFormat(FieldMeta.FORMAT_CODE);
            }
            MoneyValue money = EntityReferences.moneyOf(type, field.name(), entityClass, path);
            if (money != null) result = result.withMoney(new MoneyMeta(money.currency(), money.scale()));
            Object initial = FieldDefaults.of(type, field.name(), entityClass, path);
            if (initial != null) result = result.withDefaultValue(initial);
            return result;
        });
        withLocaleSortAliases(fields);

        String extendsType = null;
        Class<?> parent = type.getSuperclass();
        if (parent != null && parent != Object.class && !parent.getName().startsWith("java.")) {
            extendsType = registry.register(parent);
        }

        List<String> typeParams = Arrays.stream(type.getTypeParameters())
                .map(p -> (String) p.getName()).toList();

        Schema classSchema = type.getAnnotation(Schema.class);
        String description = classSchema != null && !classSchema.description().isEmpty()
                ? classSchema.description() : null;

        return new TypeMeta(type.getName(), extendsType, typeParams, description, fields, entity);
    }

    /**
     * The reference with the {@code nameField} its entity field declares, where this DTO carries
     * that path; where it does not, the reference goes out without one and the miss is recorded
     * for {@link #unresolvedNames()}, since only the operations tell a response from a request body.
     */
    private ReferenceMeta named(ReferenceMeta reference, Class<?> type, String field, Class<?> entity, String path) {
        String nameField = EntityReferences.declaredNameField(entity, path);
        if (nameField == null) return reference;
        if (carriesPath(type, nameField)) return reference.withNameField(nameField);
        unresolvedNames.add(new UnresolvedName(MetaTypeRegistry.typeName(type), field,
                entity.getName() + "." + path, nameField));
        return reference;
    }

    /**
     * Whether {@code path}, a wire name or a dotted path of wire names, reaches a value in what
     * {@code type} serializes: each segment but the last a property holding one nested record (not
     * a collection, a map or an array), the last a property holding a single value. A nested record
     * is read as the holding property serializes it, so a segment its
     * {@code @JsonIncludeProperties} leaves out, or its {@code @JsonIgnoreProperties} names, is not
     * carried.
     */
    boolean carriesPath(Class<?> type, String path) {
        Class<?> current = type;
        BeanPropertyDefinition holder = null;
        String[] segments = path.split("\\.", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || current == null) return false;
            // Only a record a client receives nested is stepped into, never a value's own getters.
            if (i > 0 && (current.isPrimitive() || current.isEnum() || current.getName().startsWith("java."))) return false;
            if (holder != null && !holderSerializes(holder, segment)) return false;
            BeanPropertyDefinition property = serializedProperty(current, segment);
            if (property == null) return false;
            Class<?> raw = property.getPrimaryType().getRawClass();
            if (raw.isArray() || Collection.class.isAssignableFrom(raw) || Map.class.isAssignableFrom(raw)) return false;
            holder = property;
            current = raw;
        }
        return true;
    }

    /** Whether the nested record a property holds is serialized with {@code name} by that property. */
    private static boolean holderSerializes(BeanPropertyDefinition holder, String name) {
        AnnotatedMember member = holder.getPrimaryMember();
        if (member == null) return true;
        JsonIncludeProperties included = member.getAnnotation(JsonIncludeProperties.class);
        if (included != null && !Arrays.asList(included.value()).contains(name)) return false;
        JsonIgnoreProperties ignored = member.getAnnotation(JsonIgnoreProperties.class);
        return ignored == null || !Arrays.asList(ignored.value()).contains(name);
    }

    private BeanPropertyDefinition serializedProperty(Class<?> type, String name) {
        BeanDescription bean = mapper.getSerializationConfig().introspect(mapper.constructType(type));
        for (BeanPropertyDefinition property : bean.findProperties()) {
            if (property.getName().equals(name) && property.couldSerialize()) return property;
        }
        return null;
    }

    /** The declared name fields the DTOs built so far did not carry, in the order they were met. */
    public List<UnresolvedName> unresolvedNames() {
        return List.copyOf(unresolvedNames);
    }

    /**
     * A field that is not sortable itself but has sortable siblings named
     * {@code <field>Sort<Xx>} (one per language, {@code Xx} a two-letter language code) is sorted
     * through them; the siblings are published as the field's {@code sortAlias}, keyed by the
     * lower-cased language code.
     */
    private static void withLocaleSortAliases(List<FieldMeta> fields) {
        Map<String, Map<String, String>> aliases = new LinkedHashMap<>();
        for (FieldMeta field : fields) {
            if (field.searchable() == null || !field.searchable().sortable()) continue;
            Matcher sibling = LOCALE_SORT_SIBLING.matcher(field.name());
            if (!sibling.matches()) continue;
            aliases.computeIfAbsent(sibling.group(1), k -> new LinkedHashMap<>())
                    .put(sibling.group(2).toLowerCase(Locale.ROOT), field.name());
        }
        if (aliases.isEmpty()) return;
        fields.replaceAll(field -> {
            Map<String, String> alias = aliases.get(field.name());
            if (alias == null || field.searchable() == null || field.searchable().sortable()) return field;
            return field.withSearchable(field.searchable().withSortAlias(new TreeMap<>(alias)));
        });
    }

    /**
     * The properties a client writes and the server never returns, as format {@code "secret"}: a
     * field marked {@code @JsonProperty(access = WRITE_ONLY)} is absent from the serialized view
     * the loop above reads, so a form generated from this document would have no input for it.
     */
    private List<FieldMeta> writeOnlyFields(Class<?> type) {
        BeanDescription bean = mapper.getDeserializationConfig().introspect(mapper.constructType(type));
        List<FieldMeta> fields = new ArrayList<>();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            Field backing = backingField(property);
            if (backing == null || !backing.getDeclaringClass().equals(type)) continue;
            JsonProperty json = backing.getAnnotation(JsonProperty.class);
            if (json == null || json.access() != JsonProperty.Access.WRITE_ONLY) continue;
            FieldMeta field = toFieldMeta(property, backing, type);
            if (field != null) fields.add(field.withFormat(FieldMeta.FORMAT_SECRET));
        }
        return fields;
    }

    /**
     * Returns {@code null} when the property cannot be described, e.g. a write-only setter
     * or a creator parameter never re-exposed through a getter or field, so there is nothing
     * to read a type from. The caller drops nulls rather than emit a {@link FieldMeta} with no type.
     */
    private FieldMeta toFieldMeta(BeanPropertyDefinition property, Field backing, Class<?> owner) {
        AnnotatedMember member = property.getAccessor() != null
                ? property.getAccessor() : property.getPrimaryMember();
        if (backing == null && member == null) return null;

        ResolvableType resolvable = resolvableType(backing, member, owner);
        TypeRef type = typeRefMapper.map(resolvable);

        String description = null;
        String labelKey = null;
        String label = null;
        List<ConstraintMeta> constraints = List.of();
        SearchableMeta searchable = null;
        boolean required = resolvable.resolve(Object.class).isPrimitive();

        if (backing != null) {
            Schema schema = backing.getAnnotation(Schema.class);
            if (schema != null) {
                if (!schema.description().isEmpty()) description = schema.description();
                if (schema.requiredMode() == Schema.RequiredMode.REQUIRED) required = true;
            }
            FieldLabel fieldLabel = backing.getAnnotation(FieldLabel.class);
            if (fieldLabel != null) {
                Matcher key = MESSAGE_KEY.matcher(fieldLabel.value());
                if (key.matches()) labelKey = key.group(1); else label = fieldLabel.value();
            }
            if (backing.isAnnotationPresent(NotNull.class)
                    || backing.isAnnotationPresent(NotBlank.class)
                    || backing.isAnnotationPresent(NotEmpty.class)) {
                required = true;
            }
            constraints = ConstraintExtractor.extract(backing, resolvable.resolve(Object.class));
            searchable = readSearchable(backing);

            JsonFormat format = backing.getAnnotation(JsonFormat.class);
            if (format != null && !format.pattern().isEmpty()
                    && resolvable.resolve(Object.class) == LocalTime.class) {
                type = TypeRef.time(format.pattern());
            }
        }

        boolean nullable = !required;
        return new FieldMeta(property.getName(), type, required, nullable,
                description, labelKey, label,
                constraints.isEmpty() ? null : constraints, searchable);
    }

    /**
     * A backing field resolves generics against the owner class; a getter resolves them against
     * its own return type. Anything else (an {@link AnnotatedField} reached only through
     * {@link BeanPropertyDefinition#getPrimaryMember()}, or a constructor parameter) carries no
     * generic-aware {@link ResolvableType} constructor, so it falls back to the raw type.
     */
    private ResolvableType resolvableType(Field backing, AnnotatedMember member, Class<?> owner) {
        if (backing != null) return ResolvableType.forField(backing, owner);
        if (member.getAnnotated() instanceof Method method) return ResolvableType.forMethodReturnType(method);
        return ResolvableType.forClass(member.getRawType());
    }

    /**
     * The order a type's fields are published in: the order the class declares them, then any
     * property with no field of its own (a getter), by name. Jackson's property order is not a
     * contract and moved between two runs over the same classes, which rewrote every generated file
     * that lists fields; this order depends only on the compiled class.
     */
    static Comparator<FieldMeta> declarationOrder(Class<?> type) {
        Map<String, Integer> index = new HashMap<>();
        Field[] declared = type.getDeclaredFields();
        for (int i = 0; i < declared.length; i++) index.putIfAbsent(declared[i].getName(), i);
        return Comparator.<FieldMeta>comparingInt(field -> index.getOrDefault(field.name(), Integer.MAX_VALUE))
                .thenComparing(FieldMeta::name);
    }

    private Field backingField(BeanPropertyDefinition property) {
        AnnotatedField field = property.getField();
        return field != null ? (Field) field.getAnnotated() : null;
    }

    /**
     * Whether the field takes part in the keyword search. {@code keyword()} arrived in searchable-jpa
     * 2.0.1, so an older library on the classpath has no such member and no keyword fields.
     */
    private static boolean readKeyword(Annotation annotation) throws ReflectiveOperationException {
        try {
            return (Boolean) annotation.annotationType().getMethod("keyword").invoke(annotation);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** searchable-jpa is optional on the classpath; read its annotation by name. */
    private SearchableMeta readSearchable(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (!annotation.annotationType().getName().equals(SEARCHABLE_FIELD)) continue;
            try {
                Object[] operators = (Object[]) annotation.annotationType()
                        .getMethod("operators").invoke(annotation);
                boolean sortable = (Boolean) annotation.annotationType()
                        .getMethod("sortable").invoke(annotation);
                String entityField = (String) annotation.annotationType()
                        .getMethod("entityField").invoke(annotation);
                String sortField = (String) annotation.annotationType()
                        .getMethod("sortField").invoke(annotation);
                List<String> operatorNames = Arrays.stream(operators)
                        .map(o -> ((Enum<?>) o).name()).toList();
                return new SearchableMeta(operatorNames, sortable,
                        entityField.isEmpty() ? null : entityField,
                        sortField.isEmpty() ? null : sortField)
                        .withKeyword(readKeyword(annotation));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to read @SearchableField on " + field, e);
            }
        }
        return null;
    }
}
