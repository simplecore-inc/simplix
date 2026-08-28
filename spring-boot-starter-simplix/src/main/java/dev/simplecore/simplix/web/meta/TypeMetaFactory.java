package dev.simplecore.simplix.web.meta;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import dev.simplecore.simplix.core.annotation.FieldLabel;
import dev.simplecore.simplix.web.meta.model.ConstraintMeta;
import dev.simplecore.simplix.web.meta.model.FieldMeta;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a {@link TypeMeta} for one DTO class. Serialization membership and wire names come from
 * the APPLICATION's ObjectMapper — never a fresh one; declarations come from the backing field:
 * constraints, {@code @FieldLabel}, {@code @SearchableField} (read reflectively, the library is
 * optional), {@code @Schema}, {@code @JsonFormat} patterns.
 */
public class TypeMetaFactory {

    private static final Pattern MESSAGE_KEY = Pattern.compile("^\\{(.+)}$");
    private static final String SEARCHABLE_FIELD = "dev.simplecore.searchable.core.annotation.SearchableField";

    private final ObjectMapper mapper;
    private final TypeRefMapper typeRefMapper;
    private final MetaTypeRegistry registry;

    public TypeMetaFactory(ObjectMapper mapper, TypeRefMapper typeRefMapper, MetaTypeRegistry registry) {
        this.mapper = mapper;
        this.typeRefMapper = typeRefMapper;
        this.registry = registry;
    }

    public TypeMeta create(Class<?> type) {
        BeanDescription bean = mapper.getSerializationConfig()
                .introspect(mapper.constructType(type));

        List<FieldMeta> fields = new ArrayList<>();
        for (BeanPropertyDefinition property : bean.findProperties()) {
            Field backing = backingField(property);
            Class<?> declaring = backing != null ? backing.getDeclaringClass()
                    : property.getAccessor() != null ? property.getAccessor().getDeclaringClass() : type;
            if (!declaring.equals(type)) continue; // inherited — lives on the parent TypeMeta
            FieldMeta field = toFieldMeta(property, backing, type);
            if (field != null) fields.add(field);
        }

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

        return new TypeMeta(type.getName(), extendsType, typeParams, description, fields);
    }

    /**
     * Returns {@code null} when the property cannot be described — e.g. a write-only setter
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

    private Field backingField(BeanPropertyDefinition property) {
        AnnotatedField field = property.getField();
        return field != null ? (Field) field.getAnnotated() : null;
    }

    /** searchable-jpa is optional on the classpath — read its annotation by name. */
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
                        sortField.isEmpty() ? null : sortField);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to read @SearchableField on " + field, e);
            }
        }
        return null;
    }
}
