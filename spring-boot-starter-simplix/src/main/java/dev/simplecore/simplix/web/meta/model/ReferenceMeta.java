package dev.simplecore.simplix.web.meta.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The entity a DTO field points at. {@code entity} is the target's simple name (a key of
 * {@link DtoMeta#entities()}), {@code idField} the target's identifier property, and {@code many}
 * whether the field carries a collection of identifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferenceMeta(String entity, String idField, boolean many) {}
