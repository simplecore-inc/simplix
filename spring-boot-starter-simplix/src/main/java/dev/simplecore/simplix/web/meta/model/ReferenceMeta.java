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
 *
 * <p>{@code nameField} is the path, in the DTO the reference is published on, of the property that
 * carries the referenced record's name: a sibling property ({@code actorUsername}) or a dotted path
 * into a nested record ({@code plan.name}). Absent where the reference declares none, or where this
 * DTO does not carry it (a search DTO, a request body).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferenceMeta(String entity, String idField, boolean many, String kindField, Map<String, String> kinds,
                            String nameField) {

    public ReferenceMeta(String entity, String idField, boolean many) {
        this(entity, idField, many, null, null, null);
    }

    public ReferenceMeta(String entity, String idField, boolean many, String kindField, Map<String, String> kinds) {
        this(entity, idField, many, kindField, kinds, null);
    }

    public ReferenceMeta withNameField(String value) {
        return new ReferenceMeta(entity, idField, many, kindField, kinds, value);
    }

    /** Whether the target depends on another field's value. */
    public boolean byKind() {
        return kindField != null;
    }
}
