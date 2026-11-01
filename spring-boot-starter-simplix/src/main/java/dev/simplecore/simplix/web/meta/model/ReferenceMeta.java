package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The entity a DTO field points at. {@code entity} is the target's simple name (a key of
 * {@link DtoMeta#entities()}), {@code idField} the target's property the value matches (its
 * identifier, unless {@code @References(field)} names another), and {@code many}
 * whether the field carries a collection of identifiers.
 *
 * <p>A field declared with {@code @ReferencesByKind} names one of several entities: {@code entity}
 * and {@code idField} are absent, {@code kindField} names the sibling field whose value picks the
 * target, and {@code kinds} maps each of its values to the entity simple name it names. The value
 * always matches the target's identifier.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferenceMeta(String entity, String idField, boolean many, String kindField, Map<String, String> kinds) {

    public ReferenceMeta(String entity, String idField, boolean many) {
        this(entity, idField, many, null, null);
    }

    /** Whether the target depends on another field's value. */
    public boolean byKind() {
        return kindField != null;
    }
}
